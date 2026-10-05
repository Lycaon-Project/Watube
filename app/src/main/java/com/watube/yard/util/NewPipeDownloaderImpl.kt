package com.watube.yard.util

import com.watube.yard.helpers.PrivacyHelper
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class NewPipeDownloaderImpl : Downloader() {
    // the app wide pool: the extractor's requests reuse the connections of the other clients
    private val client = SharedHttpClient.base

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
        val response = client.newCall(requestBuilder.build()).execute()

        return when (response.code) {
            429 -> {
                response.close()
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            else -> {
                val responseBodyToReturn = response.body.string()
                Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBodyToReturn,
                    response.request.url.toString()
                )
            }
        }
    }
}