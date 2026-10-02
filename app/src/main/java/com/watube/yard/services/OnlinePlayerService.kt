package com.watube.yard.services

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
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
import com.watube.yard.constants.IntentData
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.parcelable
import com.watube.yard.extensions.setMetadata
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.extensions.updateParameters
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PlayerHelper.getSubtitleRoleFlags
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.player.SabrMediaSource
import com.watube.yard.player.manifest.SabrManifest
import com.watube.yard.util.DeArrowUtil
import com.watube.yard.util.PlayingQueue
import com.watube.yard.util.YoutubeHlsPlaylistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the selected videos audio in background mode with a notification area.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class OnlinePlayerService : AbstractPlayerService() {
    override val isOfflinePlayer: Boolean = false

    // PlaylistId/ChannelId for autoplay
    private var playlistId: String? = null
    private var channelId: String? = null
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
                    // slept for a long time and there was nothing left to resume
                    if (shouldStopOnlinePlayerService(exoPlayer, playbackState)) {
                        onDestroy()
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

        // start loading the video info while keeping a reference to the job
        // so that it can be canceled once a different video is loaded
        fetchVideoInfoJob = scope.launch {
            streams = withContext(Dispatchers.IO) {
                try {
                    MediaServiceRepository.instance.getStreams(videoId).let {
                        DeArrowUtil.deArrowStreams(it, videoId)
                    }
                }  catch (e: Exception) {
                    Log.e(TAG(), e.stackTraceToString())
                    toastFromMainDispatcher(e.localizedMessage.orEmpty())
                    return@withContext null
                }
            } ?: return@launch

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
                configurePlayer(timestampMs)
            }
        }

        fetchVideoInfoJob?.join()
        fetchVideoInfoJob = null
    }

    private fun configurePlayer(seekToPositionMs: Long) {
        // seek to the previous position if available
        if (seekToPositionMs != 0L) {
            exoPlayer?.seekTo(seekToPositionMs)
        } else if (watchPositionsEnabled) {
            DatabaseHelper.getWatchPositionBlocking(videoId)?.let {
                if (!DatabaseHelper.isVideoWatched(it, streams?.duration)) exoPlayer?.seekTo(it)
            }
        }

        exoPlayer?.apply {
            // automatically start playback when using the audio player
            playWhenReady = PlayerHelper.playAutomatically || isAudioOnlyPlayer
            prepare()
        }
    }

    private suspend fun getSponsorBlockSegments(): List<Segment> {
        return runCatching {
            MediaServiceRepository.instance.getSegments(
                videoId,
                sponsorBlockConfig.keys.toList(),
                listOf("skip", "mute", "full", "poi", "chapter")
            ).segments
        }.getOrElse { emptyList() }
    }

    override fun navigateVideo(videoId: String) {
        this.streams = null

        super.navigateVideo(videoId)
    }

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
                            DefaultDataSource.Factory(this), extractorsFactory
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
                val hlsMediaSourceFactory = HlsMediaSource.Factory(DefaultDataSource.Factory(this))
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
                val hlsMediaSourceFactory = HlsMediaSource.Factory(DefaultDataSource.Factory(this))
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

    private fun getSubtitleConfigs(): List<SubtitleConfiguration> = streams?.subtitles
        ?.mapNotNull {
            val subtitleUrl = it.url
            if (subtitleUrl.isNullOrEmpty()) return@mapNotNull null
            val roleFlags = getSubtitleRoleFlags(it)
            SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setRoleFlags(roleFlags)
                .setLanguage(it.code)
                .setMimeType(it.mimeType).build()
        }.orEmpty()

    private fun createMediaItem(uri: Uri, mimeType: String, streams: Streams) =
        MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setSubtitleConfigurations(getSubtitleConfigs())
            .setMetadata(streams, videoId)
            .build()
}

/**
 * The player goes idle both when playback is over and when it failed. Only the first case
 * means the service has nothing left to do, otherwise the error could not be recovered.
 */
internal fun shouldStopOnlinePlayerService(player: Player?, playbackState: Int): Boolean {
    return playbackState == Player.STATE_IDLE && player?.playerError == null
}
