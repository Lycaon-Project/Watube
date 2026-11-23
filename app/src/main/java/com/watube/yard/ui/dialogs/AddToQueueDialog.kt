package com.watube.yard.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.ui.base.SecureAlertDialogBuilder

// region Playing Queue
/**
 * Asks before a video shared by another app is queued or played (AddToQueueActivity): a share
 * can come from any installed app, so nothing starts without the user's tap. Shows the short
 * link of the video so the user sees what was shared.
 *
 * Rebuilt from the 26.12 build after the emergency restore.
 */
class AddToQueueDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val videoId = requireNotNull(arguments.getString(IntentData.videoId)) {
            "AddToQueueDialog needs a video id"
        }
        val action = if (arguments.getBoolean(ADDS_TO_QUEUE)) {
            R.string.add_to_queue
        } else {
            R.string.tooltip_play
        }

        return SecureAlertDialogBuilder(requireContext())
            .setTitle(action)
            .setMessage("youtu.be/$videoId")
            .setPositiveButton(action) { _, _ ->
                setFragmentResult(ADD_TO_QUEUE_CONFIRMED_KEY, bundleOf())
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        setFragmentResult(ADD_TO_QUEUE_DIALOG_DISMISSED_KEY, bundleOf())
    }

    companion object {
        /** Argument: true when something is playing, so the video is queued rather than played. */
        const val ADDS_TO_QUEUE = "adds_to_queue"
        const val ADD_TO_QUEUE_CONFIRMED_KEY = "add_to_queue_confirmed"
        const val ADD_TO_QUEUE_DIALOG_DISMISSED_KEY = "add_to_queue_dialog_dismissed"
    }
}
// endregion
