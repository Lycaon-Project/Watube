package com.watube.yard.cast

import java.io.ByteArrayOutputStream

// region Stolm
/**
 * Rewraps an HLS packed audio segment (ID3 timestamp tag, then AAC ADTS frames) into MPEG-TS,
 * without touching the audio. YouTube's HLS streams carry their audio this way next to MPEG-TS
 * video; Cast receivers stall on that mix (seen with a Chromecast: video alone plays, video with
 * the packed audio never starts) but play MPEG-TS audio, timed by the same clock as the video.
 *
 * The first frame takes the timestamp of the ID3 PRIV frame
 * `com.apple.streaming.transportStreamTimestamp` (RFC 8216, 3.4), so audio and video stay in
 * sync. Pure, bounded parsing of untrusted bytes: anything unexpected returns null and the
 * segment is served as it came.
 */
internal object PackedAudioTs {
    private const val PACKET = 188
    private const val HEADER = 4
    private const val PAT_PID = 0x0000
    private const val PMT_PID = 0x1000
    private const val AUDIO_PID = 0x0101
    private const val STREAM_TYPE_ADTS = 0x0F
    private const val AUDIO_STREAM_ID = 0xC0
    private const val CLOCK = 90_000L
    private const val SAMPLES_PER_FRAME = 1024L
    private const val PTS_MASK = (1L shl 33) - 1
    /** The PCR runs a little ahead of the audio, as a multiplexer does. */
    private const val PCR_LEAD = 9_000L
    /** Frames per PES packet: fewer packets and less stuffing than one frame each. */
    private const val FRAMES_PER_PES = 8
    private const val TIMESTAMP_OWNER = "com.apple.streaming.transportStreamTimestamp"
    private val SAMPLE_RATES = intArrayOf(
        96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350
    )

    private class Frame(val offset: Int, val length: Int)

    /** Whether [head] starts like packed audio: an ID3 tag or an ADTS frame. */
    fun isPackedAudio(head: ByteArray): Boolean = head.size >= 3 &&
        ((head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) ||
            isAdtsSync(head, 0))

    /** The segment as MPEG-TS, or null when it is not packed AAC with a timestamp. */
    fun toTs(data: ByteArray): ByteArray? {
        var position = 0
        var basePts: Long? = null
        while (position + 10 <= data.size && data[position] == 'I'.code.toByte() &&
            data[position + 1] == 'D'.code.toByte() && data[position + 2] == '3'.code.toByte()
        ) {
            val tagSize = syncSafe(data, position + 6) ?: return null
            val footer = if (data[position + 5].toInt() and 0x10 != 0) 10 else 0
            val end = position + 10 + tagSize + footer
            if (end > data.size) return null
            id3Timestamp(data, position, end)?.let { basePts = it }
            position = end
        }
        val pts = basePts ?: return null

        val frames = mutableListOf<Frame>()
        var sampleRate = 0
        while (position + 7 <= data.size && isAdtsSync(data, position)) {
            val rate = SAMPLE_RATES.getOrNull(data[position + 2].toInt() shr 2 and 0x0F) ?: return null
            if (sampleRate == 0) sampleRate = rate else if (rate != sampleRate) return null
            val length = (data[position + 3].toInt() and 0x03 shl 11) or
                (data[position + 4].toInt() and 0xFF shl 3) or
                (data[position + 5].toInt() and 0xE0 shr 5)
            if (length < 7 || position + length > data.size) return null
            frames += Frame(position, length)
            position += length
        }
        if (frames.isEmpty() || position != data.size) return null

        val out = ByteArrayOutputStream(data.size + data.size / 4 + 2 * PACKET)
        val counters = IntArray(3)
        writeSection(out, PAT_PID, counters, 0, pat())
        writeSection(out, PMT_PID, counters, 1, pmt())
        for (start in frames.indices step FRAMES_PER_PES) {
            val group = frames.subList(start, minOf(start + FRAMES_PER_PES, frames.size))
            val framePts = (pts + start * SAMPLES_PER_FRAME * CLOCK / sampleRate) and PTS_MASK
            val payload = ByteArrayOutputStream()
            group.forEach { payload.write(data, it.offset, it.length) }
            writePes(out, counters, pes(payload.toByteArray(), framePts), (framePts - PCR_LEAD).coerceAtLeast(0))
        }
        return out.toByteArray()
    }

