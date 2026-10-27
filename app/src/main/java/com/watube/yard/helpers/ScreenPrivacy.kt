package com.watube.yard.helpers

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.watube.yard.constants.PreferenceKeys

// region Privacy
/**
 * "Block screenshots" setting: FLAG_SECURE keeps a window out of screenshots, screen recordings,
 * screen casting and the recent apps preview. The flag belongs to each window: activities apply it
 * to their own window, [dialogWatcher] to the window of every dialog fragment and bottom sheet,
 * and alert dialogs get it from SecureAlertDialogBuilder.
 */
object ScreenPrivacy {
    val isScreenshotBlockingEnabled: Boolean
        get() = PreferenceHelper.getBoolean(PreferenceKeys.BLOCK_SCREENSHOTS, false)

    /** Applies the current setting to [window], so that turning it off takes effect too. */
    fun applyTo(window: Window) {
        if (isScreenshotBlockingEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Gives the setting to the window of every dialog fragment (and bottom sheet) of a screen. */
    val dialogWatcher = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) = secureDialogOf(f)

        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) = secureDialogOf(f)

        private fun secureDialogOf(fragment: Fragment) {
            (fragment as? DialogFragment)?.dialog?.window?.let(::applyTo)
        }
    }
}
// endregion
