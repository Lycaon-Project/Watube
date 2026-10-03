package com.watube.yard.helpers

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object DisplayHelper {
    @Volatile
    private var hdrSupport: Boolean? = null

    /**
     * Detect whether the device supports HDR as the ExoPlayer doesn't handle it properly.
     *
     * The capability of the built-in display never changes, so it is resolved once: SABR asks
     * for it on every segment request, and resolving the display from the application context
     * each time also logged a ContextCompat warning per request.
     */
    fun supportsHdr(context: Context): Boolean = hdrSupport
        ?: (context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.isHdr == true)
            .also { hdrSupport = it }
}
