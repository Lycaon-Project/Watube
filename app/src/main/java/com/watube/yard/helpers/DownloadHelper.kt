package com.watube.yard.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import com.watube.yard.R
import com.watube.yard.api.PlaylistsHelper
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.db.obj.DownloadWithItems
import com.watube.yard.enums.FileType
import com.watube.yard.enums.PlaylistType
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.bundleOf
import com.watube.yard.extensions.toID
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.parcelable.DownloadData
import com.watube.yard.services.DownloadService
import com.watube.yard.ui.dialogs.DownloadDialog
import com.watube.yard.ui.dialogs.DownloadPlaylistDialog
import com.watube.yard.ui.dialogs.ShareDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.moveTo

// region Downloads
object DownloadHelper {
    const val VIDEO_DIR = "video"
    const val AUDIO_DIR = "audio"
    const val SUBTITLE_DIR = "subtitle"
    const val THUMBNAIL_DIR = "thumbnail"
    const val PLAYLIST_THUMBNAIL_DIR = "playlist_thumbnail"
    private const val PARTIAL_DIR = "downloading"
    private const val SPACE_MARGIN = 16L * 1024 * 1024
    private const val LEFTOVER_AGE_MS = 60L * 60 * 1000

    /** One file encrypted at a time: see [encryptInPlace]. */
    private val encryption = Mutex()
    const val DOWNLOAD_CHUNK_SIZE = 8L * 1024
    const val DEFAULT_TIMEOUT = 15 * 1000
    const val MAX_CONCURRENT_DOWNLOADS = 6
    private const val VIDEO_MIMETYPE = "video/*"

    fun getDownloadDir(context: Context, path: String): Path {
        val storageDir =
            try {
                context.getExternalFilesDir(null)!!
            } catch (_: Exception) {
                context.filesDir
            }
        val base = storageDir.toPath().normalize()
        // never leave the app storage, whatever the requested directory is
        val dir = (base / path).normalize()
        return (if (dir.startsWith(base)) dir else base).createDirectories()
    }

    /**
     * Where a download is written while it runs (and can be paused and resumed): the app's
     * private storage, out of reach of USB and of the other apps. Once complete, it is
     * encrypted into [getDownloadDir] (see DownloadEncryption) and this copy is deleted.
     */
    fun getPartialDownloadDir(context: Context, path: String): Path {
        val base = (context.noBackupFilesDir.toPath() / PARTIAL_DIR).normalize()
        val dir = (base / path).normalize()
        return (if (dir.startsWith(base)) dir else base).createDirectories()
    }

    /**
     * Encrypts the complete clear file of [item] into its place among the downloads, then
     * points the item at it and deletes the clear copy. The clear file is only deleted once
     * the database knows the encrypted one: an interruption never loses the download.
     *
     * [onEncrypted] runs under the lock [discardDownloadItem] takes too: a download stopped
     * meanwhile is never announced complete, and is gone from the database (nothing to do).
     */
    suspend fun encryptInPlace(
        context: Context,
        item: DownloadItem,
        onEncrypted: suspend () -> Unit = {}
    ) = encryption.withLock {
        // the download service and the start-up pass may both reach the same file: the
        // stored item is the one written back, a stale copy would undo other changes
        val dao = DatabaseHolder.Database.downloadDao()
        val current = dao.findDownloadItemById(item.id) ?: return@withLock
        item.path = current.path
        if (DownloadEncryption.isEncrypted(current.path)) {
            onEncrypted()
            return@withLock
        }
        val clear = current.path
        val target = getDownloadDir(context, directoryOf(current.type))
        // the encrypted copy is written next to the clear one before the latter goes
        if (target.toFile().usableSpace < clear.fileSize() + SPACE_MARGIN) {
            throw NotEnoughSpaceException()
        }
        val encrypted = DownloadEncryption.encryptedPathOf(resolveDownloadPath(target, current.fileName))
        DownloadEncryption.encryptFile(clear, encrypted)
        current.path = encrypted
        dao.updateDownloadItem(current)
        item.path = encrypted
        clear.deleteIfExists()
        onEncrypted()
    }

    /**
     * A download started by a version without encryption runs among the downloads, in reach
     * of USB: it is moved to [getPartialDownloadDir] before it goes on. Returns false when
     * it is not to go on: stopped meanwhile, or complete and encrypted already.
     */
    suspend fun moveToPartialDir(context: Context, item: DownloadItem): Boolean = encryption.withLock {
        // read again under the lock, like encryptInPlace: the start-up pass may have run since
        val dao = DatabaseHolder.Database.downloadDao()
        val current = dao.findDownloadItemById(item.id) ?: return@withLock false
        item.path = current.path
        if (DownloadEncryption.isEncrypted(current.path)) return@withLock false
        val partialDir = getPartialDownloadDir(context, directoryOf(current.type))
        if (current.path.startsWith(partialDir)) return@withLock true

        val moved = resolveDownloadPath(partialDir, current.fileName)
        if (current.path.exists()) {
            // from one storage volume to another, the move is a copy
            if (partialDir.toFile().usableSpace < current.path.fileSize() + SPACE_MARGIN) {
                throw NotEnoughSpaceException()
            }
            current.path.moveTo(moved, overwrite = true)
            // a copy keeps the old date, which would make it look like a leftover
            moved.toFile().setLastModified(System.currentTimeMillis())
        }
        current.path = moved
        dao.updateDownloadItem(current)
        item.path = moved
        true
    }

