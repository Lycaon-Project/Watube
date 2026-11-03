package com.watube.yard.cast

import com.watube.yard.cast.proto.CastMessage
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

// region Stolm
/**
 * Cast v2 framing: every [CastMessage] travels as its size (4 bytes, big endian) followed by
 * its protobuf bytes.
 */
internal object CastFraming {
    /** Larger frames are refused: receivers never send that much, and it bounds memory use. */
    const val MAX_MESSAGE_SIZE = 64 * 1024

    private const val HEADER_SIZE = 4

    fun write(message: CastMessage, output: OutputStream) {
        val bytes = message.toByteArray()
        if (bytes.size > MAX_MESSAGE_SIZE) throw IOException("Cast message of ${bytes.size} bytes refused")
        output.write(ByteBuffer.allocate(HEADER_SIZE).putInt(bytes.size).array())
        output.write(bytes)
        output.flush()
    }

    /** Blocks until a whole message arrived; [IOException] on a closed stream or a bad frame. */
    fun read(input: InputStream): CastMessage {
        val data = DataInputStream(input)
        val size = data.readInt()
        if (size !in 0..MAX_MESSAGE_SIZE) throw IOException("Cast frame of $size bytes refused")
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return CastMessage.parseFrom(bytes)
    }
}
// endregion
