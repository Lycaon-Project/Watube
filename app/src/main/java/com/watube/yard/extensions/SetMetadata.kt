package com.watube.yard.extensions

import android.media.MediaMetadata as FrameworkMediaMetadata
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.watube.yard.api.JsonHelper
import com.watube.yard.api.obj.Streams
import com.watube.yard.constants.IntentData
import com.watube.yard.db.obj.DownloadChapter
import com.watube.yard.db.obj.DownloadWithItems

@OptIn(UnstableApi::class)
fun MediaItem.Builder.setMetadata(streams: Streams, videoId: String) = apply {
    // Avoid reaching the max parcelable size of 1MB for binder transactions.
    val clearedStreams = streams.copy(audioStreams = emptyList(), videoStreams = emptyList())
    val extras = bundleOf(
        FrameworkMediaMetadata.METADATA_KEY_TITLE to streams.title,
        FrameworkMediaMetadata.METADATA_KEY_ARTIST to streams.uploader,
        IntentData.videoId to videoId,
        // JSON-encode as work-around for https://github.com/androidx/media/issues/564
        IntentData.streams to JsonHelper.json.encodeToString(clearedStreams),
        IntentData.chapters to JsonHelper.json.encodeToString(streams.chapters)
    )
    setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(streams.title)
            .setArtist(streams.uploader)
            // live streams have no duration (0 or less): no length must reach the session,
            // otherwise the system media controls draw a seek bar of a bogus length
            .setDurationMs(streams.duration.takeIf { it > 0 }?.times(1000))
            .setArtworkUri(streams.thumbnailUrl.toUri())
            .setComposer(streams.uploaderUrl.orEmpty().toID())
            .setExtras(extras)
            // send a unique timestamp to notify that the metadata changed, even if playing the same video twice
            .setTrackNumber(System.currentTimeMillis().mod(Int.MAX_VALUE))
            .build()
    )
}

@OptIn(UnstableApi::class)
fun MediaItem.Builder.setMetadata(downloadWithItems: DownloadWithItems) = apply {
    val (download, _, downloadChapters) = downloadWithItems
    val chapters = downloadChapters.map(DownloadChapter::toChapterSegment)
    val streams = downloadWithItems.toStreams()

    val extras = bundleOf(
        FrameworkMediaMetadata.METADATA_KEY_TITLE to download.title,
        FrameworkMediaMetadata.METADATA_KEY_ARTIST to download.uploader,
        IntentData.videoId to download.videoId,
        IntentData.streams to JsonHelper.json.encodeToString(streams),
        IntentData.chapters to JsonHelper.json.encodeToString(chapters)
    )
    setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(download.title)
            .setArtist(download.uploader)
            .setDurationMs(download.duration?.takeIf { it > 0 }?.times(1000))
            .setArtworkUri(download.thumbnailPath?.toAndroidUri())
            .setExtras(extras)
            // send a unique timestamp to notify that the metadata changed, even if playing the same video twice
            .setTrackNumber(System.currentTimeMillis().mod(Int.MAX_VALUE))
            .build()
    )
}
