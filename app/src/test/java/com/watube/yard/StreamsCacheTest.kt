package com.watube.yard

import com.watube.yard.api.StreamsCache
import com.watube.yard.api.obj.Streams
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class StreamsCacheTest {
    private var now = 0L

    private fun streams(title: String) = Streams(
        title = title, description = "", uploader = "", uploaderUrl = null,
        thumbnailUrl = "", category = "", uploaderVerified = false, duration = 60
    )

    @Before
    fun setUp() {
        now = 0L
        StreamsCache.clock = { now }
        StreamsCache.clear()
    }

    @After
    fun tearDown() = StreamsCache.clear()

    @Test
    fun `kept streams come back for the same video only`() {
        val kept = streams("a")
        StreamsCache.put("a", kept)

        assertSame(kept, StreamsCache.get("a"))
        assertNull(StreamsCache.get("b"))
    }

    @Test
    fun `streams older than five minutes are dropped`() {
        StreamsCache.put("a", streams("a"))

        now = 5 * 60 * 1000L
        assertNotNull(StreamsCache.get("a"))
        now += 1
        assertNull(StreamsCache.get("a"))
    }

    @Test
    fun `a fifth video drops the one used least recently`() {
        listOf("a", "b", "c", "d").forEach { StreamsCache.put(it, streams(it)) }
        // "a" used again: "b" is now the oldest
        StreamsCache.get("a")

        StreamsCache.put("e", streams("e"))

        assertNotNull(StreamsCache.get("a"))
        assertNull(StreamsCache.get("b"))
        assertNotNull(StreamsCache.get("e"))
    }
}
