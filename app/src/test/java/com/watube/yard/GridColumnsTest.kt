package com.watube.yard

import com.watube.yard.helpers.GridColumns
import org.junit.Assert.assertEquals
import org.junit.Test

// region UI Components
class GridColumnsTest {

    @Test
    fun `a phone held upright keeps a single column`() {
        assertEquals(1, GridColumns.forWidth(360))
        assertEquals(1, GridColumns.forWidth(599))
    }

    @Test
    fun `a phone turned sideways shows three cards beside the rail`() {
        // 2400 x 1080 at 480 dpi, three-button navigation on the side: 719 x 323 dp
        assertEquals(3, GridColumns.forWidth(719))
    }

    @Test
    fun `a medium window leaves the single column`() {
        assertEquals(2, GridColumns.forWidth(GridColumns.MEDIUM_WIDTH_DP))
    }

    @Test
    fun `a tablet gets more columns as it widens`() {
        assertEquals(3, GridColumns.forWidth(800))
        assertEquals(5, GridColumns.forWidth(1280))
    }

    @Test
    fun `a very wide window stops at six columns`() {
        assertEquals(6, GridColumns.forWidth(4000))
    }

    @Test
    fun `playlist covers sit two abreast on a phone held upright`() {
        assertEquals(2, GridColumns.coversFor(360))
        assertEquals(2, GridColumns.coversFor(411))
    }

    @Test
    fun `playlist covers widen with the window and stop at six`() {
        assertEquals(3, GridColumns.coversFor(719))
        assertEquals(6, GridColumns.coversFor(1280))
        assertEquals(6, GridColumns.coversFor(4000))
    }
}
// endregion
