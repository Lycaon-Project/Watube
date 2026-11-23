package com.watube.yard.helpers

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.obj.DownloadItem
import com.watube.yard.enums.FileType
import com.watube.yard.extensions.TAG
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.outputStream

// region Downloads
/**
 * The destination folder of the downloads (Data & maintenance > Downloads), where they can be
 * reached from the other apps and the file manager: Watube's own folder, Download/Watube, or
 * one the user picked. Either way, a Video and an Audio sub-folder; subtitles go with the
 * videos, so that players pick them up.
 *
 * Downloads are kept encrypted in the app storage (see DownloadEncryption) and only land here
 * in the clear when the user asks for it: an export, or Save readable switched on.
 *
 * Watube writes to the folder directly when Android lets it (its own files in Download/, a
 * folder picked in the phone's shared storage); a folder it may not write to that way (a
 * memory card, a cloud provider) is written to through the access the user granted.
 */
object DownloadDestination {
    private const val ROOT_NAME = "Watube"
    private const val PRIMARY_PREFIX = "primary:"
    /**
     * Bytes of a name's main part: file systems allow 255 bytes per name, room is left for a
     * twin's number, a subtitle language and the extension.
     */
    private const val NAME_BYTES = 180
    private const val MAX_TWINS = 999
    private const val FALLBACK_MIME = "application/octet-stream"

    /**
     * Extensions an audio-only download gets, so that Android files it with the music (a .webm
     * or .mp4 is taken for a video): WebM is Matroska, hence .mka.
     */
    private val audioExtensions = mapOf("webm" to "mka", "mp4" to "m4a")

    /** Characters the file systems (and the file managers) refuse in a name. */
    private val forbidden = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** Whether new downloads are kept readable, in the clear, in the destination folder. */
    val isReadable: Boolean
        get() = PreferenceHelper.getBoolean(PreferenceKeys.DOWNLOAD_READABLE, false)

    /** The folder the user picked, null for Watube's own. */
    fun pickedFolder(): Uri? =
        PreferenceHelper.getString(PreferenceKeys.DOWNLOAD_FOLDER, "").takeIf { it.isNotBlank() }?.toUri()

    /** Where the folder (or its sub-folder for [type]) is, as a file manager shows it. */
    fun describe(context: Context, type: FileType? = null, group: String? = null): String {
        val base = pickedFolder()?.let(::labelOf) ?: defaultLabel()
        return listOfNotNull(base, type?.let { subFolderOf(context, it) }, group?.let(::cleanName))
            .joinToString("/")
    }

    /** Where [folder] is, as a file manager shows it: from the root of the phone's storage. */
    fun locate(context: Context, folder: Path): String {
        val storage = context.getSystemService<StorageManager>()
            ?.primaryStorageVolume?.directory?.toPath()?.normalize()
        val dir = folder.normalize()
        return if (storage != null && dir.startsWith(storage)) {
            storage.relativize(dir).joinToString("/")
        } else {
            dir.toString()
        }
    }

    /** Download/Watube, Watube's own folder. */
    fun defaultLabel() = "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_NAME"

    /**
     * The name a file of [item] gets in the folder: the video's [title] rather than the
     * download's internal name, the subtitle language before the extension ("Title.en.srt").
     */
    fun fileNameOf(title: String?, item: DownloadItem): String {
        val extension = item.fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
            ?.let { if (item.type == FileType.AUDIO) audioExtensions[it.lowercase()] ?: it else it }
        val base = title?.let(::cleanName)?.takeIf { it.isNotEmpty() }
            ?: cleanName(item.fileName.substringBeforeLast('.'))
        val language = item.language?.takeIf { item.type == FileType.SUBTITLE }?.let(::cleanName)
        return listOfNotNull(base, language, extension).joinToString(".")
    }

    /**
     * Copies the complete clear file [clear] into the folder, as [name] in the sub-folder of
     * [type]. Returns the copy, null when the folder can't be written directly (nothing is left
     * behind then). The caller deletes [clear] once the copy is known.
     */
    fun copyInto(context: Context, clear: Path, type: FileType, name: String): Path? {
        val dir = directFolder(context, type, null) ?: return null
        if (dir.toFile().usableSpace < clear.fileSize() + DownloadHelper.SPACE_MARGIN) {
            throw DownloadHelper.NotEnoughSpaceException()
        }
        val target = twinFree(dir, name)
        return try {
            clear.copyTo(target)
            announce(context, target)
            target
        } catch (e: FileAlreadyExistsException) {
            // someone else's file appeared there meanwhile: left alone
            Log.e(TAG(), "A file took the name meanwhile", e)
            null
        } catch (e: IOException) {
            Log.e(TAG(), "Could not write to the destination folder", e)
            target.deleteIfExists()
            null
        }
    }

