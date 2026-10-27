package com.watube.yard.cast

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// region Stolm
/** One video as the TV plays it. */
internal data class StolmItem(
    val videoId: String,
    /** The phone-side item: title, artist and artwork for the notification, never sent to the TV. */
    val mediaItem: MediaItem,
    val title: String,
    val isLive: Boolean,
    val durationMs: Long
)

/**
 * Stolm, Watube's own Cast: the TV as a Media3 [Player]. The media session, its notification,
 * the player screen and the volume keys drive the receiver through it, like the local player.
 *
 * The receiver reports its state in MEDIA_STATUS messages; between two of them the position
 * is extrapolated here, so nothing is polled. A command shows its effect right away and is
 * confirmed (or corrected) by the next status.
 */
@OptIn(UnstableApi::class)
internal class StolmPlayer(
    looper: Looper,
    private val session: CastSession,
    seekIncrementMs: Long,
    /** Asked to end the cast: the session player goes back to the phone. */
    private val onStopRequested: () -> Unit
) : SimpleBasePlayer(looper) {
    private val scope = CoroutineScope(SupervisorJob() + Handler(looper).asCoroutineDispatcher())
    private val seekIncrementMs = seekIncrementMs.coerceAtLeast(1)

    private var item: StolmItem? = null
    private var source: Source? = null
    private var status: CastMediaStatus? = null
    private var isLoading = false
    private var volume: CastVolume? = null
    private var playlistMetadata = MediaMetadata.EMPTY

    /** What the user asked for, until the receiver reports it. */
    private var requestedPlayWhenReady: Boolean? = null
    private var requestedPositionMs: Long? = null

    /** The published stream, to load it again (replay after the end, retry after an error). */
    private class Source(val url: String, val formats: HlsFormats)

    /** The video on the TV, null until the first [load]. */
    val currentItem: StolmItem? get() = item

    init {
        scope.launch {
            session.mediaStatus.collect { newStatus ->
                // while a LOAD runs, the end of the previous media is not this one's state
                if (isLoading || newStatus == null) return@collect
                status = newStatus
                requestedPlayWhenReady = null
                requestedPositionMs = null
                invalidateState()
            }
        }
        scope.launch {
            session.volume.collect {
                volume = it
                invalidateState()
            }
        }
    }

    /** Plays [newItem], published at [url], from [positionMs]. Throws when the TV refuses it. */
    suspend fun load(newItem: StolmItem, url: String, positionMs: Long, formats: HlsFormats) {
        item = newItem
        source = Source(url, formats)
        status = null
        isLoading = true
        requestedPlayWhenReady = true
        requestedPositionMs = positionMs.takeUnless { newItem.isLive }
        invalidateState()
        try {
            session.load(url, CONTENT_TYPE_HLS, newItem.isLive, newItem.title, positionMs / 1000.0, formats)
        } finally {
            isLoading = false
            status = session.mediaStatus.value
            invalidateState()
        }
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setDeviceInfo(DEVICE_INFO)
            .setDeviceVolume(((volume?.level ?: 0.0) * MAX_VOLUME).roundToInt())
            .setIsDeviceMuted(volume?.muted == true)
            .setPlaylistMetadata(playlistMetadata)
            .setSeekBackIncrementMs(seekIncrementMs)
            .setSeekForwardIncrementMs(seekIncrementMs)

        val item = item ?: return builder.setAvailableCommands(BASE_COMMANDS).build()
        val status = status.takeUnless { isLoading }
        val playbackState = StolmStatus.playbackStateOf(status, isLoading)
        val durationMs = status?.durationSeconds?.takeIf { it > 0 }?.let { (it * 1000).roundToLong() }
            ?: item.durationMs.takeIf { it > 0 }

        return builder
            .setAvailableCommands(if (item.isLive) LIVE_COMMANDS else COMMANDS)
            .setPlaylist(
                listOf(
                    MediaItemData.Builder(item.videoId)
                        .setMediaItem(item.mediaItem)
                        .setDurationUs(durationMs?.times(1000) ?: C.TIME_UNSET)
                        .setIsSeekable(!item.isLive)
                        .setIsDynamic(item.isLive)
                        .apply { if (item.isLive) setLiveConfiguration(MediaItem.LiveConfiguration.UNSET) }
                        .build()
                )
            )
            .setPlaybackState(playbackState)
            .setPlayWhenReady(
                requestedPlayWhenReady ?: StolmStatus.playWhenReadyOf(status),
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE
            )
            .setIsLoading(playbackState == Player.STATE_BUFFERING)
            .setPlayerError(
                if (status?.playerState == StolmStatus.IDLE && status.idleReason == "ERROR") {
                    PlaybackException("Cast receiver error", null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
                } else {
                    null
                }
            )
            .setContentPositionMs(position(status, durationMs))
            .build()
    }

    private fun position(status: CastMediaStatus?, durationMs: Long?): PositionSupplier {
        requestedPositionMs?.let { return PositionSupplier.getConstant(it) }
        if (status == null) return PositionSupplier.ZERO
        val reportedMs = (status.positionSeconds * 1000).roundToLong()
        if (status.playerState != StolmStatus.PLAYING) return PositionSupplier.getConstant(reportedMs)
        val elapsedMs = (System.nanoTime() - status.receivedAtNanos) / 1_000_000
        val nowMs = (reportedMs + elapsedMs * status.playbackRate).roundToLong()
        return PositionSupplier.getExtrapolating(
            durationMs?.let { nowMs.coerceAtMost(it) } ?: nowMs,
            status.playbackRate.toFloat()
        )
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        requestedPlayWhenReady = playWhenReady
        val state = StolmStatus.playbackStateOf(status, isLoading)
        when {
            // a finished or failed media is gone from the receiver: load it again
            playWhenReady && (state == Player.STATE_ENDED || state == Player.STATE_IDLE) ->
                reload(if (state == Player.STATE_ENDED) 0 else currentPositionMs())
            playWhenReady -> scope.launch { runCatching { session.play() } }
            else -> scope.launch { runCatching { session.pause() } }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (StolmStatus.playbackStateOf(status, isLoading) == Player.STATE_IDLE) reload(currentPositionMs())
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = if (positionMs == C.TIME_UNSET) 0 else positionMs.coerceAtLeast(0)
        requestedPositionMs = target
        when (StolmStatus.playbackStateOf(status, isLoading)) {
            Player.STATE_ENDED, Player.STATE_IDLE -> reload(target)
            else -> scope.launch { runCatching { session.seek(target / 1000.0) } }
        }
        return Futures.immediateVoidFuture()
    }

    private fun currentPositionMs() = requestedPositionMs
        ?: status?.let { (it.positionSeconds * 1000).roundToLong() } ?: 0

    private fun reload(positionMs: Long) {
        val item = item ?: return
        val source = source ?: return
        scope.launch {
            // a refused reload leaves the IDLE state on screen: nothing more to do here
            runCatching { load(item, source.url, positionMs, source.formats) }
        }
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        val level = deviceVolume.coerceIn(0, MAX_VOLUME) / MAX_VOLUME.toDouble()
        volume = CastVolume(level, volume?.muted == true)
        scope.launch { runCatching { session.setVolume(level) } }
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int) = handleSetDeviceVolume(deviceVolume + 1, flags)

    override fun handleDecreaseDeviceVolume(flags: Int) = handleSetDeviceVolume(deviceVolume - 1, flags)

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        volume = CastVolume(volume?.level ?: 0.0, muted)
        scope.launch { runCatching { session.setMuted(muted) } }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaylistMetadata(playlistMetadata: MediaMetadata): ListenableFuture<*> {
        this.playlistMetadata = playlistMetadata
        return Futures.immediateVoidFuture()
    }

    /** "Stop" from the notification, a headset or the player screen ends the cast. */
    override fun handleStop(): ListenableFuture<*> {
        onStopRequested()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    companion object {
        const val CONTENT_TYPE_HLS = "application/x-mpegURL"

        /** Volume steps of the TV for the volume keys and the slider. */
        const val MAX_VOLUME = 20


        private val DEVICE_INFO = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMaxVolume(MAX_VOLUME)
            .build()

        private val BASE_COMMANDS = Player.Commands.Builder().addAll(
            COMMAND_STOP,
            COMMAND_SET_PLAYLIST_METADATA,
            COMMAND_GET_DEVICE_VOLUME,
            COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
            COMMAND_RELEASE
        ).build()

        private val LIVE_COMMANDS = BASE_COMMANDS.buildUpon().addAll(
            COMMAND_PLAY_PAUSE,
            COMMAND_PREPARE,
            COMMAND_GET_CURRENT_MEDIA_ITEM,
            COMMAND_GET_TIMELINE,
            COMMAND_GET_METADATA
        ).build()

        private val COMMANDS = LIVE_COMMANDS.buildUpon().addAll(
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_DEFAULT_POSITION,
            COMMAND_SEEK_BACK,
            COMMAND_SEEK_FORWARD
        ).build()

        /** Whether this player plays on another device: the TV of a Stolm cast. */
        val Player.isRemote: Boolean
            get() = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    }
}

/** How the receiver's MEDIA_STATUS reads as a Media3 player state (no Android class: JVM-tested). */
internal object StolmStatus {
    const val PLAYING = "PLAYING"
    const val IDLE = "IDLE"

    /** The Media3 playback state of a receiver status (null: nothing reported yet). */
    fun playbackStateOf(status: CastMediaStatus?, isLoading: Boolean): Int = when {
        isLoading || status == null -> Player.STATE_BUFFERING
        status.playerState == PLAYING || status.playerState == "PAUSED" -> Player.STATE_READY
        status.playerState == IDLE && status.idleReason == "FINISHED" -> Player.STATE_ENDED
        status.playerState == IDLE -> Player.STATE_IDLE
        // BUFFERING, LOADING
        else -> Player.STATE_BUFFERING
    }

    /** Whether the receiver plays (or will once buffered): a load always autoplays. */
    fun playWhenReadyOf(status: CastMediaStatus?): Boolean =
        status == null || status.playerState in setOf(PLAYING, "BUFFERING", "LOADING")
}
// endregion
