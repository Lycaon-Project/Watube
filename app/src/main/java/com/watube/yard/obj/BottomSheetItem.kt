package com.watube.yard.obj

data class BottomSheetItem(
    val title: String,
    val drawable: Int? = null,
    val getCurrent: () -> String? = { null },
    val isSelected: Boolean = false,
    /** short marker drawn as a pill after the title (e.g. HD / 4K) */
    val badge: String? = null,
    val onClick: () -> Unit = {},
)
