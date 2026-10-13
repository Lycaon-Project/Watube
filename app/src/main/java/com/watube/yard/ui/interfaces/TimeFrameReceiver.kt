package com.watube.yard.ui.interfaces

import android.graphics.Bitmap

// region Seekbar Preview
abstract class TimeFrameReceiver {
    abstract suspend fun getFrameAtTime(position: Long): Bitmap?
}
// endregion
