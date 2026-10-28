package com.watube.yard.services

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import com.watube.yard.extensions.bundleOf
import androidx.core.os.postDelayed
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.watube.yard.R
import com.watube.yard.api.JsonHelper
import com.watube.yard.api.obj.Segment
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.enums.PlayerCommand
import com.watube.yard.enums.PlayerEvent
import com.watube.yard.enums.SbSkipOptions
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.parcelableExtra
import com.watube.yard.extensions.toID
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.extensions.updateParameters
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PlayerHelper.getCurrentSegment
import com.watube.yard.helpers.PrivacyStats
import com.watube.yard.ui.activities.MainActivity
import com.watube.yard.ui.tools.RestMode
import com.watube.yard.util.DefaultTrackSelectorWithAudioQualitySupport
import com.watube.yard.util.NowPlayingNotification
import com.watube.yard.util.PauseableTimer
import com.watube.yard.util.PlayingQueue
import com.watube.yard.util.PlayingQueueMode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
abstract class AbstractPlayerService : MediaLibraryService(), MediaLibrarySession.Callback {
    // region Media Notification
    private var mediaLibrarySession: MediaLibrarySession? = null
    // endregion

    // region Video Player
    var exoPlayer: ExoPlayer? = null
        private set
    // endregion

    // region Media Notification
    private var notificationProvider: NowPlayingNotification? = null

    private fun handlePlayerAction(event: PlayerEvent) {
        if (PlayerHelper.handlePlayerAction(activePlayer ?: return, event)) return

        when (event) {
            PlayerEvent.Next -> {
                navigateVideo(PlayingQueue.getNext() ?: return)
            }

            PlayerEvent.Prev -> {
                navigateVideo(PlayingQueue.getPrev() ?: return)
            }

            PlayerEvent.Stop -> {
                releaseAndStop()
            }

            else -> Unit
        }
    }

    /**
     * Trigger a notification update with an updated PendingIntent.
     */
    private fun updateNotification() {
        val notificationIntent = Intent(this, getIntentActivity()).apply {
            putExtra(IntentData.maximizePlayer, true)
            putExtra(IntentData.offlinePlayer, isOfflinePlayer)
            putExtra(IntentData.audioOnly, isAudioOnlyPlayer)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        notificationProvider?.notificationIntent = notificationIntent
        triggerNotificationUpdate()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        // Since Media3 1.11 super.onConnect() returns no command at all: start from the per-controller
        // defaults instead. Untrusted controllers (other apps) keep their read-only access and never
        // get the private service commands; this app, the system and media-control apps get them.
        val connectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
        if (!controller.isTrusted) return connectionResult

        val mediaNotificationSessionCommands =
            connectionResult.availableSessionCommands.buildUpon()
                .also { builder ->
                    builder.addSessionCommands(
                        listOf(
                            startServiceCommand,
                            runPlayerActionCommand,
                            stopServiceCommand
                        )
                    )
                }
                .build()

        val playerCommands = connectionResult.availablePlayerCommands.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(mediaNotificationSessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        val event: KeyEvent = intent.parcelableExtra(Intent.EXTRA_KEY_EVENT) ?: return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                handlePlayerAction(PlayerEvent.Next)
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                handlePlayerAction(PlayerEvent.Prev)
                return true
            }

            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                handlePlayerAction(PlayerEvent.Rewind)
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                handlePlayerAction(PlayerEvent.Forward)
            }

            KeyEvent.KEYCODE_MEDIA_STOP -> {
                handlePlayerAction(PlayerEvent.Stop)
            }
        }

