package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Channels
@Serializable
data class ChannelTabResponse(
    var content: List<ContentItem> = emptyList(),
    val nextpage: String? = null
)
// endregion
