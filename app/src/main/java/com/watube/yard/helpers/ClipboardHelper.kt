package com.watube.yard.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import com.watube.yard.R

// region Share
object ClipboardHelper {
    /** Android 13+ confirms the copy itself, so no toast is needed. */
    fun save(
        context: Context,
        label: String = context.getString(R.string.copied),
        text: String
    ) {
        val clip = ClipData.newPlainText(label, text)
        // mark the clip as sensitive: the clipboard preview must not render it, and
        // Android 13+ clears it without showing the content on the screen
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(
                android.content.ClipDescription.EXTRA_IS_SENSITIVE,
                true
            )
        }
        context.getSystemService<ClipboardManager>()!!.setPrimaryClip(clip)
    }
}
// endregion
