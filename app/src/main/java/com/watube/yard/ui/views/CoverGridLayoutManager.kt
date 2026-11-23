package com.watube.yard.ui.views

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

// region Playlists
/**
 * Two rows of playlist covers that scroll sideways: the first cover at the top left, the next
 * one under it, then on to the right column by column. Exactly [visibleColumns] covers fit
 * across the list, so the margins on both sides stay equal; the next column waits past the
 * edge, a swipe away.
 */
class CoverGridLayoutManager(
    context: Context,
    private val visibleColumns: Int
) : GridLayoutManager(context, ROWS, HORIZONTAL, false) {

    /**
     * A horizontal grid has no width to give its children, so each cover gets its share of the
     * list's width here, as it is laid out.
     */
    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean {
        val space = width - paddingStart - paddingEnd
        if (lp != null && space > 0) {
            lp.width = space / visibleColumns
        }
        return super.checkLayoutParams(lp)
    }

    private companion object {
        const val ROWS = 2
    }
}
// endregion
