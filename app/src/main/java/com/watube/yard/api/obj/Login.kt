package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Piped Account
@Serializable
data class Login(
    val username: String,
    val password: String
)
// endregion