    private fun isAdtsSync(data: ByteArray, at: Int) =
        at + 1 < data.size && data[at] == 0xFF.toByte() && data[at + 1].toInt() and 0xF6 == 0xF0

    private fun syncSafe(data: ByteArray, at: Int): Int? {
        var value = 0
        for (i in 0 until 4) {
            val byte = data[at + i].toInt() and 0xFF
            if (byte and 0x80 != 0) return null
            value = value shl 7 or byte
        }
        return value
    }

    /** The 33-bit timestamp of the ID3 tag in [start, end), from its PRIV frame. */
    private fun id3Timestamp(data: ByteArray, start: Int, end: Int): Long? {
        val major = data[start + 3].toInt()
        var position = start + 10
        // an extended header comes before the frames
        if (data[start + 5].toInt() and 0x40 != 0) {
            if (position + 4 > end) return null
            position += if (major >= 4) syncSafe(data, position) ?: return null else
                ((data[position].toInt() and 0xFF shl 24) or (data[position + 1].toInt() and 0xFF shl 16) or
                    (data[position + 2].toInt() and 0xFF shl 8) or (data[position + 3].toInt() and 0xFF)) + 4
        }
        while (position + 10 <= end) {
            val id = String(data, position, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val size = if (major >= 4) syncSafe(data, position + 4) ?: return null else
                (data[position + 4].toInt() and 0xFF shl 24) or (data[position + 5].toInt() and 0xFF shl 16) or
                    (data[position + 6].toInt() and 0xFF shl 8) or (data[position + 7].toInt() and 0xFF)
            val body = position + 10
            if (size < 0 || body + size > end) return null
            if (id == "PRIV") {
                val ownerEnd = (body until body + size).firstOrNull { data[it] == 0.toByte() }
                if (ownerEnd != null && body + size - (ownerEnd + 1) == 8 &&
                    String(data, body, ownerEnd - body, Charsets.ISO_8859_1) == TIMESTAMP_OWNER
                ) {
                    var value = 0L
                    for (i in 0 until 8) value = value shl 8 or (data[ownerEnd + 1 + i].toLong() and 0xFF)
                    return value and PTS_MASK
                }
            }
            position = body + size
        }
        return null
    }

    private fun pat(): ByteArray = section(
        tableId = 0x00, tableIdExtension = 1,
        body = byteArrayOf(0x00, 0x01, (0xE0 or (PMT_PID shr 8)).toByte(), (PMT_PID and 0xFF).toByte())
    )

    private fun pmt(): ByteArray = section(
        tableId = 0x02, tableIdExtension = 1,
        body = byteArrayOf(
            (0xE0 or (AUDIO_PID shr 8)).toByte(), (AUDIO_PID and 0xFF).toByte(), // PCR PID
            0xF0.toByte(), 0x00, // no program descriptors
            STREAM_TYPE_ADTS.toByte(),
            (0xE0 or (AUDIO_PID shr 8)).toByte(), (AUDIO_PID and 0xFF).toByte(),
            0xF0.toByte(), 0x00 // no stream descriptors
        )
    )

    /** A PSI section with its CRC: version 0, current, one section. */
    private fun section(tableId: Int, tableIdExtension: Int, body: ByteArray): ByteArray {
        val length = 5 + body.size + 4
        val out = ByteArrayOutputStream()
        out.write(tableId)
        out.write(0xB0 or (length shr 8 and 0x0F))
        out.write(length and 0xFF)
        out.write(tableIdExtension shr 8)
        out.write(tableIdExtension and 0xFF)
        out.write(0xC1) // version 0, current_next_indicator
        out.write(0x00)
        out.write(0x00)
        out.write(body)
        val crc = crc32(out.toByteArray())
        for (shift in intArrayOf(24, 16, 8, 0)) out.write((crc ushr shift).toInt() and 0xFF)
        return out.toByteArray()
    }

    /** MPEG-2 CRC-32: polynomial 0x04C11DB7, not reflected, initial value 0xFFFFFFFF. */
    internal fun crc32(data: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (byte in data) {
            crc = crc xor ((byte.toLong() and 0xFF) shl 24)
            repeat(8) {
                crc = if (crc and 0x80000000L != 0L) (crc shl 1) xor 0x04C11DB7L else crc shl 1
                crc = crc and 0xFFFFFFFFL
            }
        }
        return crc
    }

    private fun pes(payload: ByteArray, pts: Long): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 14)
        out.write(0x00)
        out.write(0x00)
        out.write(0x01)
        out.write(AUDIO_STREAM_ID)
        val length = 3 + 5 + payload.size
        val field = if (length > 0xFFFF) 0 else length
        out.write(field shr 8)
        out.write(field and 0xFF)
        out.write(0x80) // marker bits
        out.write(0x80) // PTS only
        out.write(5)
        out.write((0x21 or (pts shr 29 and 0x0E).toInt()))
        out.write((pts shr 22 and 0xFF).toInt())
        out.write((pts shr 14 and 0xFE).toInt() or 1)
        out.write((pts shr 7 and 0xFF).toInt())
        out.write((pts shl 1 and 0xFE).toInt() or 1)
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeSection(out: ByteArrayOutputStream, pid: Int, counters: IntArray, slot: Int, section: ByteArray) {
        val packet = ByteArray(PACKET) { 0xFF.toByte() }
        header(packet, pid, payloadStart = true, adaptation = false, counter = counters[slot]++ and 0x0F)
        packet[HEADER] = 0 // pointer field
        section.copyInto(packet, HEADER + 1)
        out.write(packet)
    }

