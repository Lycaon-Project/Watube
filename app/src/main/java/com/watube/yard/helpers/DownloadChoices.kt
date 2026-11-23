package com.watube.yard.helpers

import com.watube.yard.api.obj.PipedStream
import com.watube.yard.extensions.getWhileDigit

// region Downloads
/**
 * What the download sheet offers for one video, out of its raw streams: one card per video
 * quality (in the chosen codec), one per audio quality of the chosen track. Pure functions,
 * kept apart from the sheet so they can be tested on their own.
 */
object DownloadChoices {
    /** The video codecs YouTube serves, in the order Auto prefers them: the most compatible first. */
    enum class Codec(val label: String, private vararg val prefixes: String) {
        H264("H.264", "avc"),
        VP9("VP9", "vp9", "vp09"),
        AV1("AV1", "av01");

        fun matches(codec: String?) = codec != null && prefixes.any { codec.startsWith(it, true) }

        companion object {
            fun of(codec: String?): Codec? = entries.firstOrNull { it.matches(codec) }
        }
    }

    // the original track first, then an untyped one, then the dubbed and described ones
    private val TRACK_TYPE_ORDER = listOf("ORIGINAL", "", "DUBBED", "DESCRIPTIVE", "SECONDARY")

    // the lowest bitrate offered while a higher one exists: AAC 128 stays, opus 70 goes
    private const val MIN_AUDIO_KBPS = 100

    /** The codecs [videoStreams] offer, in [Codec] order. */
    fun codecsOf(videoStreams: List<PipedStream>): List<Codec> =
        Codec.entries.filter { codec -> playableVideo(videoStreams).any { codec.matches(it.codec) } }

    /**
     * One stream per quality (1080p60, 1080p, 720p...), best first. With a [codec], the stream in
     * that codec where the quality has one; otherwise, or with Auto (null), the one whose format
     * was chosen last time ([preferredFormat]), else the first codec in [Codec] order.
     */
    fun videoChoices(
        videoStreams: List<PipedStream>,
        codec: Codec?,
        preferredFormat: String? = null
    ): List<PipedStream> = playableVideo(videoStreams)
        .groupBy { it.quality.orEmpty() }
        .map { (_, sameQuality) ->
            sameQuality.firstOrNull { codec != null && codec.matches(it.codec) }
                ?: sameQuality.firstOrNull { preferredFormat != null && it.format == preferredFormat }
                ?: sameQuality.minBy { Codec.of(it.codec)?.ordinal ?: Codec.entries.size }
        }
        .sortedWith(
            compareByDescending<PipedStream> { it.quality.getWhileDigit() ?: 0 }
                .thenByDescending { it.fps ?: 0 }
        )

    /** The audio tracks (their locale, null for the only or original one), main track first. */
    fun audioTracks(audioStreams: List<PipedStream>): List<String?> = playable(audioStreams)
        .sortedBy { trackRank(it.audioTrackType) }
        .map { it.audioTrackLocale }
        .distinct()

    /**
     * The audio streams of [track], one per quality and format, the highest bitrate first. Only
     * the high qualities are offered (opus 160 and AAC 128 kbit/s on YouTube; opus 70 and 50,
     * AAC 48 are left out), unless the track has none of them: then every quality is kept.
     */
    fun audioChoices(audioStreams: List<PipedStream>, track: String?): List<PipedStream> {
        val all = playable(audioStreams)
            .filter { it.audioTrackLocale == track }
            .distinctBy { it.quality to it.format }
            .sortedByDescending { it.quality.getWhileDigit() ?: 0 }
        return all.filter { (it.quality.getWhileDigit() ?: 0) >= MIN_AUDIO_KBPS }.ifEmpty { all }
    }

    /** The audio added to a video: [preferredFormat] if [track] has it, else its best quality. */
    fun bestAudio(
        audioStreams: List<PipedStream>,
        track: String?,
        preferredFormat: String? = null
    ): PipedStream? {
        val choices = audioChoices(audioStreams, track)
        return choices.firstOrNull { preferredFormat != null && it.format == preferredFormat }
            ?: choices.firstOrNull()
    }

    /** Bytes of a video and its audio together; null when either size is unknown. */
    fun totalSize(video: PipedStream?, audio: PipedStream?): Long? {
        val parts = listOfNotNull(video, audio)
        if (parts.isEmpty() || parts.any { it.contentLength <= 0 }) return null
        return parts.sumOf { it.contentLength }
    }

    private fun trackRank(type: String?): Int =
        TRACK_TYPE_ORDER.indexOf(type.orEmpty().uppercase()).takeIf { it >= 0 }
            ?: TRACK_TYPE_ORDER.size

    private fun playable(streams: List<PipedStream>) = streams.filter { !it.url.isNullOrEmpty() }

    private fun playableVideo(streams: List<PipedStream>) =
        playable(streams).filter { !it.format.orEmpty().contains("HLS") }
}
// endregion
