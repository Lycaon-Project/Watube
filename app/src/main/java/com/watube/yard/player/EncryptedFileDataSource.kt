package com.watube.yard.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import com.watube.yard.helpers.DownloadEncryption
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.security.GeneralSecurityException

// region Downloads
/**
 * Plays a downloaded file straight from its encrypted form (see [DownloadEncryption]): the clear
 * content is decrypted segment by segment as the player reads it, and never written anywhere.
 */
@UnstableApi
class EncryptedFileDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var channel: SeekableByteChannel? = null
    private var uri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val path = dataSpec.uri.path
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val channel = try {
            DownloadEncryption.openSeekable(Paths.get(path))
        } catch (e: IOException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        } catch (e: GeneralSecurityException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        this.channel = channel

        val size = channel.size()
        if (dataSpec.position > size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        channel.position(dataSpec.position)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            size - dataSpec.position
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val channel = channel ?: return C.RESULT_END_OF_INPUT

        val wanted = minOf(length.toLong(), bytesRemaining).toInt()
        val read = try {
            DownloadEncryption.readSome(channel, ByteBuffer.wrap(buffer, offset, wanted))
        } catch (e: IOException) {
            // a segment that does not authenticate: the file was altered or damaged
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (read < 0) return C.RESULT_END_OF_INPUT

        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            channel?.close()
        } finally {
            channel = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedFileDataSource()
    }

    companion object {
        /** The source a downloaded file is read with: an older clear file is read as is. */
        fun factoryFor(path: Path): DataSource.Factory =
            if (DownloadEncryption.isEncrypted(path)) Factory() else FileDataSource.Factory()
    }
}
// endregion
