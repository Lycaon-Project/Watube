package com.watube.yard

import com.watube.yard.api.obj.PipedStream
import com.watube.yard.helpers.DownloadChoices
import com.watube.yard.helpers.DownloadChoices.Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadChoicesTest {

    private fun video(
        quality: String,
        codec: String,
        format: String = "WEBM",
        fps: Int = 30,
        size: Long = 1_000,
        url: String? = "https://example.invalid/v"
    ) = PipedStream(
        url = url, quality = quality, codec = codec, format = format, fps = fps,
        height = quality.takeWhile { it.isDigit() }.toIntOrNull(), contentLength = size
    )

    private fun audio(
        quality: String,
        format: String = "WEBMA_OPUS",
        locale: String? = null,
        type: String? = null,
        size: Long = 100
    ) = PipedStream(
        url = "https://example.invalid/a", quality = quality, format = format,
        audioTrackLocale = locale, audioTrackType = type, contentLength = size
    )

    @Test
    fun `one card per quality, best first, sixty frames above thirty`() {
        val streams = listOf(
            video("720p", "avc1.4d401f"),
            video("1080p60", "vp09.00", fps = 60),
            video("1080p", "avc1.640028"),
            video("1080p", "vp09.00")
        )

        val choices = DownloadChoices.videoChoices(streams, codec = null)

        assertEquals(listOf("1080p60", "1080p", "720p"), choices.map { it.quality })
    }

    @Test
    fun `auto prefers the most compatible codec, a chosen codec wins where it exists`() {
        val streams = listOf(video("1080p", "vp09.00"), video("1080p", "avc1.640028"))

        assertEquals("avc1.640028", DownloadChoices.videoChoices(streams, null).single().codec)
        assertEquals("vp09.00", DownloadChoices.videoChoices(streams, Codec.VP9).single().codec)
    }

    @Test
    fun `auto keeps the format chosen last time`() {
        val streams = listOf(
            video("720p", "avc1.4d401f", format = "MPEG_4"),
            video("720p", "vp09.00", format = "WEBM")
        )

        val choice = DownloadChoices.videoChoices(streams, null, preferredFormat = "WEBM").single()

        assertEquals("WEBM", choice.format)
    }

    @Test
    fun `streams without url and HLS streams are not offered`() {
        val streams = listOf(
            video("720p", "avc1", url = null),
            video("480p", "avc1", format = "HLS"),
            video("360p", "avc1")
        )

        assertEquals(listOf("360p"), DownloadChoices.videoChoices(streams, null).map { it.quality })
    }

    @Test
    fun `codecs are listed in compatibility order`() {
        val streams = listOf(video("1080p", "av01.0"), video("720p", "vp9"), video("360p", "avc1"))

        assertEquals(listOf(Codec.H264, Codec.VP9, Codec.AV1), DownloadChoices.codecsOf(streams))
    }

    @Test
    fun `the original audio track comes first`() {
        val streams = listOf(
            audio("128kbps", locale = "fr", type = "DUBBED"),
            audio("128kbps", locale = "en", type = "ORIGINAL")
        )

        assertEquals(listOf("en", "fr"), DownloadChoices.audioTracks(streams))
    }

    @Test
    fun `audio choices keep one track, highest bitrate first`() {
        val streams = listOf(
            audio("48kbps", locale = "en"),
            audio("160kbps", locale = "en"),
            audio("160kbps", locale = "en"),
            audio("128kbps", locale = "fr")
        )

        val choices = DownloadChoices.audioChoices(streams, "en")

        assertEquals(listOf("160kbps"), choices.map { it.quality })
    }

    @Test
    fun `audio choices offer only the high qualities, all of them when none is high`() {
        val streams = listOf(
            audio("50kbps"),
            audio("128kbps", "M4A"),
            audio("70kbps"),
            audio("48kbps", "M4A"),
            audio("160kbps")
        )
        val lowOnly = listOf(audio("70kbps"), audio("48kbps", "M4A"))

        assertEquals(listOf("160kbps", "128kbps"), DownloadChoices.audioChoices(streams, null).map { it.quality })
        assertEquals(listOf("70kbps", "48kbps"), DownloadChoices.audioChoices(lowOnly, null).map { it.quality })
    }

    @Test
    fun `best audio follows the preferred format, else the highest bitrate`() {
        val streams = listOf(audio("160kbps", "WEBMA_OPUS"), audio("128kbps", "M4A"))

        assertEquals("M4A", DownloadChoices.bestAudio(streams, null, "M4A")?.format)
        assertEquals("WEBMA_OPUS", DownloadChoices.bestAudio(streams, null)?.format)
    }

    @Test
    fun `total size adds video and audio, unknown when one size is missing`() {
        val known = video("720p", "avc1", size = 5_000)

        assertEquals(5_100L, DownloadChoices.totalSize(known, audio("128kbps", size = 100)))
        assertNull(DownloadChoices.totalSize(known, audio("128kbps", size = -1)))
        assertNull(DownloadChoices.totalSize(null, null))
    }
}
