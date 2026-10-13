package com.watube.yard.obj

import com.watube.yard.ui.dialogs.ShareDialog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class FreetubeSubscription(
    val name: String,
    @SerialName("id") val channelId: String,
    val url: String = "${ShareDialog.YOUTUBE_FRONTEND_URL}/channel/$channelId"
)
// endregion
