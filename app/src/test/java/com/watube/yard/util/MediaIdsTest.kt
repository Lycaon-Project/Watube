package com.watube.yard.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIdsTest {
    @Test
    fun `well formed ids are kept`() {
        assertEquals("dQw4w9WgXcQ", MediaIds.videoId("dQw4w9WgXcQ"))
        assertEquals("UCsXVk37bltHxD1rDPwtNM8Q", MediaIds.channelId("UCsXVk37bltHxD1rDPwtNM8Q"))
        assertEquals("PLAYLIST_id-1", MediaIds.playlistId("PLAYLIST_id-1"))
        assertEquals("RDdQw4w9WgXcQ", MediaIds.playlistId("RDdQw4w9WgXcQ"))
        assertEquals("5", MediaIds.playlistId("5"))
        assertEquals("@Gronkh", MediaIds.channelName("@Gronkh"))
        assertEquals("@名前·チャンネル", MediaIds.channelName("@名前·チャンネル"))
        assertEquals("cats and dogs", MediaIds.query("cats and dogs"))
        val ids = arrayOf("dQw4w9WgXcQ", "aaaaaaaaaaa")
        assertArrayEquals(ids, MediaIds.videoIds(ids))
    }

    @Test
    fun `anything else is dropped`() {
        listOf(null, "", "..", "dQw4w9WgXc", "dQw4w9WgXcQQ", "dQw4w9/gXcQ", "../../../x").forEach {
            assertNull(it, MediaIds.videoId(it))
        }
        assertNull(MediaIds.channelId("UC.."))
        assertNull(MediaIds.channelId("UCsXVk37bltHxD1rDPwtNM8Q/x"))
        assertNull(MediaIds.playlistId("../streams"))
        assertNull(MediaIds.playlistId("a".repeat(65)))
        listOf("..", ".", "a/b", "a\\b", "a%2e", "a?b", "a#b", "a\nb", "x".repeat(101)).forEach {
            assertNull(it, MediaIds.channelName(it))
        }
        assertNull(MediaIds.query(" "))
        assertNull(MediaIds.query("q".repeat(501)))
        assertNull(MediaIds.videoIds(arrayOf("dQw4w9WgXcQ", "..")))
        assertNull(MediaIds.videoIds(Array(201) { "dQw4w9WgXcQ" }))
        assertNull(MediaIds.videoIds(emptyArray()))
    }
}
