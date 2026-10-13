package com.watube.yard.obj

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// region UI Components
@Parcelize
data class SelectableOption(
    val isSelected: Boolean,
    val name: String
) : Parcelable
// endregion
