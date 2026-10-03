package com.watube.yard.ui.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import com.watube.yard.extensions.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.ui.preferences.InstanceSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogoutDialog : DialogFragment() {
    @SuppressLint("SetTextI18n")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val user = PreferenceHelper.getUsername()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(getString(R.string.already_logged_in) + " ($user)")
            .setPositiveButton(R.string.logout) { _, _ ->
                Toast.makeText(context, R.string.loggedout, Toast.LENGTH_SHORT).show()

                setFragmentResult(
                    InstanceSettings.INSTANCE_DIALOG_REQUEST_KEY,
                    bundleOf(IntentData.logoutTask to true)
                )
            }
            .show()
    }
}
