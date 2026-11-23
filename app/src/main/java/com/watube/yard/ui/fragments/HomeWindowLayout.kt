package com.watube.yard.ui.fragments

import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.MaterialColors
import com.watube.yard.R
import com.watube.yard.databinding.FragmentHomeBinding
import com.watube.yard.extensions.dpToPx
import com.watube.yard.helpers.GridColumns

// region Home
/** Material's compact height: a phone held sideways. The header then takes a single row. */
private const val COMPACT_HEIGHT_DP = 480

/** Share of the single header row given to the search pill, the chips taking the rest. */
private const val PILL_WEIGHT = 2f
private const val CHIPS_WEIGHT = 3f

/** Gap between the search pill and the chips beside it, and above / below that row. */
private const val COMPACT_GAP_DP = 8f
private const val COMPACT_PILL_PADDING_DP = 8f

/** Height of the full-width hero card for its width (fragment_home.xml: 16:9.6). */
private const val HERO_STACKED_RATIO = 9.6f / 16f

/** Above this share of the window height, the hero puts its picture beside its text. */
private const val HERO_MAX_HEIGHT_SHARE = 0.5f

/** Width of the hero picture beside its text, as a share of the card. */
private const val HERO_THUMB_SHARE = 0.4f

/**
 * Fits the home screen to its window, so a phone turned sideways or a tablet gets a screen
 * made for it, not the upright one stretched:
 * - on a short window (Material's compact height) the search pill and the filter chips share
 *   one row instead of two, still pinned, and leave the height to the videos;
 * - when the full-width hero would take more than half the height, its picture and its text
 *   sit side by side;
 * - the "For you" feed is a grid with the columns of the other video grids ([GridColumns]).
 *
 * MainActivity handles rotations itself (no recreation, so no resource qualifier applies):
 * every change is made here in code, both ways, each time the window changes.
 */
internal class HomeWindowLayout(private val binding: FragmentHomeBinding) {

    private val pillTopMargin = marginsOf(binding.searchPill).topMargin
    private val pillBottomMargin = marginsOf(binding.searchPill).bottomMargin
    private val pillRowPadding = binding.searchPillRow.paddingTop
    private val chipsTopPadding = binding.filterChips.paddingTop
    private val chipsBottomPadding = binding.filterChips.paddingBottom

    private val heroTitleColors = binding.heroTitle.textColors
    private val heroMetaColors = binding.heroMeta.textColors
    private val heroMetaAlpha = binding.heroMeta.alpha

