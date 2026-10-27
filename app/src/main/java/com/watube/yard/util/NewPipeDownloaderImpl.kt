package com.watube.yard.util

import com.watube.yard.helpers.PrivacyHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

// region Local Extraction
class NewPipeDownloaderImpl : Downloader() {
    // the app wide pool: the extractor's requests reuse the connections of the other clients
    private val client: OkHttpClient = SharedHttpClient.base.newBuilder()
        // a network interceptor sees every hop, redirects included: each one must stay on
        // YouTube over https (see ExtractorNetworkPolicy)
        .addNetworkInterceptor(Interceptor { chain ->
            val url = chain.request().url
            if (!ExtractorNetworkPolicy.isAllowed(url)) {
                throw IOException("Extractor request refused: ${url.scheme}://${url.host}")
            }
            chain.proceed(chain.request())
        })
        // an https -> http redirect would expose the request on the network
        .followSslRedirects(false)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", PrivacyHelper.userAgent)

        for ((headerKey, headerValues) in headers) {
            requestBuilder.removeHeader(headerKey)
            for (headerValue in headerValues) {
                requestBuilder.addHeader(headerKey, headerValue)
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                readBounded(response),
                response.request.url.toString()
            )
        }
    }

    /**
     * The body, refused beyond [MAX_RESPONSE_BYTES]: it was read whole into memory, so a
     * hostile or broken endpoint could exhaust the heap (the largest legitimate response, the
     * player JavaScript, weighs a few megabytes).
     */
    private fun readBounded(response: okhttp3.Response): String {
        val body = response.body
        val source = body.source()
        if (source.request(MAX_RESPONSE_BYTES + 1)) {
            throw IOException("Extractor response larger than $MAX_RESPONSE_BYTES bytes")
        }
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return source.buffer.readString(charset)
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RESPONSE_BYTES = 32L * 1024 * 1024
    }
}
// endregion
