package com.watube.yard.api

import android.os.SystemClock
import com.watube.yard.api.obj.Streams

// region Downloads
/**
 * The streams of the last few videos, kept a few minutes in memory only, so that one extraction
 * serves the player, the download sheet and the download service: the sheet opened on the video
 * playing shows its qualities at once, and the download then starts without extracting again.
 * Nothing is written to disk, and the stream URLs stay valid for hours, far beyond [TTL_MS].
 */
object StreamsCache {
    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_ENTRIES = 4

    private class Entry(val streams: Streams, val storedAt: Long)

    /** Monotonic time in ms; replaced in the unit tests. */
    internal var clock: () -> Long = SystemClock::elapsedRealtime

    // access order: the video used last is dropped last
    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun put(videoId: String, streams: Streams) {
        entries[videoId] = Entry(streams, clock())
    }

    /** The streams kept for [videoId], or null when there are none or they are too old. */
    @Synchronized
    fun get(videoId: String): Streams? {
        val entry = entries[videoId] ?: return null
        if (clock() - entry.storedAt > TTL_MS) {
            entries.remove(videoId)
            return null
        }
        return entry.streams
    }

    /** The streams of [videoId] for a download: kept ones, else extracted once and kept. */
    suspend fun forDownload(videoId: String): Streams = get(videoId)
        ?: MediaServiceRepository.instance.getStreamsForDownload(videoId).also { put(videoId, it) }

    @Synchronized
    internal fun clear() = entries.clear()
}
// endregion