    private val cardStacked = ConstraintSet().apply { clone(binding.heroContainer) }
    private val cardSideBySide = ConstraintSet().apply {
        clone(cardStacked)
        setDimensionRatio(R.id.hero_card, null)
        constrainHeight(R.id.hero_card, ConstraintSet.WRAP_CONTENT)
    }
    private val bodyStacked = ConstraintSet().apply { clone(binding.heroBody) }
    private val bodySideBySide = ConstraintSet().apply {
        clone(bodyStacked)
        clear(R.id.hero_thumb)
        constrainWidth(R.id.hero_thumb, ConstraintSet.MATCH_CONSTRAINT)
        constrainHeight(R.id.hero_thumb, ConstraintSet.MATCH_CONSTRAINT)
        constrainPercentWidth(R.id.hero_thumb, HERO_THUMB_SHARE)
        setDimensionRatio(R.id.hero_thumb, "H,16:9")
        connect(R.id.hero_thumb, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        connect(R.id.hero_thumb, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        // the text sits on the card itself: no shade under it
        setVisibility(R.id.hero_scrim, View.GONE)
        clear(R.id.hero_info)
        constrainWidth(R.id.hero_info, ConstraintSet.MATCH_CONSTRAINT)
        constrainHeight(R.id.hero_info, ConstraintSet.WRAP_CONTENT)
        connect(R.id.hero_info, ConstraintSet.START, R.id.hero_thumb, ConstraintSet.END)
        connect(R.id.hero_info, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        connect(R.id.hero_info, ConstraintSet.TOP, R.id.hero_thumb, ConstraintSet.TOP)
        connect(R.id.hero_info, ConstraintSet.BOTTOM, R.id.hero_thumb, ConstraintSet.BOTTOM)
    }

    private var heroSideBySide: Boolean? = null

    /** Lays the screen out for [config], with [gutter] pixels on both sides of the content. */
    fun apply(config: Configuration, gutter: Int) {
        placeHeader(config.screenHeightDp < COMPACT_HEIGHT_DP, gutter)
        placeHero(heroIsTooTall(config))
        placeFeed(GridColumns.of(config))
    }

    private fun placeHeader(compact: Boolean, gutter: Int) {
        val gap = COMPACT_GAP_DP.dpToPx()
        binding.homeHeader.orientation =
            if (compact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        binding.homeHeader.gravity = if (compact) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
        binding.searchPill.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (compact) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            weight = if (compact) PILL_WEIGHT else 0f
            marginStart = gutter
            // beside the chips, their own start padding makes the gap
            marginEnd = if (compact) 0 else gutter
            topMargin = if (compact) gap / 2 else pillTopMargin
            bottomMargin = if (compact) gap / 2 else pillBottomMargin
        }
        val rowPadding = if (compact) COMPACT_PILL_PADDING_DP.dpToPx() else pillRowPadding
        binding.searchPillRow.updatePadding(top = rowPadding, bottom = rowPadding)
        binding.filterScroll.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (compact) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            weight = if (compact) CHIPS_WEIGHT else 0f
        }
        binding.filterChips.setPaddingRelative(
            if (compact) gap else gutter,
            if (compact) 0 else chipsTopPadding,
            gutter,
            if (compact) 0 else chipsBottomPadding
        )
        // the chips scroll on under the end of the row: a fade tells there are more
        binding.filterScroll.isHorizontalFadingEdgeEnabled = compact
    }

    /** True when the full-width hero would take more than half of [config]'s height. */
    private fun heroIsTooTall(config: Configuration): Boolean {
        val railSpace = if (config.screenWidthDp >= GridColumns.MEDIUM_WIDTH_DP) {
            GridColumns.RAIL_SPACE_DP
        } else {
            0
        }
        val stackedHeight = (config.screenWidthDp - railSpace) * HERO_STACKED_RATIO
        return stackedHeight > config.screenHeightDp * HERO_MAX_HEIGHT_SHARE
    }

    private fun placeHero(sideBySide: Boolean) {
        if (heroSideBySide == sideBySide) return
        heroSideBySide = sideBySide
        (if (sideBySide) cardSideBySide else cardStacked).applyTo(binding.heroContainer)
        binding.heroBody.updateLayoutParams<ViewGroup.LayoutParams> {
            height = if (sideBySide) {
                ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        (if (sideBySide) bodySideBySide else bodyStacked).applyTo(binding.heroBody)
        if (sideBySide) {
            val view = binding.heroBody
            binding.heroTitle.setTextColor(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
            )
            binding.heroMeta.setTextColor(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            binding.heroMeta.alpha = 1f
        } else {
            // white over the picture and its shade
            binding.heroTitle.setTextColor(heroTitleColors)
            binding.heroMeta.setTextColor(heroMetaColors)
            binding.heroMeta.alpha = heroMetaAlpha
        }
    }

    private fun placeFeed(columns: Int) {
        val grid = binding.trendingRV.layoutManager as? GridLayoutManager
        if (grid == null) {
            binding.trendingRV.layoutManager = GridLayoutManager(binding.root.context, columns)
        } else if (grid.spanCount != columns) {
            grid.spanCount = columns
        }
    }

    private fun marginsOf(view: View) = view.layoutParams as ViewGroup.MarginLayoutParams
}
// endregion