    /**
     * Writes the clear content of [source] (encrypted or not) as [name] in the sub-folder of
     * [type], and of [group] within it (a playlist). Returns where it went, as [describe] says.
     *
     * @throws IOException when the folder can't be written to at all
     */
    fun export(context: Context, source: Path, type: FileType, name: String, group: String? = null): String =
        try {
            exportOrThrow(context, source, type, name, group)
        } catch (e: SecurityException) {
            // the access to the picked folder was withdrawn meanwhile
            throw IOException("destination folder no longer reachable", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("destination folder unusable", e)
        }

    private fun exportOrThrow(
        context: Context,
        source: Path,
        type: FileType,
        name: String,
        group: String?
    ): String {
        val dir = directFolder(context, type, group)
        if (dir != null) {
            val target = twinFree(dir, name)
            try {
                // created here or not at all: a file found there is never written over
                target.outputStream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                    .use { DownloadEncryption.decryptTo(source, it) }
                announce(context, target)
                return describe(context, type, group)
            } catch (e: FileAlreadyExistsException) {
                throw e
            } catch (e: IOException) {
                target.deleteIfExists()
                // a folder Android lets Watube reach only through the access granted
                if (pickedFolder() == null) throw e
            }
        }
        exportThroughAccess(context, source, type, name, group)
        return describe(context, type, group)
    }

    /** Whether the folder the user picked can still be written to (access not withdrawn). */
    fun isPickedFolderReachable(context: Context): Boolean {
        val folder = pickedFolder() ?: return true
        return runCatching {
            DocumentFile.fromTreeUri(context, folder)?.let { it.exists() && it.canWrite() } == true
        }.getOrDefault(false)
    }

    private fun exportThroughAccess(
        context: Context,
        source: Path,
        type: FileType,
        name: String,
        group: String?
    ) {
        val folder = pickedFolder() ?: throw IOException("no destination folder")
        val root = DocumentFile.fromTreeUri(context, folder)
            ?.takeIf { it.exists() && it.canWrite() }
            ?: throw IOException("destination folder unreachable")
        var dir = childFolder(root, subFolderOf(context, type))
        group?.let { dir = childFolder(dir, cleanName(it)) }
        // the provider numbers a name already taken itself
        val file = dir.createFile(mimeOf(name), name) ?: throw IOException("could not create $name")
        try {
            val output = context.contentResolver.openOutputStream(file.uri, "wt")
                ?: throw IOException("could not open $name")
            output.use { DownloadEncryption.decryptTo(source, it) }
        } catch (e: IOException) {
            // no cut-short copy is left in the user's folder
            file.delete()
            throw e
        }
    }

    private fun childFolder(parent: DocumentFile, name: String): DocumentFile =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: parent.createDirectory(name)
            ?: throw IOException("could not create the folder $name")

    /**
     * The folder for [type] (and [group]) as a path, created if needed, when Watube may write
     * there directly: its own folder, or a folder picked in the phone's shared storage.
     */
    private fun directFolder(context: Context, type: FileType, group: String?): Path? {
        val root = directRoot(context) ?: return null
        val dir = listOfNotNull(subFolderOf(context, type), group?.let(::cleanName))
            .fold(root) { path, part -> path / part }
        return try {
            dir.createDirectories()
        } catch (e: IOException) {
            Log.w(TAG(), "No direct access to the destination folder", e)
            null
        }
    }

    private fun directRoot(context: Context): Path? {
        val storage = context.getSystemService<StorageManager>()
            ?.primaryStorageVolume?.directory?.toPath()?.normalize() ?: return null
        val folder = pickedFolder()
            ?: return storage / Environment.DIRECTORY_DOWNLOADS / ROOT_NAME
        val id = runCatching { DocumentsContract.getTreeDocumentId(folder) }.getOrNull()
        // only the phone's own shared storage ("primary:Music/Mixes") has a path
        if (id == null || !id.startsWith(PRIMARY_PREFIX)) return null
        val dir = (storage / id.removePrefix(PRIMARY_PREFIX)).normalize()
        return dir.takeIf { it.startsWith(storage) && !it.startsWith(storage / DownloadHelper.APP_STORAGE_DIR) }
    }

    private fun subFolderOf(context: Context, type: FileType) = context.getString(
        if (type == FileType.AUDIO) R.string.download_folder_audio else R.string.download_folder_video
    )

    /** "Music/Mixes" for a folder picked in the shared storage, its id otherwise. */
    private fun labelOf(folder: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(folder) }.getOrNull()
            ?: return folder.toString()
        return id.substringAfter(':').ifEmpty { id }
    }

    private fun cleanName(name: String) =
        fitBytes(name.replace(forbidden, "_").trim().trim('.')).trim()

    /** The longest start of [text] within [NAME_BYTES] bytes of UTF-8, never cut in a character. */
    private fun fitBytes(text: String): String {
        var bytes = 0
        val end = text.codePoints().toArray().takeWhile {
            bytes += String(Character.toChars(it)).toByteArray(Charsets.UTF_8).size
            bytes <= NAME_BYTES
        }
        return String(end.toIntArray(), 0, end.size)
    }

    /** [name] in [dir], numbered ("Title (2).mp4") when a file has it already. */
    private fun twinFree(dir: Path, name: String): Path {
        val first = DownloadHelper.resolveDownloadPath(dir, name)
        if (!first.exists()) return first
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return (2..MAX_TWINS).asSequence()
            .map { DownloadHelper.resolveDownloadPath(dir, "$base ($it)$extension") }
            .firstOrNull { !it.exists() }
            ?: throw IOException("too many files named $name")
    }

    private fun mimeOf(name: String) = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: FALLBACK_MIME

    /** Shows the new file to the gallery, the music players and the file managers at once. */
    private fun announce(context: Context, file: Path) {
        MediaScannerConnection.scanFile(context, arrayOf(file.toString()), null, null)
    }
}
// endregion
