package com.watube.yard.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import com.watube.yard.R

object ClipboardHelper {
    fun save(
        context: Context,
        label: String = context.getString(R.string.copied),
        text: String,
        notify: Boolean = false
    ) {
        val clip = ClipData.newPlainText(label, text)
        // mark the clip as sensitive: the clipboard preview must not render it, and
        // Android 13+ clears it without showing the content on the screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(
                    android.content.ClipDescription.EXTRA_IS_SENSITIVE,
                    true
                )
            }
        }
        context.getSystemService<ClipboardManager>()!!.setPrimaryClip(clip)

        if (notify && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
    }
}
