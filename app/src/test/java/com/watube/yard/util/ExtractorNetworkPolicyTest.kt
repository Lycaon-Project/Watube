package com.watube.yard.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// region Local Extraction
class ExtractorNetworkPolicyTest {

    @Test
    fun `youtube hosts over https are allowed`() {
        listOf(
            "https://www.youtube.com/youtubei/v1/player",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/youtubei/v1/search",
            "https://suggestqueries-clients6.youtube.com/complete/search?q=a",
            "https://youtubei.googleapis.com/youtubei/v1/player?id=a",
            "https://rr3---sn-25ge7nsk.googlevideo.com/videoplayback?itag=18",
            "https://www.google.com/sorry/index?continue=x"
        ).forEach { assertTrue(it, ExtractorNetworkPolicy.isAllowed(it.toHttpUrl())) }
    }

    @Test
    fun `everything else is refused`() {
        listOf(
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com:8443/",
            "https://user@www.youtube.com/",
            "https://evil-youtube.com/",
            "https://youtube.com.evil.example/",
            "https://googleapis.com/",
            "https://storage.googleapis.com/bucket",
            "https://googlevideo.com/videoplayback",
            "https://www.google.com/search?q=watube",
            "https://127.0.0.1/",
            "https://192.168.1.1/"
        ).forEach { assertFalse(it, ExtractorNetworkPolicy.isAllowed(it.toHttpUrl())) }
    }
}
// endregion
