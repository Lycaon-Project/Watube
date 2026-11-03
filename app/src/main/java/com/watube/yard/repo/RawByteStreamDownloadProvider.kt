package com.watube.yard.repo

import android.util.Log
import com.watube.yard.api.GenericUserAgentInterceptor
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.extensions.TAG
import com.watube.yard.helpers.DownloadHelper
import com.watube.yard.util.SharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.IOException
import okio.source
import java.time.Duration
import kotlin.io.path.fileSize
import kotlin.math.min

// region Downloads
/**
 * Download from RAW HTTP stream.
 */
class RawByteStreamDownloadProvider(private val url: HttpUrl) : DownloadProvider {
    private val httpClient: OkHttpClient by lazy {
        SharedHttpClient.base.newBuilder()
            .connectTimeout(Duration.ofMillis(DownloadHelper.DEFAULT_TIMEOUT.toLong()))
            .readTimeout(Duration.ofMillis(DownloadHelper.DEFAULT_TIMEOUT.toLong()))
            .retryOnConnectionFailure(true)
            // downloads must not leak `okhttp/x.y.z` either: same UA as the API calls
            .addInterceptor(GenericUserAgentInterceptor())
            .build()
    }

    override suspend fun downloadNextChunk(
        item: DownloadItem,
        sink: BufferedSink,
    ): DownloadProgressResult {
        val startByteOffset = item.path.fileSize()
        val isSizeKnown = item.downloadSize > 0L
        // nothing left to ask for: a range past the end would only get an HTTP 416
        if (isSizeKnown && startByteOffset >= item.downloadSize) return DownloadProgressResult.DownloadComplete

        val source =
            startConnection(url, startByteOffset, item.downloadSize) ?: return DownloadProgressResult.Failed

        val sourceByte = source.byteStream().source()

        var totalRead = 0L
        var lastRead = 0L
        // Check if downloading is still active and read next bytes.
        while (sourceByte
                .read(sink.buffer, DownloadHelper.DOWNLOAD_CHUNK_SIZE)
                .also { lastRead = it } != -1L
        ) {
            sink.emit()
            totalRead += lastRead
        }

        withContext(Dispatchers.IO) {
            sourceByte.close()
            source.close()
        }

        // without a known size the request has no end bound: the end of the body is the end of the file
        return if (isSizeKnown && startByteOffset + totalRead < item.downloadSize) {
            DownloadProgressResult.Progressed(totalRead)
        } else {
            DownloadProgressResult.DownloadComplete
        }
    }

    private suspend fun startConnection(
        url: HttpUrl,
        alreadyRead: Long,
        totalSize: Long
    ): ResponseBody? {
        val request = Request.Builder()
            .url(url)
            .method("GET", null)
            .apply { rangeHeaderValue(alreadyRead, totalSize)?.let { header("Range", it) } }
            .build()

        return withContext(Dispatchers.IO) {
            // Retry connecting to server for n times.
            try {
                val call = httpClient.newCall(request)
                val response = call.execute()

                if (response.code == 403) {
                    response.close()
                    Log.e(TAG(), "Got HTTP 403 while downloading")
                    return@withContext null
                } else if (!isUsableResponse(response.code, alreadyRead)) {
                    response.close()
                    return@withContext null // TODO: print response.message
                }

                return@withContext response.body
            } catch (e: IOException) {
                // printStackTrace() returns Unit: the old code logged "kotlin.Unit" and
                // its output would survive the release log stripping anyway
                Log.e(this.javaClass.name, "failed to open the raw media stream", e)
                // TODO: forward error message

                return@withContext null
            }
        }
    }

    companion object {
        // maximum working tested chunk size is 3MB, the 512MB value here is from NewPipe
        internal const val BYTES_PER_REQUEST = 512 * 1024L

        private const val HTTP_PARTIAL_CONTENT = 206

        /**
         * Value of the `Range` header for the request starting at [start], or `null` to send none.
         *
         * A [totalSize] of 0 or less means unknown (subtitles): the request then has no end bound,
         * `bytes=0--1` being invalid, and the first one no range at all.
         * The end of a range is inclusive (RFC 9110 14.1.2).
         */
        internal fun rangeHeaderValue(start: Long, totalSize: Long): String? {
            if (totalSize <= 0L) return if (start > 0L) "bytes=$start-" else null

            val end = min(totalSize, start + BYTES_PER_REQUEST) - 1
            return "bytes=$start-$end"
        }

        /**
         * Whether a response with the HTTP [code] can be appended to the [alreadyRead] bytes.
         *
         * A resumed download needs a 206: a 200 means the range was ignored and the body
         * starts at byte 0 again, which would corrupt the file.
         */
        internal fun isUsableResponse(code: Int, alreadyRead: Long): Boolean = when {
            code !in 200..299 -> false
            alreadyRead > 0L -> code == HTTP_PARTIAL_CONTENT
            else -> true
        }
    }
}
// endregion