    /**
     * Forgets the download item [id] and deletes its file. Waits for an encryption under way,
     * so that the encrypted copy goes as well. Returns the item, null if it was gone already.
     */
    suspend fun discardDownloadItem(id: Int): DownloadItem? = encryption.withLock {
        val dao = DatabaseHolder.Database.downloadDao()
        val item = dao.findDownloadItemById(id) ?: return@withLock null
        dao.deleteDownloadItemById(id)
        runCatching { item.path.deleteIfExists() }
            .onFailure { Log.e(TAG(), "Could not delete a stopped download", it) }
        item
    }

    /** Encrypting a download needs room for a second copy of it, for a moment. */
    class NotEnoughSpaceException : java.io.IOException("not enough space for the download")

    /**
     * Downloads made before the encryption, encrypted in the background at start-up, one file
     * at a time; a file that fails stays as it is and is tried again on the next start. Then
     * what an interrupted download or encryption left behind is removed.
     */
    suspend fun encryptExistingDownloads(context: Context) = withContext(Dispatchers.IO) {
        val dao = DatabaseHolder.Database.downloadDao()
        dao.getAll().forEach { (download, items) ->
            items.filter { isCompleteClearFile(it) }.forEach { item ->
                runCatching { encryptInPlace(context, item) }
                    .onFailure { Log.e(TAG(), "Could not encrypt a download", it) }
            }
            encryptThumbnail(download.thumbnailPath) { dao.updateThumbnailPath(download.videoId, it) }
        }
        dao.getDownloadPlaylists().forEach { (playlist) ->
            encryptThumbnail(playlist.thumbnailPath) {
                dao.updatePlaylistThumbnailPath(playlist.playlistId, it)
            }
        }
        // the files the database knows right now, under the lock: a download moved by a
        // resume meanwhile is never taken for a leftover
        encryption.withLock {
            val known = dao.getAll().flatMap { it.downloadItems }.map { it.path }.toSet()
            removeLeftovers(context, known)
        }
    }

    /** Subtitles have no known size: one written by an older version was complete at once. */
    private fun isCompleteClearFile(item: DownloadItem): Boolean {
        if (DownloadEncryption.isEncrypted(item.path) || !item.path.exists()) return false
        return item.isFinished || item.type == FileType.SUBTITLE && item.path.fileSize() > 0
    }

    /**
     * Clear files of downloads cancelled or deleted while running, and encrypted copies cut
     * short (".part"): nothing refers to them any more. Recent ones are left alone, a download
     * may just be starting.
     */
    private fun removeLeftovers(context: Context, known: Set<Path>) {
        val before = System.currentTimeMillis() - LEFTOVER_AGE_MS
        val partial = (context.noBackupFilesDir.toPath() / PARTIAL_DIR).toFile()
        val encryptedDirs = listOf(VIDEO_DIR, AUDIO_DIR, SUBTITLE_DIR, THUMBNAIL_DIR, PLAYLIST_THUMBNAIL_DIR)
            .map { getDownloadDir(context, it).toFile() }
        val leftovers = partial.walkBottomUp().filter { it.isFile && it.toPath() !in known } +
            encryptedDirs.asSequence().flatMap { dir ->
                dir.listFiles().orEmpty().asSequence().filter { it.name.endsWith(".part") }
            }
        leftovers.filter { it.lastModified() < before }.forEach { it.delete() }
    }

    private suspend fun encryptThumbnail(clear: Path?, store: suspend (Path) -> Unit) {
        if (clear == null || DownloadEncryption.isEncrypted(clear) || !clear.exists()) return
        runCatching {
            val encrypted = DownloadEncryption.encryptedPathOf(clear)
            DownloadEncryption.encryptFile(clear, encrypted)
            store(encrypted)
            clear.deleteIfExists()
        }.onFailure { Log.e(TAG(), "Could not encrypt a download thumbnail", it) }
    }

    /** The directory of a file of [type], under [getDownloadDir] or [getPartialDownloadDir]. */
    fun directoryOf(type: FileType): String = when (type) {
        FileType.AUDIO -> AUDIO_DIR
        FileType.VIDEO -> VIDEO_DIR
        FileType.SUBTITLE -> SUBTITLE_DIR
    }

