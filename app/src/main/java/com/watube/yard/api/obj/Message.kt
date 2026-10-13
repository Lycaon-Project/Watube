package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Piped Instances
@Serializable
data class Message(
    val error: String? = null,
    val message: String? = null
)
// endregion
