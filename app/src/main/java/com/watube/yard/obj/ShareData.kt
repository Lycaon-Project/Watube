package com.watube.yard.obj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// region Share
@Parcelize
data class ShareData(
    val currentChannel: String? = null,
    val currentVideo: String? = null,
    val currentPlaylist: String? = null,
    var currentPosition: Long? = null
) : Parcelable
// endregion
