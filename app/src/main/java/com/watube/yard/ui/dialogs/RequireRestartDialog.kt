package com.watube.yard.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import com.watube.yard.R
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.base.SecureAlertDialogBuilder

// region Settings
class RequireRestartDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return SecureAlertDialogBuilder(requireContext())
            .setTitle(R.string.require_restart)
            .setMessage(R.string.require_restart_message)
            .setPositiveButton(R.string.okay) { _, _ ->
                ActivityCompat.recreate(requireActivity())
                NavigationHelper.restartMainActivity(requireContext())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
// endregion
