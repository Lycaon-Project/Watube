package com.watube.yard.ui.interfaces

import android.graphics.Bitmap

// region Seekbar Preview
abstract class TimeFrameReceiver {
    abstract suspend fun getFrameAtTime(position: Long): Bitmap?

    /** Lets go of what the receiver holds open; no frame is asked for afterwards. */
    open fun release() = Unit
}
// endregion
