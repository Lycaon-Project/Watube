package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Comments
@Serializable
data class CommentsPage(
    var comments: List<Comment> = emptyList(),
    val disabled: Boolean = false,
    val nextpage: String? = null,
    val commentCount: Long = 0
)
// endregion
