package com.watube.yard.helpers

import android.media.MediaDataSource
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.watube.yard.WatubeApp
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream

// region Downloads
/**
 * Downloads are stored encrypted: videos, audio tracks, subtitles and thumbnails can't be read
 * from the phone's storage (USB, file manager, another app), only through Watube.
 *
 * Tink streaming AEAD (AES-256-GCM, HKDF per file, 1 MiB segments): each file has its own key,
 * every segment is authenticated, and any position can be read without decrypting what comes
 * before, so playback and seeking work directly on the encrypted file. The file name is bound
 * to its content, two files can't be swapped. The keyset is wrapped by a key that never leaves
 * the Android Keystore.
 *
 * An encrypted file is named after the clear one, with [SUFFIX].
 */
object DownloadEncryption {
    const val SUFFIX = ".enc"

    private const val KEYSET_NAME = "download_keyset"
    private const val KEYSET_PREFS = "download_keyset"
    private const val MASTER_KEY_URI = "android-keystore://com.watube.yard.downloads"
    private const val KEY_TEMPLATE = "AES256_GCM_HKDF_1MB"
    private const val COPY_BUFFER = 64 * 1024

    private val streamingAead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(WatubeApp.instance, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    fun isEncrypted(path: Path): Boolean = path.name.endsWith(SUFFIX)

    /** Where the encrypted copy of the clear file [path] goes: next to it. */
    fun encryptedPathOf(path: Path): Path = path.resolveSibling(path.name + SUFFIX)

    /** The original file name: bound to the content, so that files can't be swapped. */
    private fun associatedData(path: Path): ByteArray =
        path.name.removeSuffix(SUFFIX).toByteArray(Charsets.UTF_8)

    /**
     * Encrypts the clear file [source] into [target] (written aside, then moved in place: an
     * interrupted copy never leaves a truncated file under the final name). [source] is kept.
     */
    fun encryptFile(source: Path, target: Path) {
        write(target) { output -> source.inputStream().use { it.copyTo(output, COPY_BUFFER) } }
    }

    fun encryptBytes(bytes: ByteArray, target: Path) {
        write(target) { it.write(bytes) }
    }

    private fun write(target: Path, content: (OutputStream) -> Unit) {
        val partial = target.resolveSibling(target.name + ".part")
        try {
            streamingAead.newEncryptingStream(partial.outputStream(), associatedData(target))
                .use(content)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.deleteIfExists()
        }
    }

    /** The whole clear content of a small file (thumbnails), encrypted or written before. */
    fun readBytes(path: Path): ByteArray {
        if (!isEncrypted(path)) return Files.readAllBytes(path)
        return streamingAead.newDecryptingStream(path.inputStream(), associatedData(path))
            .use { it.readBytes() }
    }

    /** Writes the clear content of [path] to [output] (export of a download). */
    fun decryptTo(path: Path, output: OutputStream) {
        val input = if (isEncrypted(path)) {
            streamingAead.newDecryptingStream(path.inputStream(), associatedData(path))
        } else {
            path.inputStream()
        }
        input.use { it.copyTo(output, COPY_BUFFER) }
    }

    /** Random access to the clear content: position and size are those of the clear file. */
    fun openSeekable(path: Path): SeekableByteChannel =
        seekable(streamingAead, Files.newByteChannel(path), associatedData(path))

    /**
     * Tink only picks the key of a file on its first read, and can't tell the size before:
     * one byte is read first (which also checks the file), then the position goes back to 0.
     */
    internal fun seekable(
        aead: StreamingAead,
        ciphertext: SeekableByteChannel,
        associatedData: ByteArray
    ): SeekableByteChannel {
        var channel: SeekableByteChannel? = null
        try {
            channel = aead.newSeekableDecryptingChannel(ciphertext, associatedData)
            channel.read(ByteBuffer.allocate(1))
            channel.position(0)
            return channel
        } catch (e: Exception) {
            // whatever refused the file, the file itself is never left open
            channel?.close()
            ciphertext.close()
            throw e
        }
    }

    /** Reads at least one byte into [buffer], or returns -1 at the end of the content. */
    fun readSome(channel: SeekableByteChannel, buffer: ByteBuffer): Int {
        while (true) {
            val read = channel.read(buffer)
            if (read != 0 || !buffer.hasRemaining()) return read
        }
    }

    /** For MediaMetadataRetriever (seekbar previews of a downloaded video). */
    class DecryptingMediaDataSource(path: Path) : MediaDataSource() {
        private val channel = openSeekable(path)

        @Synchronized
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= channel.size()) return -1
            if (size == 0) return 0
            channel.position(position)
            return readSome(channel, ByteBuffer.wrap(buffer, offset, size))
        }

        @Synchronized
        override fun getSize(): Long = channel.size()

        @Synchronized
        override fun close() = channel.close()
    }
}
// endregion
