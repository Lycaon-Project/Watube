package com.watube.yard.extensions

import kotlin.math.ceil

// region Common Utilities
fun Int.ceilHalf() = ceil((toDouble() / 2)).toInt()
// endregion
