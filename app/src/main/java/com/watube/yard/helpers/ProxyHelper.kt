package com.watube.yard.helpers

import android.os.SystemClock
import android.util.Log
import com.watube.yard.api.PipedMediaServiceRepository
import com.watube.yard.api.RetrofitInstance
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.extensions.TAG
import com.watube.yard.util.PublicAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

// region Piped Instances
/**
 * Where YouTube media (thumbnails, avatars, banners, video segments) are requested from.
 *
 * - Full local mode: straight from YouTube, the Piped instance is never contacted (not even
 *   for its configuration), and a URL an instance proxied earlier goes back to YouTube.
 * - Piped modes: YouTube media only through the media proxy of the selected instance. While
 *   that proxy is unknown (not fetched yet, instance down, invalid answer) nothing is requested
 *   from Google: the image stays empty rather than revealing the IP address to Google.
 */
object ProxyHelper {
    /** Don't hit the instance config endpoint more than twice a day. */
    private const val PROXY_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L

    /** A failed fetch is retried at most once a minute, not once per image. */
    private const val RETRY_INTERVAL_MS = 60 * 1000L

    private const val HTTPS_PORT = 443

    /** Hosts YouTube serves its media from. */
    private val YOUTUBE_MEDIA_DOMAINS =
        listOf("ytimg.com", "ggpht.com", "googleusercontent.com", "googlevideo.com")

    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isFetching = AtomicBoolean(false)

    @Volatile
    private var lastAttempt = 0L

    private val usesInstance get() = !PlayerHelper.fullLocalMode

    /**
     * Refreshes the media proxy of the selected instance (Piped modes only). Throttled: skipped
     * when the proxy of this instance was fetched recently, unless [force]d after the instance
     * or the mode changed.
     */
    fun refreshProxyUrl(force: Boolean = false) {
        if (!usesInstance) return
        val apiUrl = PipedMediaServiceRepository.apiUrl
        val isFresh = !force && isProxyOf(apiUrl) && System.currentTimeMillis() -
            PreferenceHelper.getLong(PreferenceKeys.LAST_PROXY_FETCH_TIME, 0L) < PROXY_REFRESH_INTERVAL_MS
        if (isFresh || !isFetching.compareAndSet(false, true)) return
        lastAttempt = SystemClock.elapsedRealtime()

        scope.launch {
            runCatching {
                val proxy = RetrofitInstance.instanceConfigApi
                    .getInstanceConfig("${apiUrl.trimEnd('/')}/config")
                    .imageProxyUrl
                    ?.toHttpUrlOrNull()
                    ?.takeIf { isAcceptableProxy(it, apiUrl.toHttpUrlOrNull()?.host) }
                // stored even when empty: the proxy of the previous instance must not survive
                PreferenceHelper.putString(PreferenceKeys.IMAGE_PROXY_URL, proxy?.toString().orEmpty())
                PreferenceHelper.putString(PreferenceKeys.LAST_PROXY_FETCH_INSTANCE, apiUrl)
                PreferenceHelper.putLong(PreferenceKeys.LAST_PROXY_FETCH_TIME, System.currentTimeMillis())
            }.onFailure {
                Log.d(TAG(), "Failed to fetch the instance config", it)
            }
            isFetching.set(false)
        }
    }

    /**
     * Over https only (plain http for an instance running on the device itself), never Google,
     * and on the public internet unless it is the very host of the instance the user chose: an
     * instance must not be able to point the app at another machine of the user's network.
     */
    private fun isAcceptableProxy(url: HttpUrl, instanceHost: String?): Boolean {
        val reachable = when {
            // resolved here (Dispatchers.IO): a public looking name can point at the LAN
            url.isHttps -> url.host == instanceHost || PublicAddress.resolvesToPublic(url.host)
            else -> url.host in LOOPBACK_HOSTS
        }
        return reachable && !isYouTubeMedia(url.host)
    }

    private fun isProxyOf(apiUrl: String) =
        PreferenceHelper.getString(PreferenceKeys.LAST_PROXY_FETCH_INSTANCE, "") == apiUrl

    /** The media proxy of the selected instance, null when unknown (a fetch is then started). */
    private fun currentProxy(): HttpUrl? {
        val proxy = PreferenceHelper.getString(PreferenceKeys.IMAGE_PROXY_URL, "")
            .takeIf { isProxyOf(PipedMediaServiceRepository.apiUrl) }
            ?.toHttpUrlOrNull()
        if (proxy == null && SystemClock.elapsedRealtime() - lastAttempt > RETRY_INTERVAL_MS) {
            refreshProxyUrl()
        }
        return proxy
    }

    private fun isYouTubeMedia(host: String) =
        YOUTUBE_MEDIA_DOMAINS.any { host == it || host.endsWith(".$it") }

    /**
     * The URL an image is loaded from, null when it must not be loaded (Piped modes while the
     * proxy of the instance is unknown).
     */
    fun imageUrl(url: String): String? =
        if (usesInstance) throughProxy(url) else unwrapUrl(url)

    /**
     * The URL a video / audio segment is loaded from, null when it must not be loaded. Only the
     * pure Piped mode routes them through the instance: locally extracted URLs are bound to the
     * device's IP address and could not be fetched by the proxy.
     */
    fun streamUrl(url: String): String? =
        if (usesInstance && !PlayerHelper.localStreamExtraction) throughProxy(url) else url

    /**
     * YouTube media through the proxy of the selected instance. Anything else (a URL already
     * served by an instance proxy, which may carry its signature, DeArrow thumbnails...) is
     * left untouched: it does not reach Google.
     */
    private fun throughProxy(url: String): String? = throughProxy(url, ::currentProxy)

    /** [throughProxy] with the proxy given: only asked for when the URL is YouTube media. */
    internal fun throughProxy(url: String, proxyOf: () -> HttpUrl?): String? {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (!isYouTubeMedia(parsed.host)) return url
        val proxy = proxyOf() ?: return null

        return parsed.newBuilder()
            .scheme(proxy.scheme)
            .host(proxy.host)
            .port(proxy.port)
            .setQueryParameter("host", parsed.host)
            .build()
            .toString()
    }

    /**
     * Applies [imageUrl] to every request of the image loader, so that no image path (lists,
     * notifications, seek bar previews, downloads) can bypass it. A blocked image fails.
     */
    class ImageInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.toString()
            val target = imageUrl(url) ?: throw IOException("YouTube media held back: no instance proxy")
            if (target == url) return chain.proceed(request)

            return chain.proceed(request.newBuilder().url(target).build())
        }
    }

    /**
     * Convert a proxied Piped url to a YouTube url that's not proxied
     */
    fun unwrapUrl(url: String): String {
        val parsedUrl = url.toHttpUrlOrNull() ?: return url

        // without a YouTube host to go back to, the URL is not a proxied one
        val host = parsedUrl.queryParameter("host")?.takeIf(::isYouTubeMedia) ?: return url

        return parsedUrl.newBuilder()
            .scheme("https")
            .host(host)
            .port(HTTPS_PORT)
            .removeAllQueryParameters("host")
            .removeAllQueryParameters("qhash")
            .build()
            .toString()
    }
}
// endregion
