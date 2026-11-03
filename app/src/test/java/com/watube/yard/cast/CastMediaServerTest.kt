package com.watube.yard.cast

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** The relay that serves the cast video, against a fake HLS origin on the loopback. */
class CastMediaServerTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val client = OkHttpClient()
    private val segment = ByteArray(1000) { it.toByte() }

    /** Minimal origin: a master playlist, a variant playlist and one segment. */
    private val origin = ServerSocket(0, 8, loopback).also { server ->
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    val path = it.getInputStream().bufferedReader().readLine().split(' ')[1]
                    val (type, body) = when (path) {
                        "/hls/master.m3u8" -> "application/vnd.apple.mpegurl" to
                            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv1/index.m3u8\n".toByteArray()
                        "/hls/v1/index.m3u8" -> "application/vnd.apple.mpegurl" to
                            "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:5,\nseg1.ts\n#EXT-X-ENDLIST\n".toByteArray()
                        "/hls/v1/seg1.ts" -> "video/mp2t" to segment
                        else -> "text/plain" to ByteArray(0)
                    }
                    val status = if (body.isEmpty()) "404 Not Found" else "200 OK"
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }
    }
    private val originUrl = "http://127.0.0.1:${origin.localPort}/hls/master.m3u8"
    private val servers = mutableListOf<CastMediaServer>()

    private fun server(peer: InetAddress = loopback, route: (String) -> String? = { it }) =
        CastMediaServer(loopback, peer, client, route).also { servers += it }

    private fun get(url: String, origin: String? = null) = client.newCall(
        Request.Builder().url(url).apply { origin?.let { header("Origin", it) } }.build()
    ).execute()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
        origin.close()
    }

    @Test
    fun playlistsAreRewrittenAndSegmentsRelayed() {
        val master = server().publish(originUrl)
        val masterText = get(master).use { it.body.string() }
        val variantUrl = masterText.lines().first { it.startsWith("http://127.0.0.1") }
        assertTrue("never the origin", !masterText.contains(":${origin.localPort}"))

        val variantText = get(variantUrl).use { it.body.string() }
        assertTrue(variantText.contains("#EXT-X-MAP:URI=\"http://127.0.0.1"))
        val segmentUrl = variantText.lines().first { it.startsWith("http://127.0.0.1") }
        assertArrayEquals(segment, get(segmentUrl).use { it.body.bytes() })
    }

    @Test
    fun wrongTokenOldTokenAndOtherMethodsAreRefused() {
        val server = server()
        val first = server.publish(originUrl)
        val forged = first.replace(Regex("/[0-9a-f]{64}/"), "/" + "0".repeat(64) + "/")
        assertEquals(404, get(forged).use { it.code })

        val second = server.publish(originUrl)
        // the previous video is not reachable anymore
        assertEquals(404, get(first).use { it.code })

        // without the token nothing is told, not even that the method is refused
        val post = { url: String -> client.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).execute() }
        assertEquals(404, post(first).use { it.code })
        assertEquals(405, post(second).use { it.code })
    }

    @Test
    fun corsOnlyForTheReceiver() {
        val master = server().publish(originUrl)
        get(master, origin = "https://www.gstatic.com").use {
            assertEquals("https://www.gstatic.com", it.header("Access-Control-Allow-Origin"))
        }
        get(master, origin = "https://evil.example").use {
            assertNull(it.header("Access-Control-Allow-Origin"))
        }
    }

    @Test
    fun corsPreflightIsAnsweredForTheReceiverOnly() {
        val master = server().publish(originUrl)
        fun options(origin: String) = client.newCall(
            Request.Builder().url(master).method("OPTIONS", null).header("Origin", origin)
                .header("Access-Control-Request-Headers", "range").build()
        ).execute()
        options("https://www.gstatic.com").use {
            assertEquals(204, it.code)
            assertEquals("Range", it.header("Access-Control-Allow-Headers"))
        }
        options("https://evil.example").use { assertEquals(403, it.code) }
    }

    @Test
    fun refusedRouteIsNeverFetched() {
        // pure Piped mode without a proxy, or a local address in a playlist
        val master = server(route = { null }).publish(originUrl)
        assertEquals(403, get(master).use { it.code })
    }

    @Test
    fun receiverOriginsAndPublicHosts() {
        assertTrue(CastMediaServer.isReceiverOrigin("https://www.gstatic.com"))
        assertTrue(!CastMediaServer.isReceiverOrigin("http://www.gstatic.com"))
        assertTrue(!CastMediaServer.isReceiverOrigin("https://gstatic.com.evil.example"))
        assertTrue(CastMediaServer.isPublicHost("rr1---sn-abc.googlevideo.com"))
        listOf("127.0.0.1", "192.168.1.1", "10.0.0.2", "172.16.5.4", "169.254.1.1", "[::1]", "[fd00::1]", "localhost", "tv.local")
            .forEach { assertTrue(it, !CastMediaServer.isPublicHost(it)) }
        assertTrue(CastMediaServer.isPublicHost("8.8.8.8"))
    }

    @Test
    fun otherClientsGetNoAnswer() {
        val master = server(peer = InetAddress.getByName("10.9.8.7")).publish(originUrl)
        assertThrows(IOException::class.java) { get(master).close() }
    }

    @Test
    fun rewriterResolvesRelativeAndAttributeUris() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"a/x.m3u8\"\n/abs/v.m3u8\nhttps://cdn.example/s.ts\n"
        val out = HlsRewriter.rewrite(playlist, "https://h.example/dir/master.m3u8") { uri, _ -> "R<$uri>" }
        assertEquals(
            "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"R<https://h.example/dir/a/x.m3u8>\"\n" +
                "R<https://h.example/abs/v.m3u8>\nR<https://cdn.example/s.ts>\n",
            out
        )
    }
}
