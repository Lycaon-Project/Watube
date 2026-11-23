package com.watube.yard.helpers

// region Comments
/**
 * The rules of the live chat of a live stream, kept apart from the screen to be tested alone.
 *
 * A live chat is written by strangers and read again and again, so it is held tight:
 * - only text: no link, no image, each message on one line ([oneLine]);
 * - only in memory and only the newest [MAX_MESSAGES] ([merge]): nothing is ever stored;
 * - read every [FIRST_DELAY_MS] at most, less and less often while nothing new comes, down to
 *   once every [MAX_DELAY_MS] ([nextDelay]), and only while the chat is on screen.
 */
object LiveChatPolicy {
    /** Messages kept in memory: the oldest go once there are more. */
    const val MAX_MESSAGES = 200

    /** Shortest wait between two reads of the chat. */
    const val FIRST_DELAY_MS = 6_000L

    /** Longest wait between two reads, reached on a quiet chat or after errors. */
    const val MAX_DELAY_MS = 60_000L

    private val WHITESPACE = Regex("[\\s\\u2028\\u2029\\u0085]+")

    /** One message of the chat, already plain text. */
    data class LiveChatMessage(
        val id: String,
        val author: String,
        val text: String,
        val isOwner: Boolean,
        val isVerified: Boolean
    )

    /**
     * [current] followed by the messages of [page] not seen yet (a message without an id is
     * always new), only the newest [MAX_MESSAGES] kept.
     */
    fun merge(current: List<LiveChatMessage>, page: List<LiveChatMessage>): List<LiveChatMessage> {
        val seen = current.mapTo(HashSet()) { it.id }
        val fresh = page.filter { it.id.isEmpty() || seen.add(it.id) }
        return (current + fresh).takeLast(MAX_MESSAGES)
    }

    /** The wait before the next read: the shortest after new messages, else twice the last. */
    fun nextDelay(previous: Long, gotNew: Boolean): Long =
        if (gotNew) FIRST_DELAY_MS else (previous * 2).coerceIn(FIRST_DELAY_MS, MAX_DELAY_MS)

    /** [text] on one line: any run of spaces or line breaks becomes one space. */
    fun oneLine(text: String): String =
        text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
}
// endregion
