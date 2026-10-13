package com.watube.yard.api.obj

import kotlinx.serialization.Serializable

// region Piped Instances
@Serializable
data class PipedConfig(
    val donationUrl: String? = null,
    val statusPageUrl: String? = null,
    val imageProxyUrl: String? = null
)
// endregion
