package com.watube.yard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The versions written in the real tags and titles of the Watube releases on GitHub. */
class ReleaseVersionTest {
    @Test
    fun `the tag carries the version when the title does not`() {
        assertEquals(261100, ReleaseVersion.code("Watube2611", "Your update is ready"))
    }

    @Test
    fun `the most precise version wins`() {
        assertEquals(
            261004,
            ReleaseVersion.code(
                "Watube_26.10",
                "New features await you in this Watube update (version 26.10.4)"
            )
        )
        assertEquals(261003, ReleaseVersion.code("FIx_Watube", "Watube 26.10.3 Major Fix"))
        assertEquals(261001, ReleaseVersion.code("Watube-android", "Watube26.10.1.fix"))
    }

    @Test
    fun `the version code in brackets is not a version`() {
        // the 26.12 release: "6212" would read as year 62, month 12, newer than every real
        // version, so the installed 26.12 kept being offered itself as an update
        assertEquals(
            261200,
            ReleaseVersion.code("Watube_Android", "Your update is ready Watube 26.12 · Vireo (6212)")
        )
        assertNull(ReleaseVersion.code("Watube_Android", "Your update is ready (6212)"))
        assertNull(ReleaseVersion.code("6212"))
    }

    @Test
    fun `the app's own version name`() {
        assertEquals(261200, ReleaseVersion.code("26.12"))
        assertEquals(261101, ReleaseVersion.code("26.11.1"))
    }

    @Test
    fun `a newer month is a newer version`() {
        val installed = ReleaseVersion.code("26.12")!!
        assertTrue(ReleaseVersion.code("Watube2611")!! < installed)
        assertTrue(ReleaseVersion.code("v27.01")!! > installed)
    }

    @Test
    fun `no version, no number`() {
        assertNull(ReleaseVersion.code("android", "Your update is ready"))
        assertNull(ReleaseVersion.code(null, ""))
        // not a month, nor a year inside a longer number
        assertNull(ReleaseVersion.code("Watube 2613", "build 202611"))
    }
}
