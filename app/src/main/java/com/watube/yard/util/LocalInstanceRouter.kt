package com.watube.yard.util

import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// region Networking
/**
 * Sends each request through [client], which speaks https only, except the plain http requests
 * to an instance running on the phone itself (http://localhost, http://127.0.0.1,
 * http://[::1]): those go through a copy of [client] that speaks plain http and nothing else.
 *
 * The copy keeps the settings of [client] (interceptors, cache, timeouts, connection pool) but
 * follows no redirect: whatever the local service answers, the request cannot be led to another
 * machine, nor to another service of the phone. Every other request, a redirect included, stays
 * on [client] and is refused there if it is not https.
 */
class LocalInstanceRouter(private val client: OkHttpClient) : Call.Factory {
    private val localClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override fun newCall(request: Request): Call =
        if (isLocalInstance(request.url)) localClient.newCall(request) else client.newCall(request)

    companion object {
        /** The names of the phone itself, the only hosts ever reached in plain http. */
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        /** Whether [url] is a plain http address of the phone itself. */
        fun isLocalInstance(url: HttpUrl): Boolean = !url.isHttps && url.host in LOOPBACK_HOSTS
    }
}
// endregion
