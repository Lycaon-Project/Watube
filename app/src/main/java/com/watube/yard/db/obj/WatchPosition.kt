package com.watube.yard.db.obj

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// region Watch Positions
/**
 * @param position: Position in milliseconds
 */
@Serializable
@Entity(tableName = "watchPosition")
data class WatchPosition(
    @PrimaryKey val videoId: String = "",
    @ColumnInfo val position: Long = 0L
)
// endregion
