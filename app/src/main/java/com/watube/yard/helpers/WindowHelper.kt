package com.watube.yard.helpers

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.watube.yard.ui.extensions.toggleSystemBars

object WindowHelper {
    private const val NAVIGATION_MODE = "navigation_mode"

    /**
     * Lays [window] out over the whole screen, display cutout included, with the status and
     * navigation bars hidden (they come back transiently on a swipe from the edge).
     */
    fun applyFullscreen(window: Window) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // See: https://developer.android.com/training/system-ui/immersive#kotlin
        window.toggleSystemBars(types = WindowInsetsCompat.Type.systemBars(), showBars = false)
    }

    /**
     * Returns [WindowInsetsCompat.Type.systemBars] if the user uses gesture navigation
     * Otherwise returns [WindowInsetsCompat.Type.statusBars]
     */
    fun getGestureControlledBars(context: Context): Int {
        if (Settings.Secure.getInt(context.contentResolver, NAVIGATION_MODE, 0) == 2) {
            return WindowInsetsCompat.Type.systemBars()
        }

        return WindowInsetsCompat.Type.statusBars()
    }

    fun hasCutout(view: View) = ViewCompat.getRootWindowInsets(view)?.displayCutout != null
}
