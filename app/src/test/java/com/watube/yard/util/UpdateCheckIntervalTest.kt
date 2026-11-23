package com.watube.yard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckIntervalTest {

    @Test
    fun `every launch has no delay`() {
        assertEquals(0L, UpdateCheckInterval.millis(UpdateCheckInterval.EVERY_LAUNCH))
    }

    @Test
    fun `offered choices convert hours to milliseconds`() {
        val hour = 3_600_000L
        assertEquals(168 * hour, UpdateCheckInterval.millis("168"))
        assertEquals(720 * hour, UpdateCheckInterval.millis("720"))
        assertEquals(2160 * hour, UpdateCheckInterval.millis("2160"))
        assertEquals(4320 * hour, UpdateCheckInterval.millis("4320"))
    }

    @Test
    fun `missing malformed or negative value falls back to every launch`() {
        assertEquals(0L, UpdateCheckInterval.millis(null))
        assertEquals(0L, UpdateCheckInterval.millis(""))
        assertEquals(0L, UpdateCheckInterval.millis("weekly"))
        assertEquals(0L, UpdateCheckInterval.millis("-24"))
    }

    @Test
    fun `only the former 6 h 12 h and daily choices are retired`() {
        listOf("6", "12", "24").forEach { assertTrue(it, UpdateCheckInterval.isRetired(it)) }
        listOf("0", "168", "720", "2160", "4320", null).forEach {
            assertFalse(it.toString(), UpdateCheckInterval.isRetired(it))
        }
    }
}
