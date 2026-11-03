package com.watube.yard.api.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region SponsorBlock
@Serializable
data class SubmitSegmentResponse(
    @SerialName("UUID") val uuid: String,
    val category: String,
    val segment: List<Float>
)
// endregion
