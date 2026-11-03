package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Piped Account
@Serializable
data class DeleteUserRequest(val password: String)
// endregion
