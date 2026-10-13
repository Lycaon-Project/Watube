package com.watube.yard.services

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import com.watube.yard.R
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.SubscriptionHelper
import com.watube.yard.api.obj.Segment
import com.watube.yard.api.obj.Streams
import com.watube.yard.cast.CastTarget
import com.watube.yard.cast.Stolm
import com.watube.yard.cast.StolmCast
import com.watube.yard.cast.StolmException
import com.watube.yard.constants.IntentData
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.enums.PlayerCommand
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.parcelable
import com.watube.yard.extensions.setMetadata
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.extensions.updateParameters
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PlayerHelper.getSubtitleRoleFlags
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.player.SabrAttestationException
import com.watube.yard.player.SabrMediaSource
import com.watube.yard.player.SabrSessionRenewals
import com.watube.yard.player.manifest.SabrManifest
import com.watube.yard.util.DeArrowUtil
import com.watube.yard.util.PlayingQueue
import com.watube.yard.util.SharedHttpClient
import com.watube.yard.util.YoutubeHlsPlaylistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Loads the selected videos audio in background mode with a notification area.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class OnlinePlayerService : AbstractPlayerService() {
    // region Video Player
    override val isOfflinePlayer: Boolean = false
    // endregion

    // region Playing Queue
    // PlaylistId/ChannelId for autoplay
    private var playlistId: String? = null
    private var channelId: String? = null
    // endregion

    // region Video Player
    private var startTimestampSeconds: Long? = null

    /**
     * The response that gets when called the Api.
     */
    private var streams: Streams? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    /*
    Current job that's loading a new video (the value is null if no video is loading at the moment).
     */
    private var fetchVideoInfoJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    if (!isTransitioning) playNextVideo(relatedStreams = streams?.relatedStreams)
                }

                Player.STATE_IDLE -> {
                    // keep the service alive when the player is only idle because of an
                    // error: destroying it here dropped the playback after the device
                    // slept for a long time and there was nothing left to resume.
                    // Idem pendant une transition : navigateVideo() appelle stop() (-> STATE_IDLE
                    // sans erreur) juste avant d'extraire la vidéo suivante ; détruire le service
                    // ici couperait la navigation (chargement en boucle sur la nouvelle vidéo).
                    if (!isTransitioning && shouldStopOnlinePlayerService(exoPlayer, playbackState)) {
                        releaseAndStop()
                    }
                }

                Player.STATE_BUFFERING -> {}
                Player.STATE_READY -> {
                    // save video to watch history when the video starts playing or is being resumed
                    // waiting for the player to be ready since the video can't be claimed to be watched
                    // while it did not yet start actually, but did buffer only so far
                    if (PlayerHelper.watchHistoryEnabled) {
                        scope.launch(Dispatchers.IO) {
                            streams?.let { streams ->
                                val watchHistoryItem =
                                    streams.toStreamItem(videoId).toWatchHistoryItem(videoId)
                                DatabaseHelper.addToWatchHistory(watchHistoryItem)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun onServiceCreated(args: Bundle) {
        val playerData = args.parcelable<PlayerData>(IntentData.playerData)
        if (playerData == null) {
            stopSelf()
            return
        }
        isAudioOnlyPlayer = args.getBoolean(IntentData.audioOnly)

        // get the intent arguments
        val requestedVideoId = playerData.videoId
        if (requestedVideoId.isNullOrEmpty()) {
            stopSelf()
            return
        }
        videoId = requestedVideoId
        playlistId = playerData.playlistId
        channelId = playerData.channelId
        startTimestampSeconds = playerData.timestamp

        if (!playerData.keepQueue) PlayingQueue.clear()

        exoPlayer?.addListener(playerListener)
        trackSelector?.updateParameters {
            setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, isAudioOnlyPlayer)
        }
    }

    override suspend fun startPlayback() {
        super.startPlayback()

        val timestampMs = startTimestampSeconds?.times(1000) ?: 0L
        startTimestampSeconds = null

        // stop any previous task for loading video info
        fetchVideoInfoJob?.cancelAndJoin()

        SabrSessionRenewals.start(videoId)

        // start loading the video info while keeping a reference to the job
        // so that it can be canceled once a different video is loaded
        fetchVideoInfoJob = scope.launch {
            streams = fetchStreams() ?: return@launch

            streams?.toStreamItem(videoId)?.let {
                // save the current stream to the queue
                PlayingQueue.updateCurrent(it)

                if (!PlayingQueue.hasNext()) {
                    PlayingQueue.updateQueue(it, playlistId, channelId)
                }

                // update feed item with newer information, e.g. more up-to-date views
                SubscriptionHelper.submitFeedItemChange(it.toFeedItem())
            }

            launch {
                val segments = getSponsorBlockSegments()
                withContext(Dispatchers.Main) { setSponsorBlockSegments(segments) }
            }

            withContext(Dispatchers.Main) {
                setStreamSource()
                val cast = stolm
                if (cast == null) {
                    configurePlayer(timestampMs)
                } else {
                    // the next video goes to the TV; the phone keeps it ready, unprepared
                    exoPlayer?.playWhenReady = false
                    streams?.let { loadOnCast(cast, it, startPositionMs(timestampMs) ?: 0) }
                }
            }
        }

        fetchVideoInfoJob?.join()
        fetchVideoInfoJob = null
    }

    private suspend fun fetchStreams(): Streams? = withContext(Dispatchers.IO) {
        try {
            MediaServiceRepository.instance.getStreams(videoId).let {
                DeArrowUtil.deArrowStreams(it, videoId)
            }
        } catch (e: Exception) {
            Log.e(TAG(), e.stackTraceToString())
            toastFromMainDispatcher(e.localizedMessage.orEmpty())
            null
        }
    }

    private fun configurePlayer(seekToPositionMs: Long) {
        startPositionMs(seekToPositionMs)?.let { exoPlayer?.seekTo(it) }

        exoPlayer?.apply {
            // automatically start playback when using the audio player
            playWhenReady = PlayerHelper.playAutomatically || isAudioOnlyPlayer
            prepare()
        }
    }

    override fun navigateVideo(videoId: String) {
        this.streams = null

        super.navigateVideo(videoId)
    }
    // endregion

    // region Stolm
    /** The running cast, null when the video plays on the phone. */
    private var stolm: StolmCast? = null
    private var castJob: Job? = null

    private val castListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) playNextVideo(relatedStreams = streams?.relatedStreams)
        }

        override fun onPlayerError(error: PlaybackException) = stopCast(R.string.stolm_playback_failed)
    }

    override val activePlayer: Player? get() = stolm?.player ?: exoPlayer

    override fun watchPositionSource(): Player? {
        val cast = stolm ?: return super.watchPositionSource()
        // between two videos the TV still plays the previous one
        return cast.player.takeIf { cast.player.currentItem?.videoId == videoId }
    }

    override fun runPlayerCommand(args: Bundle) {
        when {
            args.containsKey(PlayerCommand.CAST_TO.name) -> {
                val target = args.getBundle(PlayerCommand.CAST_TO.name)?.let(CastTarget::fromBundle) ?: return
                startCast(target)
            }

            args.containsKey(PlayerCommand.STOP_CAST.name) -> stopCast()
            else -> super.runPlayerCommand(args)
        }
    }

    /** Moves the current video to [target], from where the phone is. */
    private fun startCast(target: CastTarget) {
        val current = streams ?: return toastFromMainThread(R.string.stolm_wait_for_video)
        stopCast(endState = Stolm.State.Connecting(target.name))
        castJob = scope.launch {
            val cast = try {
                StolmCast.connect(this@OnlinePlayerService, target, mainLooper, PlayerHelper.seekIncrement) {
                    handler.post { stopCast() }
                }
            } catch (e: StolmException) {
                Stolm.update(Stolm.State.Idle)
                toastFromMainDispatcher(e.messageRes)
                return@launch
            }
            val position = withContext(Dispatchers.Main) {
                val exo = exoPlayer
                // the player was closed or another video started meanwhile
                if (exo == null || streams !== current) {
                    cast.close()
                    Stolm.update(Stolm.State.Idle)
                    return@withContext null
                }
                val position = if (current.isLive) 0 else exo.currentPosition
                exo.pause()
                stolm = cast
                cast.player.playlistMetadata = exo.playlistMetadata
                cast.player.addListener(servicePlayerListener)
                cast.player.addListener(castListener)
                useSessionPlayer(cast.player)
                Stolm.update(Stolm.State.Casting(target.name))
                position
            } ?: return@launch

            launch {
                cast.finished.await()
                // ended from the TV side (a stop from here has already let go of it)
                withContext(Dispatchers.Main) { if (stolm === cast) stopCast(R.string.stolm_ended) }
            }
            withContext(Dispatchers.Main) { loadOnCast(cast, current, position) }
        }
    }

    /** Main thread. A video the TV cannot play ends the cast, with the reason. */
    private fun loadOnCast(cast: StolmCast, streams: Streams, positionMs: Long) {
        val mediaItem = MediaItem.Builder().setMediaId(videoId).setMetadata(streams, videoId).build()
        castJob = scope.launch(Dispatchers.Main) {
            try {
                cast.play(videoId, streams, mediaItem, positionMs)
            } catch (e: StolmException) {
                if (stolm === cast) stopCast(e.messageRes)
            } catch (e: IOException) {
                if (stolm !== cast) return@launch
                // the TV never asked the phone for the video: it cannot reach it
                if (cast.receiverReached) {
                    stopCast(R.string.stolm_playback_failed)
                } else {
                    stopCast(R.string.stolm_tv_cannot_reach_phone, Stolm.State.Unreachable(cast.target))
                }
            }
        }
    }

    /**
     * Main thread. Ends the cast: the video goes back to the phone, paused where the TV was,
     * and the TV quits the receiver app.
     */
    private fun stopCast(@StringRes message: Int? = null, endState: Stolm.State = Stolm.State.Idle) {
        castJob?.cancel()
        castJob = null
        val cast = stolm
        stolm = null
        if (cast != null) {
            val castItem = cast.player.currentItem
            val position = cast.player.currentPosition
            cast.player.removeListener(servicePlayerListener)
            cast.player.removeListener(castListener)
            exoPlayer?.let { exo ->
                useSessionPlayer(exo)
                exo.playWhenReady = false
                if (castItem?.videoId == videoId && !castItem.isLive) exo.seekTo(position)
                // a video started during the cast was never prepared on the phone
                if (exo.playbackState == Player.STATE_IDLE && exo.mediaItemCount > 0) exo.prepare()
            }
            cast.close()
        }
        Stolm.update(endState)
        message?.let { toastFromMainThread(it) }
    }

    override fun onRelease() = stopCast()
    // endregion

    // region SABR Streaming
    /**
     * A streaming session flagged by YouTube stops after about a minute (see
     * [SabrAttestationException]): a new session is played on from the same position, without
     * the "source error" and the restart from 0:00 it used to end with.
     *
     * The first renewal only asks for a new stream ([MediaServiceRepository.renewStreamingSession]:
     * two requests, no third party). When the server refuses that session as well (seen on device:
     * an immediate player reload request), the next ones are full extractions, which it accepted
     * in that case. The title and thumbnail shown are kept, so DeArrow is not asked again.
     */
    override fun recoverFromPlayerError(error: PlaybackException): Boolean {
        val current = streams
        if (error.cause !is SabrAttestationException || current == null) return false
        val attempt = SabrSessionRenewals.consume(videoId)
        if (attempt == 0) return false

        val position = exoPlayer?.currentPosition ?: 0L
        Log.i(TAG(), "renewing the streaming session (attempt $attempt) at $position ms")
        fetchVideoInfoJob?.cancel()
        fetchVideoInfoJob = scope.launch {
            // privacy: the new session opens its own connections, none is shared with the old one
            SharedHttpClient.dropIdleConnections()
            val renewed = runCatching {
                if (attempt == 1) {
                    MediaServiceRepository.instance.renewStreamingSession(videoId, current)
                } else {
                    MediaServiceRepository.instance.getStreams(videoId)
                        .copy(title = current.title, thumbnailUrl = current.thumbnailUrl)
                }
            }.onFailure { Log.e(TAG(), "streaming session renewal failed", it) }.getOrNull()
            if (renewed == null) {
                // nothing left to try: the message the error would have shown
                toastFromMainDispatcher(error.localizedMessage.orEmpty())
                return@launch
            }
            withContext(Dispatchers.Main) {
                streams = renewed
                setStreamSource()
                exoPlayer?.seekTo(position)
                exoPlayer?.prepare()
            }
        }
        return true
    }
    // endregion

    // region Watch Positions
    /** Where the video starts: the link's timestamp, or the saved position of an unfinished video. */
    private fun startPositionMs(timestampMs: Long): Long? = when {
        timestampMs != 0L -> timestampMs
        watchPositionsEnabled -> DatabaseHelper.getWatchPositionBlocking(videoId)
            ?.takeUnless { DatabaseHelper.isVideoWatched(it, streams?.duration) }
        else -> null
    }
    // endregion

    // region SponsorBlock
    private suspend fun getSponsorBlockSegments(): List<Segment> {
        return runCatching {
            MediaServiceRepository.instance.getSegments(
                videoId,
                sponsorBlockConfig.keys.toList(),
                listOf("skip", "mute", "full", "poi", "chapter")
            ).segments
        }.getOrElse { emptyList() }
    }
    // endregion

    // region Stream Sources
    /**
     * Sets the [MediaItem] with the [streams] into the [exoPlayer]
     */
    private fun setStreamSource() {
        val streams = streams ?: return

        // NewPipeExtractor renvoie "" (jamais null) quand un manifest est absent : sans
        // cette normalisation les tests "hls != null"/"dash != null" sont toujours vrais,
        // ExoPlayer reçoit une URI vide et le live échoue immédiatement en "erreur source".
        val hlsUrl = streams.hls?.takeIf { it.isNotBlank() }
        val dashUrl = streams.dash?.takeIf { it.isNotBlank() }

        when {
            // SABR
            // skip SABR for livestreams, as the player impl has no support for it
            !streams.isLive && streams.serverAbrStreamingUrl != null && streams.videoPlaybackUstreamerConfig != null -> {
                val sabrMediaSourceFactory = SabrMediaSource.Factory(
                    SabrManifest(videoId, streams)
                )
                val mediaItem = createMediaItem(
                    streams.serverAbrStreamingUrl.toUri(),
                    "application/vnd.yt-ump",
                    streams
                )
                val mediaSource = sabrMediaSourceFactory.createMediaSource(mediaItem)
                val mediaSources = listOf<MediaSource>(mediaSource) +
                    streams.subtitles.mapNotNull {
                        val subtitleUrl = it.url
                        if (subtitleUrl.isNullOrEmpty()) return@mapNotNull null
                        val format = Format.Builder()
                            .setSampleMimeType(it.mimeType)
                            .setLanguage(it.code)
                            .setLabel(it.name)
                            .setRoleFlags(getSubtitleRoleFlags(it))
                            .build()
                        val subtitleParserFactory = DefaultSubtitleParserFactory()
                        val extractorsFactory = ExtractorsFactory {
                            arrayOf(
                                SubtitleExtractor(
                                    subtitleParserFactory.create(format), format
                                )
                            )
                        }
                        val progressiveMediaSourceFactory = ProgressiveMediaSource.Factory(
                            PlayerHelper.mediaDataSourceFactory(this), extractorsFactory
                        ).setLoadOnlySelectedTracks(true)
                        try {
                            // `enableLazyLoadingWithSingleTrack` is private
                            val method =
                                ProgressiveMediaSource.Factory::class.java.getDeclaredMethod(
                                    "enableLazyLoadingWithSingleTrack",
                                    Int::class.java,
                                    Format::class.java
                                )
                            method.isAccessible = true
                            method.invoke(
                                progressiveMediaSourceFactory, SubtitleExtractor.TRACK_ID,
                                format
                                    .buildUpon()
                                    .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                                    .setCodecs(format.sampleMimeType)
                                    .setCueReplacementBehavior( subtitleParserFactory.getCueReplacementBehavior(format))
                                    .build()
                            )
                        } catch (e: Exception) {
                            Log.w(this::class.simpleName, "failed to set subtitle lazy-loading: ${e.stackTrace}")
                        }
                        progressiveMediaSourceFactory.createMediaSource(MediaItem.fromUri(subtitleUrl))
                    }

                exoPlayer?.setMediaSource(MergingMediaSource(*mediaSources.toTypedArray()))
                return
            }

            // LIVE : HLS en priorité. Les manifests ne passent PAS par le proxy d'images
            // de l'instance (il ne relaie ni playlist ni MPD -> le live ne démarrait pas).
            streams.isLive && hlsUrl != null -> {
                val hlsMediaSourceFactory = HlsMediaSource.Factory(PlayerHelper.mediaDataSourceFactory(this))
                    .setPlaylistParserFactory(YoutubeHlsPlaylistParser.Factory())

                val mediaItem = createMediaItem(hlsUrl.toUri(), MimeTypes.APPLICATION_M3U8, streams)
                exoPlayer?.setMediaSource(hlsMediaSourceFactory.createMediaSource(mediaItem))
                return
            }
            streams.isLive && dashUrl != null -> {
                val mediaItem = createMediaItem(dashUrl.toUri(), MimeTypes.APPLICATION_MPD, streams)
                exoPlayer?.setMediaItem(mediaItem)
                return
            }

            // DASH : manifest généré localement (qualité sélectionnable)
            streams.videoStreams.any { it.url?.startsWith("sabr://") != true } -> {
                val dashUri = PlayerHelper.createDashSource(streams.copy(videoStreams = streams.videoStreams.filter {
                    it.url?.startsWith("sabr://") != true
                }), this)

                val mediaItem = createMediaItem(dashUri, MimeTypes.APPLICATION_MPD, streams)
                exoPlayer?.setMediaItem(mediaItem)
            }
            // DASH YouTube en secours quand aucun manifest local n'est construisible
            dashUrl != null -> {
                exoPlayer?.setMediaItem(
                    createMediaItem(dashUrl.toUri(), MimeTypes.APPLICATION_MPD, streams)
                )
                return
            }
            // HLS en dernier recours
            hlsUrl != null -> {
                val hlsMediaSourceFactory = HlsMediaSource.Factory(PlayerHelper.mediaDataSourceFactory(this))
                    .setPlaylistParserFactory(YoutubeHlsPlaylistParser.Factory())

                val mediaItem = createMediaItem(hlsUrl.toUri(), MimeTypes.APPLICATION_M3U8, streams)
                exoPlayer?.setMediaSource(hlsMediaSourceFactory.createMediaSource(mediaItem))
                return
            }
            // NO STREAM FOUND
            else -> {
                toastFromMainThread(R.string.unknown_error)
                return
            }
        }
    }

    private fun createMediaItem(uri: Uri, mimeType: String, streams: Streams) =
        MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setSubtitleConfigurations(getSubtitleConfigs())
            .setMetadata(streams, videoId)
            .build()
    // endregion

    // region Captions
    private fun getSubtitleConfigs(): List<SubtitleConfiguration> = streams?.subtitles
        ?.mapNotNull {
            val subtitleUrl = it.url
            if (subtitleUrl.isNullOrEmpty()) return@mapNotNull null
            val roleFlags = getSubtitleRoleFlags(it)
            SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setRoleFlags(roleFlags)
                .setLanguage(it.code)
                .setLabel(it.name)
                .setMimeType(it.mimeType).build()
        }.orEmpty()
    // endregion
}

// region Video Player
/**
 * The player goes idle both when playback is over and when it failed. Only the first case
 * means the service has nothing left to do, otherwise the error could not be recovered.
 */
internal fun shouldStopOnlinePlayerService(player: Player?, playbackState: Int): Boolean {
    return playbackState == Player.STATE_IDLE && player?.playerError == null
}
// endregion
