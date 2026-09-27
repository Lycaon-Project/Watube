package com.watube.yard.helpers

import android.util.Log
import com.watube.yard.api.PipedMediaServiceRepository
import com.watube.yard.api.RetrofitInstance
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.extensions.TAG
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ProxyHelper {
    /** Don't hit the instance config endpoint more than twice a day. */
    private const val PROXY_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L

    /**
     * Refreshes the image proxy url of the current instance.
     *
     * The request is throttled: it is skipped when the proxy of the current instance has
     * already been fetched recently, which avoids one network call on every app start.
     */
    suspend fun fetchProxyUrl() {
        val apiUrl = PipedMediaServiceRepository.apiUrl
        val lastFetchInstance = PreferenceHelper.getString(PreferenceKeys.LAST_PROXY_FETCH_INSTANCE, "")
        val lastFetchTime = PreferenceHelper.getLong(PreferenceKeys.LAST_PROXY_FETCH_TIME, 0L)
        val isFresh = lastFetchInstance == apiUrl &&
                System.currentTimeMillis() - lastFetchTime < PROXY_REFRESH_INTERVAL_MS
        if (isFresh) return

        runCatching {
            val fetchedAt = System.currentTimeMillis()
            RetrofitInstance.externalApi.getInstanceConfig(apiUrl)
                .imageProxyUrl
                ?.takeIf { it.isNotEmpty() }
                ?.let { PreferenceHelper.putString(PreferenceKeys.IMAGE_PROXY_URL, it) }

            PreferenceHelper.putString(PreferenceKeys.LAST_PROXY_FETCH_INSTANCE, apiUrl)
            PreferenceHelper.putLong(PreferenceKeys.LAST_PROXY_FETCH_TIME, fetchedAt)
        }.onFailure {
            Log.d(TAG(), "Failed to fetch the instance config", it)
        }
    }

    /**
     * Decide whether the proxy should be used or not for a given stream URL based on user preferences
     */
    fun rewriteUrlUsingProxyPreference(url: String): String {
        return proxyRewriteUrl(url) ?: url
    }

    /**
     * Rewrite the URL to use the stored image proxy url of the selected instance.
     * Can handle both Piped links and normal YouTube links.
     */
    private fun proxyRewriteUrl(url: String?): String? {
        if (url == null) return null

        val proxyUrl = PreferenceHelper.getString(PreferenceKeys.IMAGE_PROXY_URL, "")
            .toHttpUrlOrNull()

        // parsedUrl should now be a plain YouTube URL without using any proxy
        val parsedUrl = unwrapUrl(url).toHttpUrlOrNull()
        if (proxyUrl == null || parsedUrl == null) return null

        return parsedUrl.newBuilder()
            .host(proxyUrl.host)
            .port(proxyUrl.port)
            .setQueryParameter("host", parsedUrl.host)
            .build()
            .toString()
    }

    /**
     * Convert a proxied Piped url to a YouTube url that's not proxied
     *
     * Should not be called directly in most cases, use [rewriteUrlUsingProxyPreference] instead
     */
    fun unwrapUrl(url: String): String {
        val parsedUrl = url.toHttpUrlOrNull() ?: return url

        val host = parsedUrl.queryParameter("host")
        // If the host is not set, the URL is probably already unwrapped
        if (host.isNullOrEmpty()) {
            return url
        }

        return parsedUrl.newBuilder()
            .host(host)
            .removeAllQueryParameters("host")
            .removeAllQueryParameters("qhash")
            .build()
            .toString()
    }
}
