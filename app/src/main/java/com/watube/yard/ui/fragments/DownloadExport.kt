package com.watube.yard.ui.fragments

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.db.DatabaseHolder.Database
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.enums.FileType
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.DownloadEncryption
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import com.watube.yard.ui.sheets.DownloadOptionsBottomSheet
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

// region Downloads
/**
 * "Export the file" of a download: a clear copy of one of its files, written where the user
 * chooses. Downloads are stored encrypted (see DownloadEncryption), this is the way to use one
 * outside of Watube. Created with its [fragment], before the fragment is created.
 */
class DownloadExport(private val fragment: Fragment) {
    private var source: Path? = null
    private val createFile = fragment.registerForActivityResult(CreateDocument(MIME_TYPE)) {
        onDestination(it)
    }

    /** Follows the "export" action of the download options while the fragment's view lives. */
    fun bind() {
        fragment.requireActivity().supportFragmentManager.setFragmentResultListener(
            DownloadOptionsBottomSheet.EXPORT_DOWNLOAD_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result -> result.getString(IntentData.videoId)?.let(::export) }
    }

    private fun export(videoId: String) {
        fragment.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                Database.downloadDao().findById(videoId)?.downloadItems.orEmpty()
                    .filter { it.isFinished }
            }
            val context = fragment.context ?: return@launch
            when (files.size) {
                0 -> context.toastFromMainDispatcher(R.string.download_export_unfinished)
                1 -> chooseDestination(files.single())
                else -> SecureAlertDialogBuilder(context)
                    .setTitle(R.string.download_export_choose)
                    .setItems(files.map { labelOf(context, it) }.toTypedArray()) { _, which ->
                        chooseDestination(files[which])
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun labelOf(context: Context, file: DownloadItem): String {
        val kind = context.getString(
            when (file.type) {
                FileType.VIDEO -> R.string.video
                FileType.AUDIO -> R.string.audio
                FileType.SUBTITLE -> R.string.captions
            }
        )
        val detail = listOfNotNull(file.quality, file.format, file.language).joinToString(" · ")
        return if (detail.isEmpty()) kind else "$kind · $detail"
    }

    private fun chooseDestination(file: DownloadItem) {
        source = file.path
        createFile.launch(file.fileName)
    }

    private fun onDestination(uri: Uri?) {
        val source = source ?: return
        this.source = null
        val appContext = fragment.context?.applicationContext ?: return
        if (uri == null) return

        // started even if the screen closes meanwhile: the file the picker created gets filled
        fragment.lifecycleScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            val exported = runCatching {
                val output = appContext.contentResolver.openOutputStream(uri, "wt")
                    ?: error("export destination unavailable")
                output.use { DownloadEncryption.decryptTo(source, it) }
            }.onFailure {
                Log.e(TAG(), "Could not export a download", it)
                // no cut-short clear copy is left behind where the user put it
                runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
            }.isSuccess
            appContext.toastFromMainDispatcher(
                if (exported) R.string.download_exported else R.string.download_export_failed
            )
        }
    }

    private companion object {
        const val MIME_TYPE = "application/octet-stream"
    }
}
// endregion
