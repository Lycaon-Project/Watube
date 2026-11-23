package com.watube.yard.ui.extensions

import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat

// region UI Components
/**
 * Names what a long press on this view does. Screen readers, switch and keyboard users reach a
 * long press only through the actions menu, where it would otherwise read just "long press";
 * the press itself still runs the view's own long click listener.
 */
fun View.nameLongPress(@StringRes label: Int) {
    ViewCompat.replaceAccessibilityAction(
        this,
        AccessibilityActionCompat.ACTION_LONG_CLICK,
        context.getString(label),
        null
    )
}
// endregion