    /** Splits [pes] into packets; the first one carries the PCR, the last one is padded. */
    private fun writePes(out: ByteArrayOutputStream, counters: IntArray, pes: ByteArray, pcr: Long) {
        var offset = 0
        var first = true
        while (offset < pes.size) {
            val packet = ByteArray(PACKET)
            // adaptation field: length byte + flags (+ 6 PCR bytes on the first packet)
            val minimumAdaptation = if (first) 8 else 0
            val room = PACKET - HEADER - minimumAdaptation
            val chunk = minOf(room, pes.size - offset)
            val adaptationSize = PACKET - HEADER - chunk
            header(packet, AUDIO_PID, payloadStart = first, adaptation = adaptationSize > 0, counter = counters[2]++ and 0x0F)
            if (adaptationSize > 0) {
                packet[HEADER] = (adaptationSize - 1).toByte()
                if (adaptationSize > 1) {
                    packet[HEADER + 1] = if (first) 0x50 else 0x00 // random access + PCR on the first packet
                    var at = HEADER + 2
                    if (first) {
                        packet[at++] = (pcr shr 25 and 0xFF).toByte()
                        packet[at++] = (pcr shr 17 and 0xFF).toByte()
                        packet[at++] = (pcr shr 9 and 0xFF).toByte()
                        packet[at++] = (pcr shr 1 and 0xFF).toByte()
                        packet[at++] = ((pcr and 1).toInt() shl 7 or 0x7E).toByte()
                        packet[at++] = 0x00
                    }
                    while (at < HEADER + adaptationSize) packet[at++] = 0xFF.toByte()
                }
            }
            pes.copyInto(packet, HEADER + adaptationSize, offset, offset + chunk)
            out.write(packet)
            offset += chunk
            first = false
        }
    }

    private fun header(packet: ByteArray, pid: Int, payloadStart: Boolean, adaptation: Boolean, counter: Int) {
        packet[0] = 0x47
        packet[1] = ((if (payloadStart) 0x40 else 0) or (pid shr 8 and 0x1F)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = ((if (adaptation) 0x30 else 0x10) or counter).toByte()
    }
}
// endregion
