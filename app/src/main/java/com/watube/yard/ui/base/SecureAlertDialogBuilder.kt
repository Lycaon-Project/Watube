package com.watube.yard.ui.base

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.watube.yard.helpers.ScreenPrivacy

// region Privacy
/**
 * The app's dialog builder: a [MaterialAlertDialogBuilder] whose dialogs follow the "Block
 * screenshots" setting, whether they are shown directly or hosted by a dialog fragment
 * (show() goes through create()).
 */
class SecureAlertDialogBuilder(context: Context) : MaterialAlertDialogBuilder(context) {
    override fun create(): AlertDialog = super.create().also { dialog ->
        dialog.window?.let(ScreenPrivacy::applyTo)
    }
}
// endregion
