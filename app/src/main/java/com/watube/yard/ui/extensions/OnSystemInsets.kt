package com.watube.yard.ui.extensions

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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