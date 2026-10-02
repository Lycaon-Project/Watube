package com.watube.yard.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import com.watube.yard.extensions.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.DialogPlayOfflineBinding
import com.watube.yard.util.TextUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlayOfflineDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPlayOfflineBinding.inflate(layoutInflater)
        binding.videoTitle.text = requireArguments().getString(IntentData.videoTitle)

        val downloadInfo = requireArguments().getStringArray(IntentData.downloadInfo)
        binding.downloadInfo.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, downloadInfo.orEmpty().map {
                TextUtils.SEPARATOR + it
            })

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_play_offline_title)
            .setView(binding.root)
            .setPositiveButton(R.string.yes) { _, _ ->
                setFragmentResult(
                    PLAY_OFFLINE_DIALOG_REQUEST_KEY,
                    bundleOf(IntentData.isPlayingOffline to true)
                )
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                setFragmentResult(
                    PLAY_OFFLINE_DIALOG_REQUEST_KEY,
                    bundleOf(IntentData.isPlayingOffline to false)
                )
            }
            .show()
    }

    companion object {
        const val PLAY_OFFLINE_DIALOG_REQUEST_KEY = "play_offline_dialog_request_key"
    }
}