        return super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    /**
     * [Player] wrapper that handles seeking actions (next/previous) itself instead of using the
     * default [Player] implementation
     */
    inner class MediaSessionForwarder(player: Player) : ForwardingPlayer(player) {
        override fun hasNextMediaItem(): Boolean {
            return PlayingQueue.hasNext()
        }

        override fun hasPreviousMediaItem(): Boolean {
            return PlayingQueue.hasPrev()
        }

        override fun seekToPrevious() {
            handlePlayerAction(PlayerEvent.Prev)
        }

        override fun seekToNext() {
            handlePlayerAction(PlayerEvent.Next)
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .addAll(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            if (command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS) return true

            return super.isCommandAvailable(command)
        }
    }
    // endregion

    // region Playback Options
    var trackSelector: DefaultTrackSelector? = null
        private set
    // endregion

    // region Video Player
    var isTransitioning = false
        private set

    /** [releaseAndStop] state, and the system's onDestroy() waiting for the release to finish. */
    private var isReleasing = false
    private var isReleased = false
    private var afterRelease: (() -> Unit)? = null

    val handler = Handler(Looper.getMainLooper())
    // endregion

    // region Watch Positions
    private val watchPositionTimer = PauseableTimer(
        onTick = ::saveWatchPosition,
        delayMillis = PlayerHelper.WATCH_POSITION_TIMER_DELAY_MS
    )

    val watchPositionsEnabled
        get() =
            (PlayerHelper.watchPositionsAudio && isAudioOnlyPlayer) || (PlayerHelper.watchPositionsVideo && !isAudioOnlyPlayer)

    private fun saveWatchPosition() {
        if (!watchPositionsEnabled || !::videoId.isInitialized) return

        watchPositionSource()?.let { PlayerHelper.saveWatchPosition(it, videoId) }
    }

    /** The player whose position belongs to [videoId] right now, null while it changes. */
    protected open fun watchPositionSource(): Player? = exoPlayer.takeUnless { isTransitioning }
    // endregion

    // region SponsorBlock
    // SponsorBlock Segment data
    private var sponsorBlockAutoSkip = true
    protected val sponsorBlockConfig = PlayerHelper.getSponsorBlockCategories()
    private var sponsorBlockSegments = listOf<Segment>()
    // endregion

    // region Autoplay
    /**
     * Whether the service should automatically play the next video after the current video finished.
     *
     * If set to `false`, the player UI views have to handle autoplay themselves.
     */
    private var shouldHandleAutoplay = true
    // endregion

    // region Rest Mode
    private val pauseForRestMode: () -> Unit = { activePlayer?.pause() }
    // endregion

    // region Video Player
    /** Listens to the local player, and to the TV while it is the [activePlayer]. */
    protected val servicePlayerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // the SponsorBlock loop sleeps until the next segment: a seek moves that target
            checkForSegments()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            // Start or pause watch position timer
            if (isPlaying) {
                // nothing plays while the scheduled rest mode runs, whoever started it
                if (RestMode.isActive) activePlayer?.pause()
                watchPositionTimer.resume()
                // the SponsorBlock polling loop stops while paused: restart it here
                checkForSegments()
            } else {
                watchPositionTimer.pause()
                // persist the exact position right away, the periodic timer is throttled
                saveWatchPosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // a failing cast is ended by its owner, with a message of its own
            if (activePlayer !== exoPlayer) return
            // show a toast on errors, unless the service recovers on its own
            if (recoverFromPlayerError(error)) return
            toastFromMainThread(error.localizedMessage.orEmpty())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            when (playbackState) {
                Player.STATE_ENDED -> {
                    saveWatchPosition()
                }

                Player.STATE_READY -> {
                    isTransitioning = false
                }

                else -> Unit
            }
        }
    }
    // endregion

    // region SponsorBlock
    /**
     * Check for SponsorBlock segments. This method automatically schedules itself to repeat every
     * 100ms using [handler], so it's not needed to schedule it manually.
     */
    private val segmentsChecker = Runnable { checkForSegments() }

    protected fun setSponsorBlockSegments(segments: List<Segment>) {
        sponsorBlockSegments = segments
        if (!PlayerHelper.sponsorBlockEnabled) return

        updatePlaylistMetadata {
            // JSON-encode as work-around for https://github.com/androidx/media/issues/564
            val segments = JsonHelper.json.encodeToString(sponsorBlockSegments)
            setExtras(bundleOf(IntentData.segments to segments))
        }

        checkForSegments()
    }

