package com.watube.yard.util

import okhttp3.HttpUrl

// region Local Extraction
/**
 * Where the extractor may connect. Watube only uses its YouTube service, so every request it
 * sends, redirects included, has to reach YouTube over https: a tampered response or a hostile
 * redirect can neither make it disclose the user's address to another host nor fetch content
 * from one, and nothing it does can reach the phone or the local network.
 */
object ExtractorNetworkPolicy {
    private const val HTTPS_PORT = 443

    /** YouTube's own sites and APIs (www, m, music, charts, suggestions, consent...) */
    private val YOUTUBE_DOMAINS = listOf("youtube.com", "youtu.be")

    /** Exact API and video hosts outside of youtube.com */
    private const val INNERTUBE_HOST = "youtubei.googleapis.com"
    private const val VIDEO_SERVERS_DOMAIN = "googlevideo.com"

    /**
     * When YouTube rate limits an address it redirects to this page, which the extractor
     * recognizes to report a reCAPTCHA: only this path of google.com is allowed.
     */
    private const val GOOGLE_HOST = "www.google.com"
    private const val RATE_LIMIT_PATH = "/sorry/"

    fun isAllowed(url: HttpUrl): Boolean {
        if (!url.isHttps || url.username.isNotEmpty() || url.port != HTTPS_PORT) return false
        val host = url.host
        return YOUTUBE_DOMAINS.any { host == it || host.endsWith(".$it") } ||
            host == INNERTUBE_HOST ||
            host.endsWith(".$VIDEO_SERVERS_DOMAIN") ||
            (host == GOOGLE_HOST && url.encodedPath.startsWith(RATE_LIMIT_PATH))
    }
}
// endregion
