package com.watube.yard.helpers

import android.content.Context
import com.watube.yard.constants.PreferenceKeys
import kotlin.math.roundToInt

/**
 * "Feed density" (Settings > Appearance) mirrors the mockup's spacing tokens:
 *  - cozy   : --pad 15dp, --cgap 15dp (default)
 *  - compact: --pad 11dp, --cgap 9dp
 *
 * The values are read when a feed is built, so changing the preference takes
 * effect the next time the feed is displayed.
 */
object FeedDensity {
    fun isCompact(): Boolean =
        PreferenceHelper.getString(PreferenceKeys.FEED_DENSITY, "cozy") == "compact"

    /** Gap between the cards of a vertical feed, in dp. */
    fun gapDp(): Int = if (isCompact()) 9 else 15

    /** Horizontal screen gutter, in dp. */
    fun padDp(): Int = if (isCompact()) 11 else 15

    fun gapPx(context: Context): Int = dpToPx(context, gapDp())

    fun padPx(context: Context): Int = dpToPx(context, padDp())

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}
