package com.watube.yard.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.watube.yard.R
import com.watube.yard.extensions.bundleOf
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// region Rest Mode
/** Covers the app while the scheduled rest mode runs: it can only be left by quitting. */
class RestModeDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        val until = requireArguments().getString(UNTIL).orEmpty()
        return SecureAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_sleep)
            .setTitle(R.string.rest_mode_active)
            .setMessage(getString(R.string.rest_mode_active_message, until))
            .setPositiveButton(R.string.rest_mode_quit) { _, _ -> requireActivity().finishAffinity() }
            .create()
    }

    companion object {
        const val TAG = "rest_mode"
        private const val UNTIL = "until"

        fun newInstance(until: LocalTime) = RestModeDialog().apply {
            arguments = bundleOf(UNTIL to DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(until))
        }
    }
}
// endregion
