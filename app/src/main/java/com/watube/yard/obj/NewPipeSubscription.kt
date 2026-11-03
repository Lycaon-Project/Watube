package com.watube.yard.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Backup & Restore
@Serializable
data class NewPipeSubscription(
    val name: String,
    @SerialName("service_id") val serviceId: Int,
    val url: String
)
// endregion
