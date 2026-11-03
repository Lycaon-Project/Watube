package com.watube.yard.obj

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// region Backup & Restore
@Serializable
data class PreferenceItem(
    val key: String? = null,
    val value: JsonPrimitive = JsonNull
)
// endregion
