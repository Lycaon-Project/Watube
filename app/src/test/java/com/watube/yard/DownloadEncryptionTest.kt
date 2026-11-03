package com.watube.yard

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.watube.yard.helpers.DownloadEncryption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.random.Random

/**
 * What playing an encrypted download relies on (DownloadEncryption): the format the app uses
 * (Tink AES256_GCM_HKDF_1MB), with a test key instead of the phone's Keystore.
 */
class DownloadEncryptionTest {
    // a little over three segments, so that reads cross segment boundaries
    private val clear = Random(42).nextBytes(3 * 1024 * 1024 + 12_345)
    private val aead: StreamingAead = run {
        StreamingAeadConfig.register()
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    private fun encrypted(name: String): Path {
        val file = Files.createTempFile("download", ".enc")
        aead.newEncryptingStream(Files.newOutputStream(file), name.toByteArray()).use { it.write(clear) }
        return file
    }

    /** Opened the way the app opens a download for playback. */
    private fun open(file: Path, name: String): SeekableByteChannel =
        DownloadEncryption.seekable(aead, Files.newByteChannel(file), name.toByteArray())

    @Test
    fun anyPositionReadsTheClearContent() {
        val file = encrypted("abc.mp4")
        open(file, "abc.mp4").use { channel ->
            assertEquals(clear.size.toLong(), channel.size())
            for (position in listOf(0L, 1L, 1024L * 1024 - 3, 2L * 1024 * 1024 + 7, clear.size - 10L)) {
                channel.position(position)
                val buffer = ByteBuffer.allocate(4096)
                val read = DownloadEncryption.readSome(channel, buffer)
                assertTrue(read > 0)
                val expected = clear.copyOfRange(position.toInt(), position.toInt() + read)
                assertArrayEquals("at $position", expected, buffer.array().copyOf(read))
            }
            channel.position(clear.size.toLong())
            assertEquals(-1, DownloadEncryption.readSome(channel, ByteBuffer.allocate(16)))
        }
        Files.delete(file)
    }

    @Test
    fun readingFromStartToEndGivesTheWholeFile() {
        val file = encrypted("abc.mp4")
        val all = ByteArrayOutputStream()
        aead.newDecryptingStream(Files.newInputStream(file), "abc.mp4".toByteArray())
            .use { it.copyTo(all) }
        assertArrayEquals(clear, all.toByteArray())
        Files.delete(file)
    }

    @Test(expected = IOException::class)
    fun aFileUnderAnotherNameIsRefused() {
        val file = encrypted("abc.mp4")
        try {
            // refused on opening already: the first byte is read then
            open(file, "other.mp4").close()
        } finally {
            Files.delete(file)
        }
    }

    @Test
    fun aRefusedFileIsNotLeftOpen() {
        val file = encrypted("abc.mp4")
        val ciphertext = Files.newByteChannel(file)
        val refused = runCatching {
            DownloadEncryption.seekable(aead, ciphertext, "other.mp4".toByteArray())
        }
        assertTrue(refused.isFailure)
        assertFalse(ciphertext.isOpen)
        Files.delete(file)
    }

    @Test(expected = IOException::class)
    fun anAlteredSegmentIsRefused() {
        val file = encrypted("abc.mp4")
        val bytes = Files.readAllBytes(file)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        Files.write(file, bytes)
        try {
            open(file, "abc.mp4").use { channel ->
                channel.position(clear.size / 2L)
                channel.read(ByteBuffer.allocate(4096))
            }
        } finally {
            Files.delete(file)
        }
    }

    @Test
    fun encryptedFilesAreNamedAfterTheClearOnes() {
        val clearPath = Path("/downloads/video/abc.mp4")
        val encryptedPath = DownloadEncryption.encryptedPathOf(clearPath)
        assertEquals(Path("/downloads/video/abc.mp4.enc"), encryptedPath)
        assertTrue(DownloadEncryption.isEncrypted(encryptedPath))
        assertFalse(DownloadEncryption.isEncrypted(clearPath))
    }

    @Test
    fun anEncryptedFileIsBiggerThanTheClearOne() {
        // DownloadItem.isFinished compares the stored size with the expected clear size
        val file = encrypted("abc.mp4")
        assertTrue(Files.size(file) > clear.size)
        Files.delete(file)
    }
}
