package com.watube.yard.extensions

import java.io.ByteArrayOutputStream
import java.io.InputStream

// region Common Utilities
private const val READ_BUFFER_BYTES = 8 * 1024

/**
 * At most [limit] bytes of the stream, read until its end or that limit: what
 * InputStream.readNBytes does, which Android only has from version 13.
 */
fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    while (output.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
// endregion
