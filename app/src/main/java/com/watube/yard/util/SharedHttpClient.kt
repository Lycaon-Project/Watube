package com.watube.yard.util

import android.util.Log
import com.watube.yard.BuildConfig
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.TlsVersion

// region Networking
/**
 * One connection pool and one dispatcher for every HTTP client of the app (API, images,
 * extractor, streams, downloads): each one derives from [base] with newBuilder() and keeps its own
 * settings. Separate clients each held their own threads and idle connections, and the stream
 * client was even built anew for every video, with a fresh TLS handshake each time.
 */
object SharedHttpClient {
    /**
     * Developer trace, debug builds only: one line per request on the network ("WatubeNet" tag),
     * to find repeated or needless calls. The query string is left out (it carries tokens).
     */
    private val debugRequestLog = Interceptor { chain ->
        val request = chain.request()
        Log.d("WatubeNet", "${request.method} ${request.url.host}${request.url.encodedPath}")
        chain.proceed(request)
    }

    /**
     * TLS 1.3 only, for hosts known to support it (SponsorBlock, DeArrow, Return YouTube Dislike,
     * GitHub): no older version is offered, so a downgrade attempt fails instead of succeeding.
     */
    val TLS_1_3_ONLY: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
        .tlsVersions(TlsVersion.TLS_1_3)
        .build()

    val base: OkHttpClient = OkHttpClient.Builder()
        // TLS 1.3 / 1.2 with forward secret AEAD ciphers only, no older fallback. Plain http stays
        // limited to localhost (self-hosted instances) by network_security_config.xml.
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.CLEARTEXT))
        // first network interceptor: it sees the refusals of the policies added after it
        .addNetworkInterceptor(NetworkLedger.interceptor)
        .apply { if (BuildConfig.DEBUG) addNetworkInterceptor(debugRequestLog) }
        // 5 redirects at most, for every client derived from this one
        .apply { RedirectLimit.install(this) }
        // host names resolved over encrypted DNS, unless the user turned it off
        .dns(PrivateDns)
        .build()

    /**
     * Closes the idle connections, so that the next requests open new ones: a renewed streaming
     * session must not travel over the connection of the session it replaces.
     */
    fun dropIdleConnections() = base.connectionPool.evictAll()
}
// endregion
