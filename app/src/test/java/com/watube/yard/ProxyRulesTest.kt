package com.watube.yard

import com.watube.yard.helpers.ProxyHelper
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
