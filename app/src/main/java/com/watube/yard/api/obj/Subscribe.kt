package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Subscriptions
@Serializable
data class Subscribe(val channelId: String)
// endregion
