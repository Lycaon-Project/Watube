package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Subscriptions
@Serializable
data class Subscribed(val subscribed: Boolean? = null)
// endregion
