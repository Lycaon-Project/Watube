package com.watube.yard.helpers

import android.content.res.Configuration
import com.watube.yard.constants.PreferenceKeys

// region UI Components
/**
 * How many columns the video grids use. The "grid" settings hold a number, or [AUTO] (the
 * default): the grid then follows the window, one column on a phone held upright, and as many
 * cards of a readable width as fit beside the navigation rail anywhere wider (a phone turned
 * sideways, a tablet, a TV).
 */
object GridColumns {
    const val AUTO = "auto"

    /**
     * Material's medium width class: from this window width the navigation rail replaces the
     * bottom bar and the grids leave their single column.
     */
    const val MEDIUM_WIDTH_DP = 600

    /** Room the floating navigation rail takes at the start of the window (rail and its margin). */
    const val RAIL_SPACE_DP = 82

    /**
     * Narrowest card that still shows its thumbnail and a two-line title comfortably. Kept
     * small enough for a phone held sideways to show three cards: wider, its short window
     * held a single row of thumbnails.
     */
    private const val MIN_CARD_WIDTH_DP = 200

    private const val MAX_COLUMNS = 6

    /** The columns for [config], from the setting of its orientation. */
    fun of(config: Configuration): Int {
        val key = if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            PreferenceKeys.GRID_COLUMNS_PORTRAIT
        } else {
            PreferenceKeys.GRID_COLUMNS_LANDSCAPE
        }
        // a number chosen in an older version or restored from a backup is kept; anything
        // else means automatic
        val chosen = PreferenceHelper.getString(key, AUTO).toIntOrNull()
        return chosen?.coerceIn(1, MAX_COLUMNS) ?: forWidth(config.screenWidthDp)
    }

    /** The automatic columns of a window [widthDp] wide. */
    fun forWidth(widthDp: Int): Int {
        if (widthDp < MEDIUM_WIDTH_DP) return 1
        return ((widthDp - RAIL_SPACE_DP) / MIN_CARD_WIDTH_DP).coerceIn(1, MAX_COLUMNS)
    }

    /**
     * Columns of covers seen at once in the library's playlist and bookmark rows (two rows that
     * scroll sideways): two on a phone held upright, then as many covers of a readable width as
     * fit beside the rail. They follow the window only: the "grid" settings are about the video
     * cards.
     */
    fun coversFor(widthDp: Int): Int {
        if (widthDp < MEDIUM_WIDTH_DP) return MIN_COVER_COLUMNS
        return ((widthDp - RAIL_SPACE_DP) / MIN_COVER_WIDTH_DP)
            .coerceIn(MIN_COVER_COLUMNS, MAX_COVER_COLUMNS)
    }

    private const val MIN_COVER_COLUMNS = 2
    private const val MAX_COVER_COLUMNS = 6

    /** Narrowest cover whose title still fits a few words per line. */
    private const val MIN_COVER_WIDTH_DP = 160
}
// endregion
