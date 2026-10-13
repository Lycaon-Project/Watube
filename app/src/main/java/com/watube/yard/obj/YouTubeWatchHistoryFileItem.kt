package com.watube.yard.obj

import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class YouTubeWatchHistoryFileItem(
    val header: String = "",
    val time: String = "",
    val title: String = "",
    val titleUrl: String = "",
    val activityControls: List<String> = emptyList(),
    val products: List<String> = emptyList(),
    val subtitles: List<YouTubeWatchHistoryChannelInfo> = emptyList()
)
// endregion
