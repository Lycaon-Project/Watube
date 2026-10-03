package com.watube.yard.ui.extensions

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun View.onSystemInsets(callback: (v: View, systemBarInsets: Insets) -> Unit) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        // systemBars() alone misses the camera cutout on the sides/top in landscape
        val systemBars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        callback(this, systemBars)

        WindowInsetsCompat.CONSUMED
    }
}

fun Activity.getSystemInsets(): Insets? {
    val insets =
        WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets ?: return null)
    return insets.getInsets(WindowInsetsCompat.Type.systemBars())
}
/**
 * Keeps the end of a full-screen page above the bottom system bar (3-button navigation)
 * without consuming the insets, so children like a fitsSystemWindows toolbar still get them.
 * Relative to the initial padding: insets are dispatched again on every rotation.
 */
fun View.padBottomForSystemBars() {
    val basePaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(
            bottom = basePaddingBottom + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        )
        insets
    }
}
