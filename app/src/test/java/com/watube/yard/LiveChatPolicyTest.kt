package com.watube.yard

import com.watube.yard.helpers.LiveChatPolicy
import com.watube.yard.helpers.LiveChatPolicy.LiveChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveChatPolicyTest {

    private fun message(id: String, text: String = "hi") =
        LiveChatMessage(id = id, author = "a", text = text, isOwner = false, isVerified = false)

    @Test
    fun `merge appends only the messages not seen yet`() {
        val current = listOf(message("1"), message("2"))

        val merged = LiveChatPolicy.merge(current, listOf(message("2"), message("3"), message("3")))

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }

    @Test
    fun `merge keeps messages without an id`() {
        val merged = LiveChatPolicy.merge(listOf(message("")), listOf(message(""), message("")))

        assertEquals(3, merged.size)
    }

    @Test
    fun `merge keeps only the newest messages in memory`() {
        val current = (1..LiveChatPolicy.MAX_MESSAGES).map { message("old$it") }

        val merged = LiveChatPolicy.merge(current, listOf(message("new1"), message("new2")))

        assertEquals(LiveChatPolicy.MAX_MESSAGES, merged.size)
        assertEquals("old3", merged.first().id)
        assertEquals("new2", merged.last().id)
    }

    @Test
    fun `next delay goes back to the shortest when new messages came`() {
        assertEquals(LiveChatPolicy.FIRST_DELAY_MS, LiveChatPolicy.nextDelay(48_000L, gotNew = true))
    }

    @Test
    fun `next delay doubles on a quiet chat up to the longest`() {
        assertEquals(12_000L, LiveChatPolicy.nextDelay(LiveChatPolicy.FIRST_DELAY_MS, gotNew = false))
        assertEquals(LiveChatPolicy.MAX_DELAY_MS, LiveChatPolicy.nextDelay(48_000L, gotNew = false))
        assertEquals(LiveChatPolicy.MAX_DELAY_MS, LiveChatPolicy.nextDelay(LiveChatPolicy.MAX_DELAY_MS, gotNew = false))
    }

    @Test
    fun `plain text puts a message on one line`() {
        assertEquals("a b c", LiveChatPolicy.oneLine("a\n\n\nb\t  c "))
    }
}
