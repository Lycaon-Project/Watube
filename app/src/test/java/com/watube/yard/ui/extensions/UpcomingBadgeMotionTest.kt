package com.watube.yard.ui.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The memory of introduced upcoming pills is shared by the whole app, so each test uses its own
 * video ids. Rebuilt from the 26.12 build after the emergency restore.
 */
class UpcomingBadgeMotionTest {
    @Test
    fun `first showing of a video introduces it`() {
        assertTrue(UpcomingBadgeMotion.firstTime("first-showing"))
    }

    @Test
    fun `later showings of the same video are still`() {
        UpcomingBadgeMotion.firstTime("shown-twice")
        assertFalse(UpcomingBadgeMotion.firstTime("shown-twice"))
        assertFalse(UpcomingBadgeMotion.firstTime("shown-twice"))
    }

    @Test
    fun `each video has its own first time`() {
        UpcomingBadgeMotion.firstTime("video-a")
        assertTrue(UpcomingBadgeMotion.firstTime("video-b"))
    }

    @Test
    fun `memory stays bounded and forgets the oldest first`() {
        UpcomingBadgeMotion.firstTime("oldest")
        repeat(300) { UpcomingBadgeMotion.firstTime("filler-$it") }
        assertTrue(UpcomingBadgeMotion.firstTime("oldest"))
        assertFalse(UpcomingBadgeMotion.firstTime("filler-299"))
    }
}
