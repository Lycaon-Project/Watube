package com.watube.yard.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** HLS packed audio (ID3 timestamp + ADTS frames) rewrapped into MPEG-TS for Cast receivers. */
class PackedAudioTsTest {
    private val timestamp = 900_000L + 123 // 10 s and a bit, 90 kHz
    private val frames = (0 until 10).map { adtsFrame(40 + it * 3, it) }
    private val segment = id3(timestamp) + frames.reduce { a, b -> a + b }

    @Test
    fun recognizesPackedAudio() {
        assertTrue(PackedAudioTs.isPackedAudio(segment))
        assertTrue(PackedAudioTs.isPackedAudio(frames[0]))
        assertTrue(!PackedAudioTs.isPackedAudio(byteArrayOf(0x47, 0x40, 0x00)))
    }

    @Test
    fun rewrapsTheFramesWithTheirTimestamps() {
        val ts = PackedAudioTs.toTs(segment)!!
        assertEquals(0, ts.size % 188)
        val packets = ts.toList().chunked(188).map { it.toByteArray() }
        packets.forEach { assertEquals(0x47.toByte(), it[0]) }

        // PAT then PMT, both with a valid CRC, the PMT declaring one ADTS stream
        assertEquals(0, pid(packets[0]))
        assertEquals(0x1000, pid(packets[1]))
        assertValidSection(packets[0])
        val pmt = assertValidSection(packets[1])
        assertEquals(0x0F, pmt[12].toInt() and 0xFF)

        // the audio, reassembled from its PES packets, is the input unchanged
        val audio = packets.drop(2)
        audio.forEach { assertEquals(0x0101, pid(it)) }
        val pesList = mutableListOf<ByteArrayOutputStream>()
        audio.forEach { packet ->
            val start = packet[1].toInt() and 0x40 != 0
            val hasAdaptation = packet[3].toInt() and 0x20 != 0
            val payloadAt = if (hasAdaptation) 5 + (packet[4].toInt() and 0xFF) else 4
            if (start) pesList += ByteArrayOutputStream()
            pesList.last().write(packet, payloadAt, 188 - payloadAt)
        }
        val pes = pesList.map { it.toByteArray() }
        val payload = ByteArrayOutputStream()
        pes.forEach { payload.write(it, 14, it.size - 14) }
        assertArrayEquals(frames.reduce { a, b -> a + b }, payload.toByteArray())

        // 8 frames per PES: the second one starts 8 frames of 1024 samples later
        assertEquals(timestamp, pts(pes[0]))
        assertEquals(timestamp + 8 * 1024 * 90_000L / 44_100, pts(pes[1]))
    }

    @Test
    fun leavesAnythingElseAlone() {
        // no timestamp: the audio could not be put in sync with the video
        assertNull(PackedAudioTs.toTs(frames.reduce { a, b -> a + b }))
        // truncated frame, stray bytes
        assertNull(PackedAudioTs.toTs(segment.copyOf(segment.size - 5)))
        assertNull(PackedAudioTs.toTs(segment + byteArrayOf(1, 2, 3)))
        assertNull(PackedAudioTs.toTs(ByteArray(0)))
    }

    private fun pid(packet: ByteArray) = (packet[1].toInt() and 0x1F shl 8) or (packet[2].toInt() and 0xFF)

    /** The section of a PSI packet, checked against its CRC. */
    private fun assertValidSection(packet: ByteArray): ByteArray {
        val start = 5 // header + pointer field
        val length = (packet[start + 1].toInt() and 0x0F shl 8) or (packet[start + 2].toInt() and 0xFF)
        val section = packet.copyOfRange(start, start + 3 + length)
        val crc = PackedAudioTs.crc32(section.copyOf(section.size - 4))
        val stored = section.takeLast(4).fold(0L) { acc, b -> acc shl 8 or (b.toLong() and 0xFF) }
        assertEquals(crc, stored)
        return section
    }

    private fun pts(pes: ByteArray): Long =
        (pes[9].toLong() and 0x0E shl 29) or (pes[10].toLong() and 0xFF shl 22) or
            (pes[11].toLong() and 0xFE shl 14) or (pes[12].toLong() and 0xFF shl 7) or (pes[13].toLong() and 0xFE shr 1)

    /** An ADTS frame (AAC LC, 44.1 kHz, stereo) carrying [payloadSize] bytes of [seed]. */
    private fun adtsFrame(payloadSize: Int, seed: Int): ByteArray {
        val length = 7 + payloadSize
        val header = byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),
            (1 shl 6 or (4 shl 2) or 0).toByte(), // LC, 44.1 kHz
            (2 shl 6 or (length shr 11 and 0x03)).toByte(),
            (length shr 3 and 0xFF).toByte(),
            (length and 0x07 shl 5 or 0x1F).toByte(),
            0xFC.toByte()
        )
        return header + ByteArray(payloadSize) { (it * 31 + seed).toByte() }
    }

    /** An ID3v2.4 tag holding the HLS transport stream timestamp. */
    private fun id3(timestamp: Long): ByteArray {
        val owner = "com.apple.streaming.transportStreamTimestamp".toByteArray(Charsets.ISO_8859_1)
        val body = owner + 0.toByte() + ByteArray(8) { (timestamp shr (56 - it * 8) and 0xFF).toByte() }
        val frame = "PRIV".toByteArray() + syncSafe(body.size) + byteArrayOf(0, 0) + body
        return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size) + frame
    }

    private fun syncSafe(value: Int) = ByteArray(4) { (value shr (21 - it * 7) and 0x7F).toByte() }
}
