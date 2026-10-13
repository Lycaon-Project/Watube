package com.watube.yard.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class NewPipeSubscriptions(
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("app_version_int") val appVersionInt: Int = 0,
    val subscriptions: List<NewPipeSubscription> = emptyList()
)
// endregion
