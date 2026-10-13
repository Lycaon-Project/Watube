package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region SponsorBlock
@Serializable
data class SegmentData(
    val hash: String? = null,
    val segments: List<Segment> = listOf(),
    val videoID: String? = null
)
// endregion
