package com.watube.yard.repo

import com.watube.yard.repo.RawByteStreamDownloadProvider.Companion.BYTES_PER_REQUEST
import com.watube.yard.repo.RawByteStreamDownloadProvider.Companion.isUsableResponse
import com.watube.yard.repo.RawByteStreamDownloadProvider.Companion.rangeHeaderValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// region Downloads
class RawByteStreamDownloadProviderTest {

    @Test
    fun `known size asks for one chunk with an inclusive end`() {
        assertEquals("bytes=0-${BYTES_PER_REQUEST - 1}", rangeHeaderValue(0L, 10 * BYTES_PER_REQUEST))
        assertEquals(
            "bytes=$BYTES_PER_REQUEST-${2 * BYTES_PER_REQUEST - 1}",
            rangeHeaderValue(BYTES_PER_REQUEST, 10 * BYTES_PER_REQUEST)
        )
    }

    @Test
    fun `known size never asks past the last byte`() {
        assertEquals("bytes=0-999", rangeHeaderValue(0L, 1000L))
        assertEquals("bytes=900-999", rangeHeaderValue(900L, 1000L))
        assertEquals("bytes=999-999", rangeHeaderValue(999L, 1000L))
    }

    @Test
    fun `unknown size sends no range for the first request`() {
        assertNull(rangeHeaderValue(0L, -1L))
        assertNull(rangeHeaderValue(0L, 0L))
    }

    @Test
    fun `unknown size resumes with an open ended range`() {
        assertEquals("bytes=4096-", rangeHeaderValue(4096L, -1L))
        assertEquals("bytes=1-", rangeHeaderValue(1L, 0L))
    }

    @Test
    fun `range headers never carry a negative end`() {
        listOf(-1L, 0L, 1L, 1000L, 10 * BYTES_PER_REQUEST).forEach { size ->
            listOf(0L, 1L, 999L).filter { size <= 0L || it < size }.forEach { start ->
                val value = rangeHeaderValue(start, size) ?: return@forEach
                assertTrue(value, Regex("""bytes=\d+-\d*""").matches(value))
            }
        }
    }

    @Test
    fun `a resumed download needs a partial response`() {
        assertTrue(isUsableResponse(200, 0L))
        assertTrue(isUsableResponse(206, 0L))
        assertTrue(isUsableResponse(206, 4096L))
        // the server ignored the range: appending its whole body would corrupt the file
        assertFalse(isUsableResponse(200, 4096L))
    }

    @Test
    fun `error responses are refused`() {
        listOf(403, 404, 416, 500).forEach { assertFalse(isUsableResponse(it, 0L)) }
        assertFalse(isUsableResponse(416, 4096L))
    }
}
// endregion
