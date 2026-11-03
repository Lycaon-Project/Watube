package com.watube.yard.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toAndroidUri
import com.watube.yard.helpers.DownloadEncryption
import com.watube.yard.ui.interfaces.TimeFrameReceiver
import java.nio.file.Path

// region Seekbar Preview
class OfflineTimeFrameReceiver(
    private val context: Context,
    private val videoSource: Path
) : TimeFrameReceiver() {
    /** Null when the file cannot be read (altered, removed, key lost): no preview then. */
    private val metadataRetriever = MediaMetadataRetriever().let { retriever ->
        runCatching {
            setSource(retriever)
            retriever
        }.onFailure {
            retriever.release()
            Log.e(TAG(), "Seekbar previews unavailable for this download", it)
        }.getOrNull()
    }

    private fun setSource(retriever: MediaMetadataRetriever) {
        if (!DownloadEncryption.isEncrypted(videoSource)) {
            retriever.setDataSource(context, videoSource.toAndroidUri())
            return
        }
        // an encrypted download is read through a source that decrypts it on demand; the
        // retriever closes it once released, or it is closed here if it was refused
        val source = DownloadEncryption.DecryptingMediaDataSource(videoSource)
        try {
            retriever.setDataSource(source)
        } catch (e: RuntimeException) {
            source.close()
            throw e
        }
    }

    override suspend fun getFrameAtTime(position: Long): Bitmap? {
        val retriever = metadataRetriever ?: return null
        return runCatching { retriever.getFrameAtTime(position * 1000) }.getOrNull()
    }

    override fun release() {
        metadataRetriever?.release()
    }
}
// endregion