    /**
     * Resolves [fileName] inside [directory] and guarantees the result stays there:
     * separators are replaced and a crafted name ("../../..") can never make a download
     * write outside of its own directory.
     */
    fun resolveDownloadPath(directory: Path, fileName: String): Path {
        val safeName = fileName
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .ifBlank { "download" }

        val base = directory.normalize()
        val resolved = (base / safeName).normalize()
        // "." or ".." would collapse to the directory itself or to a parent: keep a file inside
        return if (resolved.startsWith(base) && resolved != base) resolved else base / "download"
    }

    fun startDownloadService(context: Context, downloadData: DownloadData? = null) {
        val intent = Intent(context, DownloadService::class.java)
            .putExtra(IntentData.downloadData, downloadData)

        ContextCompat.startForegroundService(context, intent)
    }

    fun DownloadItem.getNotificationId(): Int {
        return Int.MAX_VALUE - id
    }

    fun startDownloadDialog(context: Context, fragmentManager: FragmentManager, videoId: String) {
        val externalProviderPackageName =
            PreferenceHelper.getString(PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER, "")

        if (externalProviderPackageName.isBlank()) {
            DownloadDialog().apply {
                arguments = bundleOf(IntentData.videoId to videoId)
            }.show(fragmentManager, DownloadDialog::class.java.name)
        } else {
            val intent = Intent(Intent.ACTION_VIEW)
                .setPackage(externalProviderPackageName)
                .setDataAndType(
                    "${ShareDialog.YOUTUBE_FRONTEND_URL}/watch?v=$videoId".toUri(),
                    VIDEO_MIMETYPE
                )

            runCatching { context.startActivity(intent) }
        }
    }

    fun startDownloadPlaylistDialog(
        context: Context,
        fragmentManager: FragmentManager,
        playlistId: String,
        playlistName: String,
        playlistType: PlaylistType
    ) {
        val externalProviderPackageName =
            PreferenceHelper.getString(PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER, "")

        if (externalProviderPackageName.isBlank()) {
            val downloadPlaylistDialog = DownloadPlaylistDialog().apply {
                arguments = bundleOf(
                    IntentData.playlistId to playlistId,
                    IntentData.playlistName to playlistName,
                    IntentData.playlistType to playlistType
                )
            }
            downloadPlaylistDialog.show(fragmentManager, null)
        } else if (playlistType == PlaylistType.PUBLIC) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setPackage(externalProviderPackageName)
                .setDataAndType(
                    "${ShareDialog.YOUTUBE_FRONTEND_URL}/playlist?list=$playlistId".toUri(),
                    VIDEO_MIMETYPE
                )

            runCatching { context.startActivity(intent) }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val playlistVideoIds = try {
                    PlaylistsHelper.getPlaylist(playlistId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    context.toastFromMainDispatcher(R.string.unknown_error)
                    return@launch
                }.relatedStreams.mapNotNull { it.url?.toID() }.joinToString(",")

                val intent = Intent(Intent.ACTION_VIEW)
                    .setPackage(externalProviderPackageName)
                    .setDataAndType(
                        "${ShareDialog.YOUTUBE_FRONTEND_URL}/watch_videos?video_ids=${playlistVideoIds}".toUri(),
                        VIDEO_MIMETYPE
                    )

                withContext(Dispatchers.Main) {
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    fun extractDownloadInfoText(context: Context, download: DownloadWithItems): List<String> {
        val downloadInfo = mutableListOf<String>()
        download.downloadItems.firstOrNull { it.type == FileType.VIDEO }?.let { videoItem ->
            downloadInfo.add(context.getString(R.string.video) + ": ${videoItem.format} ${videoItem.quality}")
        }
        download.downloadItems.firstOrNull { it.type == FileType.AUDIO }?.let { audioItem ->
            var infoString = ": ${audioItem.quality} ${audioItem.format})"
            if (audioItem.language != null) infoString += " ${audioItem.language}"
            downloadInfo.add(context.getString(R.string.audio) + infoString)
        }
        download.downloadItems.firstOrNull { it.type == FileType.SUBTITLE }?.let {
            downloadInfo.add(context.getString(R.string.captions) + ": ${it.language}")
        }
        return downloadInfo
    }

    suspend fun deleteDownloadIncludingFiles(downloadWithItems: DownloadWithItems) {
        val download = downloadWithItems.download
        val items = downloadWithItems.downloadItems

        items.forEach {
            it.path.deleteIfExists()
        }
        runCatching {
            download.thumbnailPath?.deleteIfExists()
        }

        withContext(Dispatchers.IO) {
            // downloadChapters n'a pas de FK CASCADE : nettoyage manuel sinon orphelins
            DatabaseHolder.Database.downloadDao().deleteChaptersForVideo(download.videoId)
            DatabaseHolder.Database.downloadDao().deleteDownload(download)
        }
    }
}
// endregion
