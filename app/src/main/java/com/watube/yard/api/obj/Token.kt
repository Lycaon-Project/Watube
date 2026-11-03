package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Piped Account
@Serializable
data class Token(
    val token: String? = null,
    val error: String? = null
)
// endregion
