package com.watube.yard.api.obj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

// region Channels
@Serializable
@Parcelize
data class ChannelTab(
    val name: String,
    val data: String
) : Parcelable
// endregion
