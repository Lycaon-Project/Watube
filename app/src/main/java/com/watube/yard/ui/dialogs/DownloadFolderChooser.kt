package com.watube.yard.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.fragment.app.Fragment
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.DialogDownloadFolderBinding
import com.watube.yard.extensions.TAG
import com.watube.yard.helpers.DownloadDestination
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ThemeHelper
import com.watube.yard.ui.base.SecureAlertDialogBuilder

// region Downloads
/** Size of the detail line of a choice, against its name. */
private const val CHOICE_DETAIL_SCALE = 0.85f

/**
 * Choice of the downloads destination folder: Watube's own (Download/Watube) or one the user
 * picks, kept through a lasting access. Shared by the "Données & maintenance" setting and the
 * library shortcut.
 *
 * It registers the folder picker, so it must be created while [fragment] is (a property
 * initializer). [onChanged] runs once the folder changed.
 */
class DownloadFolderChooser(
    private val fragment: Fragment,
    private val onChanged: () -> Unit
) {
    private val pickFolder =
        fragment.registerForActivityResult(OpenDocumentTree()) { onFolderPicked(it) }

    /**
     * What the folder is for, where the downloads go now, and the folders to choose from:
     * buttons drawn like the dialog's Cancel, each with its name over where it is.
     */
    fun show() {
        val context = fragment.requireContext()
        val binding = DialogDownloadFolderBinding.inflate(fragment.layoutInflater)
        binding.downloadFolderCurrent.text = context.getString(
            R.string.download_folder_current,
            DownloadDestination.describe(context)
        )
        binding.downloadFolderDefault.text = choiceLabel(
            context.getString(R.string.download_folder_default),
            DownloadDestination.defaultLabel()
        )
        binding.downloadFolderPick.text = choiceLabel(
            context.getString(R.string.download_folder_pick),
            context.getString(R.string.download_folder_pick_hint)
        )
        val dialog = SecureAlertDialogBuilder(context)
            .setTitle(R.string.download_folder)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .show()
        binding.downloadFolderDefault.setOnClickListener {
            dialog.dismiss()
            setFolder(null)
        }
        binding.downloadFolderPick.setOnClickListener {
            dialog.dismiss()
            runCatching { pickFolder.launch(DownloadDestination.pickedFolder()) }
                .onFailure { Log.e(TAG(), "No folder picker", it) }
        }
    }

    /** A choice's name, and under it, smaller and quieter, where it is. */
    private fun choiceLabel(name: String, detail: String): CharSequence {
        val quiet = ThemeHelper.getThemeColor(
            fragment.requireContext(),
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        return buildSpannedString {
            append(name)
            append('\n')
            inSpans(RelativeSizeSpan(CHOICE_DETAIL_SCALE), ForegroundColorSpan(quiet)) {
                append(detail)
            }
        }
    }

    private fun onFolderPicked(uri: Uri?) {
        uri ?: return
        val context = fragment.context ?: return
        val kept = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, ACCESS_FLAGS)
        }.onFailure { Log.e(TAG(), "No lasting access to the picked folder", it) }.isSuccess
        if (kept) {
            setFolder(uri)
        } else {
            Toast.makeText(context, R.string.download_folder_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Remembers [uri] (null: Watube's own folder) and gives back the access to the former one. */
    private fun setFolder(uri: Uri?) {
        val former = DownloadDestination.pickedFolder()
        PreferenceHelper.putString(PreferenceKeys.DOWNLOAD_FOLDER, uri?.toString().orEmpty())
        if (former != null && former != uri) {
            fragment.context?.let { context ->
                runCatching { context.contentResolver.releasePersistableUriPermission(former, ACCESS_FLAGS) }
            }
        }
        onChanged()
    }

    private companion object {
        const val ACCESS_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
// endregion
