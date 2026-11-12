package com.watube.yard

import com.watube.yard.helpers.ProxyHelper
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where YouTube media may be fetched from in the Piped modes (ProxyHelper). */
class ProxyRulesTest {
    private val proxy = "https://pipedproxy.example.org".toHttpUrl()

    @Test
    fun youTubeMediaGoThroughTheProxy() {
        assertEquals(
            "https://pipedproxy.example.org/vi/abc/hqdefault.jpg?host=i.ytimg.com",
            ProxyHelper.throughProxy("https://i.ytimg.com/vi/abc/hqdefault.jpg") { proxy }
        )
        assertEquals(
            "https://pipedproxy.example.org/videoplayback?id=1&host=rr3---sn-a1.googlevideo.com",
            ProxyHelper.throughProxy("https://rr3---sn-a1.googlevideo.com/videoplayback?id=1") { proxy }
        )
    }

    @Test
    fun youTubeMediaAreHeldBackWithoutAProxy() {
        assertNull(ProxyHelper.throughProxy("https://yt3.ggpht.com/avatar=s88") { null })
    }

    @Test
    fun otherHostsAreLeftUntouched() {
        // already proxied by an instance: its signature (qhash) must survive
        val proxied = "https://pipedproxy.other.org/vi/abc/hq.jpg?host=i.ytimg.com&qhash=1234"
        assertEquals(proxied, ProxyHelper.throughProxy(proxied) { null })
        // DeArrow thumbnails, look-alike domains
        val dearrow = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=abc&time=1"
        assertEquals(dearrow, ProxyHelper.throughProxy(dearrow) { null })
        val lookAlike = "https://evilytimg.com/vi/abc.jpg"
        assertEquals(lookAlike, ProxyHelper.throughProxy(lookAlike) { null })
    }

    @Test
    fun relayFetchesYouTubeAndTheSelectedInstanceOnly() {
        val instance = "https://pipedapi.example.org".toHttpUrl()
        fun allowed(url: String, local: Boolean = false) = if (local) {
            ProxyHelper.isRelayTarget(url.toHttpUrl(), null, null)
        } else {
            ProxyHelper.isRelayTarget(url.toHttpUrl(), instance, proxy)
        }
        assertTrue(allowed("https://manifest.googlevideo.com/api/manifest/hls_variant/x", local = true))
        assertTrue(allowed("https://rr1---sn-a1.googlevideo.com/videoplayback?id=1", local = true))
        assertTrue(allowed("https://www.youtube.com/api/timedtext?v=abc&fmt=vtt", local = true))
        assertTrue(allowed("https://pipedproxy.example.org/api/manifest/hls_variant/x?host=manifest.googlevideo.com"))
        assertTrue(allowed("https://pipedapi.example.org/x.m3u8"))
        // a host or a port picked by a hostile playlist
        assertFalse(allowed("https://attacker.example/x.ts"))
        assertFalse(allowed("https://rr1---sn-a1.googlevideo.com:8443/videoplayback"))
        assertFalse(allowed("https://pipedproxy.example.org:8443/x.ts"))
        assertFalse(allowed("http://rr1---sn-a1.googlevideo.com/videoplayback"))
        assertFalse(allowed("https://evilgooglevideo.com/videoplayback"))
        // the instance and its proxy are trusted in the Piped modes only
        assertFalse(allowed("https://pipedproxy.example.org/x.ts", local = true))
    }

    @Test
    fun unwrapGoesBackToYouTubeOverHttps() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq.jpg",
            ProxyHelper.unwrapUrl("http://localhost:8080/vi/abc/hq.jpg?host=i.ytimg.com&qhash=1234")
        )
        // a "host" parameter that is no YouTube media host is not a proxied URL
        val other = "https://example.org/page?host=example.com"
        assertEquals(other, ProxyHelper.unwrapUrl(other))
    }
}
