package com.watube.yard

import com.watube.yard.player.parser.SabrClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The SABR URL may come from a Piped instance: only YouTube's video servers are trusted. */
class SabrStreamUrlTest {

    @Test
    fun `https url of the video servers is trusted`() {
        assertTrue(SabrClient.isTrustedStreamUrl("https://rr3---sn-abc.googlevideo.com/videoplayback?id=1"))
    }

    @Test
    fun `any other host, plain http or a local address is refused`() {
        listOf(
            "https://attacker.example/videoplayback",
            "https://googlevideo.com.attacker.example/videoplayback",
            "http://rr3---sn-abc.googlevideo.com/videoplayback",
            "http://127.0.0.1:8080/",
            "https://192.168.1.1/",
            "not a url",
        ).forEach { assertFalse(it, SabrClient.isTrustedStreamUrl(it)) }
    }
}
