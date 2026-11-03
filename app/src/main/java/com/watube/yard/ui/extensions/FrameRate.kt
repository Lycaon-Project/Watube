package com.watube.yard.ui.extensions

import android.os.Build
import android.view.View

// region UI Components
/**
 * For small views redrawn on a timer (progress, elapsed time): on Android 15+ high refresh rate
 * screens, the display may then stay at a low rate instead of rising to 90 / 120 Hz for a few
 * changing pixels. Touch input still raises it, so scrubbing stays smooth.
 */
fun View.preferLowFrameRate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_LOW
    }
}
// endregion
