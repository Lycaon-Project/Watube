package com.watube.yard.util

// region Deep Links
/**
 * Checks the ids and names an outside link or intent hands the app (video, channel, playlist,
 * search). Each check returns its value when it has the expected shape, else null: a malformed
 * or hostile value (a path, "..", control characters, an oversized list) is left out and the
 * app simply opens.
 *
 * Rebuilt from the 26.12 build after the emergency restore.
 */
object MediaIds {
    const val MAX_QUERY_LENGTH = 500
    const val MAX_VIDEO_IDS = 200

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
    private val PLAYLIST_ID = Regex("[A-Za-z0-9_-]{1,64}")

    /** A handle or custom name: no path, query or escape character, not only dots. */
    private val CHANNEL_NAME = Regex("""[^/\\?#%\p{Cntrl}]{1,100}""")

    fun videoId(id: String?): String? = id?.takeIf(VIDEO_ID::matches)

    fun channelId(id: String?): String? = id?.takeIf(CHANNEL_ID::matches)

    fun playlistId(id: String?): String? = id?.takeIf(PLAYLIST_ID::matches)

    fun channelName(name: String?): String? =
        name?.takeIf { CHANNEL_NAME.matches(it) && it.trim('.').isNotEmpty() }

    fun query(query: String?): String? =
        query?.takeIf { it.isNotBlank() && it.length <= MAX_QUERY_LENGTH }

    fun videoIds(ids: Array<String>?): Array<String>? =
        ids?.takeIf { it.isNotEmpty() && it.size <= MAX_VIDEO_IDS && it.all(VIDEO_ID::matches) }
}
// endregion