    private fun checkForSegments() {
        // a single loop: this is (re)started on every play and every segments update
        handler.removeCallbacks(segmentsChecker)
        val player = activePlayer
        // The loop must only run while it can actually do something: no player, paused,
        // no segments or SponsorBlock disabled would otherwise keep 10 wake-ups/second
        // alive for the whole service lifetime. It is restarted by onIsPlayingChanged(true)
        // and by setSponsorBlockSegments().
        if (player == null ||
            !player.isPlaying ||
            sponsorBlockSegments.isEmpty() ||
            !PlayerHelper.sponsorBlockEnabled
        ) {
            return
        }

        // battery: sleep until the next segment starts instead of polling 10 times a second for
        // the whole video; seeks restart the loop (onPositionDiscontinuity)
        val position = player.currentPosition
        val nextStart = sponsorBlockSegments
            .map { (it.segmentStartAndEnd.first * 1000f).toLong() }
            .filter { it > position }
            .minOrNull()
        val untilNext = nextStart?.let { ((it - position) / player.playbackParameters.speed).toLong() }
        handler.postDelayed(segmentsChecker, (untilNext ?: MAX_SEGMENT_CHECK_DELAY).coerceIn(100, MAX_SEGMENT_CHECK_DELAY))

        val (currentSegment, sbSkipOption) = player.getCurrentSegment(
            sponsorBlockSegments,
            sponsorBlockConfig
        ) ?: return

        if (sbSkipOption in arrayOf(SbSkipOptions.AUTOMATIC, SbSkipOptions.AUTOMATIC_ONCE) && sponsorBlockAutoSkip) {
            val segmentEndMs = currentSegment.segmentStartAndEnd.second.toLong() * 1000
            // the privacy tab shows the time SponsorBlock gave back
            PrivacyStats.recordSkippedSegment(segmentEndMs - player.currentPosition)
            player.seekTo(segmentEndMs)
            currentSegment.skipped = true

            if (PlayerHelper.sponsorBlockNotifications) toastFromMainThread(R.string.segment_skipped)
        }
    }
    // endregion

    // region Video Player
    abstract val isOfflinePlayer: Boolean

    /**
     * The player the media session drives: [exoPlayer], or the TV while a Stolm cast runs.
     * Position saving, SponsorBlock skips and the rest mode follow it.
     */
    protected open val activePlayer: Player? get() = exoPlayer

    lateinit var videoId: String

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            START_SERVICE_ACTION -> {
                PlayingQueue.queueMode =
                    if (isOfflinePlayer) PlayingQueueMode.OFFLINE else PlayingQueueMode.ONLINE

                CoroutineScope(Dispatchers.IO).launch {
                    onServiceCreated(args)
                    withContext(Dispatchers.Main) {
                        updateNotification()
                    }

                    if (::videoId.isInitialized) startPlayback()
                }
            }

            STOP_SERVICE_ACTION -> {
                releaseAndStop()
            }

