package com.watube.yard.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class FreeTubeImportPlaylist(
    @SerialName("playlistName") val name: String = "",
    var videos: List<FreeTubeVideo> = listOf(),
    var protected: Boolean = true
)
// endregion
