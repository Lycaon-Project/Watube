package com.watube.yard.db.obj

import androidx.room.Embedded
import androidx.room.Relation
import kotlinx.serialization.Serializable

// region Playlists
@Serializable
data class LocalPlaylistWithVideos(
    @Embedded val playlist: LocalPlaylist = LocalPlaylist(),
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val videos: List<LocalPlaylistItem> = listOf()
)
// endregion
