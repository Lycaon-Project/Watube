package com.watube.yard.obj

import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class YouTubeWatchHistoryChannelInfo(
    val name: String,
    val url: String? = null
)
// endregion
