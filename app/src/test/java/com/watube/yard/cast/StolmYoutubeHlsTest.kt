package com.watube.yard.cast

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The relay with a stream shaped like YouTube's HLS (as seen with a real Chromecast): H.264
 * variants in MPEG-TS next to VP9 ones, audio apart in packed AAC, and segment URLs holding
 * "playlist_type/DVR".
 */
class StolmYoutubeHlsTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val client = OkHttpClient()
    private val videoSegment = ByteArray(188 * 4) { if (it % 188 == 0) 0x47 else (it * 13).toByte() }
    private val audioSegment = id3(900_000L) + adts(64) + adts(80)

    private val origin = ServerSocket(0, 8, loopback).also { server ->
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    val path = it.getInputStream().bufferedReader().readLine().split(' ')[1]
                    val playlist = "application/vnd.apple.mpegurl"
                    val (type, body) = when (path) {
                        "/yt/master.m3u8" -> playlist to """
                            #EXTM3U
                            #EXT-X-INDEPENDENT-SEGMENTS
                            #EXT-X-MEDIA:URI="a/index.m3u8",TYPE=AUDIO,GROUP-ID="234",NAME="Default",DEFAULT=YES
                            #EXT-X-STREAM-INF:BANDWIDTH=500,CODECS="vp09.00.21.08,mp4a.40.2",AUDIO="234"
                            vp9/index.m3u8
                            #EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS="avc1.4D401E,mp4a.40.2",AUDIO="234"
                            v/index.m3u8
                        """.trimIndent().toByteArray()
                        "/yt/v/index.m3u8" -> playlist to
                            "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:5,\n/vp/playlist_type/DVR/sq/1/file/seg.ts\n#EXT-X-ENDLIST\n".toByteArray()
                        "/yt/a/index.m3u8" -> playlist to
                            "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:5,\n/ap/playlist_type/DVR/sq/1/file/seg.ts\n#EXT-X-ENDLIST\n".toByteArray()
                        "/vp/playlist_type/DVR/sq/1/file/seg.ts" -> "application/octet-stream" to videoSegment
                        "/ap/playlist_type/DVR/sq/1/file/seg.ts" -> "application/octet-stream" to audioSegment
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
    private val masterUrl = "http://127.0.0.1:${origin.localPort}/yt/master.m3u8"
    private val relay = CastMediaServer(loopback, loopback, client) { it }

    private fun get(url: String) = client.newCall(Request.Builder().url(url).build()).execute()

    @After
    fun tearDown() {
        relay.close()
        origin.close()
    }

    @Test
    fun receiverGetsMpegTsVideoAndAudio() {
        val published = relay.publish(masterUrl)
        // what the receiver is told: audio rewrapped into MPEG-TS, video in MPEG-TS
        assertEquals(HlsFormats(HlsFormats.TS, HlsFormats.MPEG2_TS), relay.segmentFormats(masterUrl))

        val master = get(published).use { it.body.string() }
        // the VP9 variant is gone: a receiver cannot switch from MPEG-TS to fragmented MP4
        assertFalse(master.contains("vp09"))
        assertTrue(master.contains("avc1.4D401E"))
        val relayed = master.lines().filter { it.startsWith("http://127.0.0.1") }
        val audioPlaylist = Regex("URI=\"([^\"]*)\"").find(master)!!.groupValues[1]

        // a segment URL holding "playlist_type" is relayed as it is, not read as a playlist
        val video = get(relayed.single()).use { it.body.string() }
        val videoSegmentUrl = video.lines().first { it.startsWith("http://127.0.0.1") }
        assertArrayEquals(videoSegment, get(videoSegmentUrl).use { it.body.bytes() })

        // the packed audio comes out as MPEG-TS
        val audio = get(audioPlaylist).use { it.body.string() }
        val audioSegmentUrl = audio.lines().first { it.startsWith("http://127.0.0.1") }
        get(audioSegmentUrl).use { response ->
            assertEquals("video/mp2t", response.header("Content-Type"))
            val ts = response.body.bytes()
            assertEquals(0, ts.size % 188)
            assertEquals(0x47.toByte(), ts[0])
        }
    }

    @Test
    fun streamWithoutH264IsLeftWhole() {
        val vp9Only = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,CODECS=\"vp09.00.21.08,mp4a.40.2\"\nv.m3u8\n"
        assertEquals(vp9Only, HlsVariants.castable(vp9Only))
        val media = "#EXTM3U\n#EXTINF:5,\nseg.ts\n"
        assertEquals(media, HlsVariants.castable(media))
    }

    @Test
    fun mdnsAnswerGivesTheReceiverIpv4() {
        val name = "Chromecast-abc._googlecast._tcp.local"
        val query = CastMdns.query(0x1234, name)!!
        // header, then the name label by label, SRV, IN
        assertEquals(0x12, query[0].toInt())
        assertEquals("Chromecast-abc", String(query, 13, 14))

        val answer = response(0x1234, name, target = "abc.local", ipv4 = byteArrayOf(192.toByte(), 168.toByte(), 1, 20))
        assertEquals(InetAddress.getByName("192.168.1.20") as Inet4Address, CastMdns.answerIn(answer, answer.size, 0x1234, name)?.address)
        // another query's answer, another receiver's record: not ours
        assertNull(CastMdns.answerIn(answer, answer.size, 0x4321, name))
        assertNull(CastMdns.answerIn(answer, answer.size, 0x1234, "Other._googlecast._tcp.local"))
        // a name pointing at itself ends in an error, not in a loop
        val loop = answer.copyOf().also { it[12] = 0xC0.toByte(); it[13] = 12 }
        assertThrows(IllegalArgumentException::class.java) { CastMdns.answerIn(loop, loop.size, 0x1234, name) }
        assertThrows(IllegalArgumentException::class.java) { CastMdns.answerIn(answer, 20, 0x1234, name) }
    }

    /** A response with the SRV record of [name] and, in the additional section, [target]'s A record. */
    private fun response(id: Int, name: String, target: String, ipv4: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(value: Int) = out.write(byteArrayOf((value shr 8).toByte(), value.toByte()))
        fun name(value: String) {
            value.split('.').forEach { label -> out.write(label.length); out.write(label.toByteArray()) }
            out.write(0)
        }
        u16(id); u16(0x8400); u16(0); u16(1); u16(0); u16(1)
        name(name); u16(33); u16(0x8001); u16(0); u16(120)
        val targetBytes = ByteArrayOutputStream().apply {
            target.split('.').forEach { write(it.length); write(it.toByteArray()) }
            write(0)
        }.toByteArray()
        u16(6 + targetBytes.size); u16(0); u16(0); u16(8009)
        val targetOffset = out.size()
        out.write(targetBytes)
        // the A record's owner points back at the SRV target (name compression)
        out.write(byteArrayOf((0xC0 or (targetOffset shr 8)).toByte(), targetOffset.toByte()))
        u16(1); u16(0x8001); u16(0); u16(120); u16(4)
        out.write(ipv4)
        return out.toByteArray()
    }

    private fun adts(payloadSize: Int): ByteArray {
        val length = 7 + payloadSize
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(), (1 shl 6 or (4 shl 2)).toByte(),
            (2 shl 6 or (length shr 11 and 0x03)).toByte(), (length shr 3 and 0xFF).toByte(),
            (length and 0x07 shl 5 or 0x1F).toByte(), 0xFC.toByte()
        ) + ByteArray(payloadSize) { it.toByte() }
    }

    private fun id3(timestamp: Long): ByteArray {
        val owner = "com.apple.streaming.transportStreamTimestamp".toByteArray(Charsets.ISO_8859_1)
        val body = owner + 0.toByte() + ByteArray(8) { (timestamp shr (56 - it * 8) and 0xFF).toByte() }
        fun syncSafe(value: Int) = ByteArray(4) { (value shr (21 - it * 7) and 0x7F).toByte() }
        val frame = "PRIV".toByteArray() + syncSafe(body.size) + byteArrayOf(0, 0) + body
        return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size) + frame
    }
}
