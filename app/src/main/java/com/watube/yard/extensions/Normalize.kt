package com.watube.yard.extensions

// region Common Utilities
fun Int.normalize(oldMin: Int, oldMax: Int, newMin: Int, newMax: Int): Int {
    val oldRange = oldMax - oldMin
    val newRange = newMax - newMin

    return (this - oldMin) * newRange / oldRange + newMin
}
// endregion