            RUN_PLAYER_COMMAND_ACTION -> {
                runPlayerCommand(args)
            }
        }

        return super.onCustomCommand(session, controller, customCommand, args)
    }

    open fun runPlayerCommand(args: Bundle) {
        when {
            args.containsKey(PlayerCommand.SKIP_SILENCE.name) -> exoPlayer?.skipSilenceEnabled =
                args.getBoolean(PlayerCommand.SKIP_SILENCE.name)

            args.containsKey(PlayerCommand.SET_VIDEO_TRACK_TYPE_DISABLED.name) -> trackSelector?.updateParameters {
                setTrackTypeDisabled(
                    C.TRACK_TYPE_VIDEO,
                    args.getBoolean(PlayerCommand.SET_VIDEO_TRACK_TYPE_DISABLED.name)
                )
            }

            args.containsKey(PlayerCommand.SET_AUDIO_ROLE_FLAGS.name) -> {
                trackSelector?.updateParameters {
                    setPreferredAudioRoleFlags(args.getInt(PlayerCommand.SET_AUDIO_ROLE_FLAGS.name))
                }
            }

            args.containsKey(PlayerCommand.SET_AUDIO_LANGUAGE.name) -> {
                trackSelector?.updateParameters {
                    setPreferredAudioLanguage(args.getString(PlayerCommand.SET_AUDIO_LANGUAGE.name))
                }
            }

            args.containsKey(PlayerCommand.SET_RESOLUTION.name) -> {
                trackSelector?.updateParameters {
                    val resolution = args.getInt(PlayerCommand.SET_RESOLUTION.name)
                    setMinVideoSize(Int.MIN_VALUE, resolution)
                    setMaxVideoSize(Int.MAX_VALUE, resolution)
                }
            }

            args.containsKey(PlayerCommand.SET_CAPTION_TRACK.name) -> {
                val exoPlayer = exoPlayer ?: return

                val captionId = args.getString(PlayerCommand.SET_CAPTION_TRACK.name)
                val caption = PlayerHelper.getCaptionTracks(exoPlayer).firstOrNull { it.id == captionId }

                trackSelector?.updateParameters {
                    val enableCaptions = caption != null

                    if (enableCaptions) {
                        setPreferredTextRoleFlags(caption.roleFlags)
                        setPreferredTextLanguage(caption.language)
                    }

                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enableCaptions)
                }
            }

            args.containsKey(PlayerCommand.PLAY_VIDEO_BY_ID.name) -> {
                navigateVideo(args.getString(PlayerCommand.PLAY_VIDEO_BY_ID.name) ?: return)
            }

            args.containsKey(PlayerCommand.PLAY_NEXT_VIDEO.name) -> {
                playNextVideo()
            }

            args.containsKey(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name) -> {
                isAudioOnlyPlayer = args.getBoolean(PlayerCommand.TOGGLE_AUDIO_ONLY_MODE.name)
                trackSelector?.updateParameters {
                    setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, isAudioOnlyPlayer)
                }
                updateNotification()
            }

            args.containsKey(PlayerCommand.SET_SB_AUTO_SKIP_ENABLED.name) -> {
                sponsorBlockAutoSkip = args.getBoolean(PlayerCommand.SET_SB_AUTO_SKIP_ENABLED.name)
            }

            args.containsKey(PlayerCommand.SET_AUTOPLAY_COUNTDOWN_ENABLED.name) -> {
                shouldHandleAutoplay =
                    !args.getBoolean(PlayerCommand.SET_AUTOPLAY_COUNTDOWN_ENABLED.name)
            }
        }
    }

    /**
     * Clear the currently playing video and start playing the new provided [videoId]
     *
     * If overriding, clearing old data must be done BEFORE calling [super.navigateVideo()]!
     */
    @CallSuper
    open fun navigateVideo(videoId: String) {
        sponsorBlockSegments = emptyList()

        // Couper net l'audio de la vidéo courante avant d'extraire la suivante.
        // clearMediaItems() seul laissait filtrer un court résidu audio de la vidéo précédente
        // pendant l'extraction asynchrone de la nouvelle (chevauchement signalé). On sauvegarde
        // d'abord la position (sinon stop() la ferait perdre), puis on arrête et on vide.
        saveWatchPosition()
        // On marque la transition AVANT stop() : stop() fait passer le player en STATE_IDLE
        // sans erreur, ce que OnlinePlayerService interprète sinon comme « plus rien à jouer »
        // et détruit le service en pleine navigation (nouvelle vidéo qui ne charge jamais,
        // chargement en boucle quand on enchaîne une autre vidéo depuis le mini-lecteur).
        isTransitioning = true
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        this.videoId = videoId

        CoroutineScope(Dispatchers.IO).launch {
            startPlayback()
        }
    }

    protected fun updatePlaylistMetadata(updateAction: MediaMetadata.Builder.() -> Unit) {
        handler.post {
            val metadata = MediaMetadata.Builder()
                .apply(updateAction)
                // send a unique timestamp to notify that the metadata changed, even if playing the same video twice
                .setTrackNumber(System.currentTimeMillis().mod(Int.MAX_VALUE))
                .build()
            exoPlayer?.playlistMetadata = metadata
            // the player screen reads it from the session's player
            activePlayer?.takeIf { it !== exoPlayer }?.playlistMetadata = metadata
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationProvider = NowPlayingNotification(this)
        setMediaNotificationProvider(notificationProvider!!)

        createPlayerAndMediaSession()
    }

    /**
     * Not sticky: Media3 asks Android to restart the service after its process dies (sleep
     * timer, low memory), and the restarted service only held an empty player in the
     * foreground, with its notification, in a new background process. Media buttons and
     * playback resumption start it with an intent of their own, unaffected.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    open fun getIntentActivity(): Class<*> = MainActivity::class.java

    abstract suspend fun onServiceCreated(args: Bundle)

    @OptIn(UnstableApi::class)
    private fun createPlayerAndMediaSession() {
        val trackSelector = DefaultTrackSelectorWithAudioQualitySupport(this)
        this.trackSelector = trackSelector

        val player = PlayerHelper.createPlayer(this, trackSelector)
        // prevent android from putting Watube to sleep when locked
        player.setWakeMode(if (isOfflinePlayer) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK)
        player.addListener(servicePlayerListener)
        this.exoPlayer = player
        RestMode.pausePlayback = pauseForRestMode

        val forwardingPlayer = MediaSessionForwarder(player)

        // it's possible that this is called twice at the same time:
        // if the user rotates the screen while the initial media library session is still in creation
        // this results in a race condition which service is the first one to create a `MediaLibrarySession`
        // the other one will just crash
        try {
            mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, this)
                .setId(this.javaClass.name)
                // notification artwork fetched like the media: generic User-Agent
                .setBitmapLoader(
                    CacheBitmapLoader(
                        DataSourceBitmapLoader.Builder(this)
                            .setDataSourceFactory(PlayerHelper.mediaDataSourceFactory(this))
                            .build()
                    )
                )
                .build()
        } catch (e: Exception) {
            Log.e(TAG(), "failed to start media library session because it already exists")
            e.printStackTrace()
            releaseAndStop()
        }
    }

    /** Returns true when the error is being handled here, without any message to the user. */
    protected open fun recoverFromPlayerError(error: PlaybackException) = false

    /**
     * Load the stream source and start the playback.
     *
     * This function should base its actions on the videoId variable.
     */
    @CallSuper
    open suspend fun startPlayback() {
        isTransitioning = true

        updatePlaylistMetadata {
            setExtras(bundleOf(IntentData.videoId to videoId))
        }
    }

    /** Called first by [releaseAndStop], on the main thread. */
    protected open fun onRelease() = Unit

    /**
     * Since Media3 1.10 the service is a LifecycleService: onDestroy() belongs to the system and
     * must run once, after the session is released. Stopping by hand goes through
     * [releaseAndStop] instead, which lets the system call onDestroy() afterwards.
     */
    override fun onDestroy() {
        if (isReleased) return super.onDestroy()
        afterRelease = { super.onDestroy() }
        releaseAndStop()
    }

    /** Releases the player and the session, then stops the service. Runs once. */
    protected fun releaseAndStop() {
        if (isReleasing) return
        isReleasing = true
        onRelease()

        // wait for a short time before killing the mediaSession
        // as the playerController must be released before we finish the session
        // otherwise there would be a
        // java.lang.SecurityException: Session rejected the connection request.
        // because there can't be two active playerControllers at the same time.
        handler.postDelayed(50) {
            saveWatchPosition()

            notificationProvider = null
            // a newer service may already have taken over the rest mode hook
            if (RestMode.pausePlayback === pauseForRestMode) RestMode.pausePlayback = null
            watchPositionTimer.destroy()

            handler.removeCallbacksAndMessages(null)

            runCatching {
                exoPlayer?.stop()
                exoPlayer?.release()
            }

            kotlin.runCatching {
                mediaLibrarySession?.release()
                mediaLibrarySession = null
            }

            PlayingQueue.clear()

            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()

            isReleased = true
            afterRelease?.invoke()
            afterRelease = null
        }
    }

    /**
     * Stop the service when app is removed from the task manager.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        releaseAndStop()
    }
    // endregion

    // region Background Playback
    var isAudioOnlyPlayer: Boolean = false
    // endregion

    // region Playing Queue
    /**
     * Plays the next video.
     *
     * If a video ID is explicitly given, it is played next,
     * otherwise a queued video (which includes playlists) is chosen.
     *
     * If no video is queued and autoplay is enabled, a related video is used.
     */
    fun playNextVideo(videoId: String? = null, relatedStreams: List<StreamItem>? = null) {
        if (PlayingQueue.repeatMode == Player.REPEAT_MODE_ONE) {
            activePlayer?.seekTo(0)
            return
        }

        if (!shouldHandleAutoplay) {
            return
        }

        val nextVideo = videoId ?: PlayingQueue.getNext()
        ?: relatedStreams?.firstOrNull { !it.isLive }?.url?.toID()
            ?.takeIf { PlayerHelper.autoPlayEnabled && PlayerHelper.relatedStreamsEnabled }
        ?: return

        navigateVideo(nextVideo)
    }
    // endregion

    // region Stolm
    /** Hands the media session (notification, player screen, other apps) to [player]. */
    protected fun useSessionPlayer(player: Player) {
        mediaLibrarySession?.player = MediaSessionForwarder(player)
    }
    // endregion

    companion object {
        // region Video Player
        val startServiceCommand = SessionCommand(START_SERVICE_ACTION, Bundle.EMPTY)
        val stopServiceCommand = SessionCommand(STOP_SERVICE_ACTION, Bundle.EMPTY)
        val runPlayerActionCommand = SessionCommand(RUN_PLAYER_COMMAND_ACTION, Bundle.EMPTY)

        private const val START_SERVICE_ACTION = "start_service_action"
        private const val STOP_SERVICE_ACTION = "stop_service_action"
        private const val RUN_PLAYER_COMMAND_ACTION = "run_player_command_action"
        // endregion

        // region SponsorBlock
        /** Longest sleep of the SponsorBlock loop between two segments. */
        private const val MAX_SEGMENT_CHECK_DELAY = 2000L
        // endregion
    }
}
