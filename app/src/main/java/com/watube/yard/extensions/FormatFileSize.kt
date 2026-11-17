package com.watube.yard.extensions

import android.content.Context
import android.text.format.Formatter

// region Common Utilities
fun Int.formatAsFileSize(context: Context): String = toLong().formatAsFileSize(context)

/**
 * The size in the user's language and units ("16,29 Mo" in French), as the system writes it:
 * the same units as the download dialog, so one file never shows two different sizes.
 */
fun Long.formatAsFileSize(context: Context): String = Formatter.formatFileSize(context, this)
// endregion
