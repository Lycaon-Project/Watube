package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Subscriptions
@Serializable
data class Subscription(
    val url: String,
    val name: String,
    val avatar: String? = null,
    val verified: Boolean
)
// endregion
