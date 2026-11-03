package com.watube.yard.obj

import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class PipedImportPlaylist(
    var name: String? = null,
    val type: String? = null,
    val visibility: String? = null,
    var videos: List<String> = listOf()
)
// endregion
