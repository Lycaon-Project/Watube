package com.watube.yard.ui.fragments

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.db.DatabaseHolder.Database
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.enums.FileType
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.DownloadDestination
import com.watube.yard.helpers.DownloadEncryption
import com.watube.yard.helpers.DownloadHelper
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import com.watube.yard.ui.sheets.DownloadOptionsBottomSheet
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// region Downloads
/**
 * "Export the file" of a download, and of a whole downloaded playlist: a clear copy written to
 * the destination folder (see DownloadDestination), where the other apps and the file manager
 * reach it. Downloads are stored encrypted (see DownloadEncryption), this is the way to use one
 * outside of Watube.
 */
class DownloadExport(private val fragment: Fragment) {
    /** Follows the "export" action of the download options while the fragment's view lives. */
    fun bind() {
        fragment.requireActivity().supportFragmentManager.setFragmentResultListener(
            DownloadOptionsBottomSheet.EXPORT_DOWNLOAD_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result -> result.getString(IntentData.videoId)?.let(::export) }
    }

    private fun export(videoId: String) {
        fragment.lifecycleScope.launch {
            val download = withContext(Dispatchers.IO) { Database.downloadDao().findById(videoId) }
            val files = download?.downloadItems.orEmpty().filter { it.isFinished }
            val title = download?.download?.title
            val context = fragment.context ?: return@launch
            when (files.size) {
                0 -> context.toastFromMainDispatcher(R.string.download_export_unfinished)
                1 -> exportFile(title, files.single())
                else -> SecureAlertDialogBuilder(context)
                    .setTitle(R.string.download_export_choose)
                    .setItems(files.map { labelOf(context, it) }.toTypedArray()) { _, which ->
                        exportFile(title, files[which])
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

    private fun exportFile(title: String?, file: DownloadItem) {
        val appContext = fragment.context?.applicationContext ?: return
        // started even if the screen closes meanwhile: the copy is not left cut short
        fragment.lifecycleScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            // kept readable already (Save readable): a second copy would only take room
            if (!DownloadEncryption.isEncrypted(file.path) && DownloadHelper.isStored(appContext, file.path)) {
                val folder = file.path.parent?.let { DownloadDestination.locate(appContext, it) }.orEmpty()
                appContext.toastFromMainDispatcher(
                    appContext.getString(R.string.download_already_readable, folder)
                )
                return@launch
            }
            val where = try {
                DownloadDestination.export(
                    appContext,
                    file.path,
                    file.type,
                    DownloadDestination.fileNameOf(title, file)
                )
            } catch (e: IOException) {
                Log.e(TAG(), "Could not export a download", e)
                null
            }
            appContext.toastFromMainDispatcher(
                where?.let { appContext.getString(R.string.download_exported, it) }
                    ?: appContext.getString(R.string.download_export_failed)
            )
        }
    }

    /**
     * Exports every finished file of the downloaded playlist [playlistId], in a folder named
     * after the playlist within the Video and Audio folders.
     */
    fun exportPlaylist(playlistId: String) {
        val appContext = fragment.context?.applicationContext ?: return
        fragment.lifecycleScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            val playlist = Database.downloadDao().getDownloadPlaylistByIdIncludingItems(playlistId)
            val group = playlist.downloadPlaylist.title
            var exported = 0
            var failed = 0
            playlist.downloadVideos.forEach { video ->
                video.downloadItems.filter { it.isFinished }.forEach { file ->
                    try {
                        DownloadDestination.export(
                            appContext,
                            file.path,
                            file.type,
                            DownloadDestination.fileNameOf(video.download.title, file),
                            group
                        )
                        exported++
                    } catch (e: IOException) {
                        Log.e(TAG(), "Could not export a file of a playlist", e)
                        failed++
                    }
                }
            }
            val message = when {
                exported > 0 -> appContext.resources.getQuantityString(
                    R.plurals.playlist_exported,
                    exported,
                    exported,
                    DownloadDestination.describe(appContext),
                    group
                )

                failed > 0 -> appContext.getString(R.string.download_export_failed)
                else -> appContext.getString(R.string.download_export_unfinished)
            }
            appContext.toastFromMainDispatcher(message)
        }
    }
}
// endregion
