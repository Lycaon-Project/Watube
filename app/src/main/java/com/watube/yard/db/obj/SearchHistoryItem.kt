package com.watube.yard.db.obj

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// region Search
@Serializable
@Entity(tableName = "searchHistoryItem")
data class SearchHistoryItem(
    @PrimaryKey val query: String = ""
)
// endregion
