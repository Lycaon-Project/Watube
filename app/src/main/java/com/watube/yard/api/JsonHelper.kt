package com.watube.yard.api

import kotlinx.serialization.json.Json

// region Networking
object JsonHelper {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
// endregion
