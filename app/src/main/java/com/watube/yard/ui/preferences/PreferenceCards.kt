package com.watube.yard.ui.preferences

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.watube.yard.R

// region Settings
/**
 * The rows of each category of a settings page in one card, like the card of the settings hub
 * (fragment_main_settings): rounded, outlined, a hairline between the rows. The rows at both ends
 * are clipped to the rounded corners, so that their touch ripple stays inside the card.
 */
class PreferenceCards(context: Context) : RecyclerView.ItemDecoration() {
    private val gutter = context.resources.getDimensionPixelSize(R.dimen.watube_gutter)
    private val radius = context.resources.getDimension(R.dimen.watube_card_radius)
    private val gapAfterCard = (CARD_GAP_DP * context.resources.displayMetrics.density).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0)
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
        color = MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorOutlineVariant, 0
        )
    }
    private val path = Path()
    private val bounds = RectF()

    /** Whether the item at [position] is a row of a card, rather than a heading or nothing. */
    private fun isRow(parent: RecyclerView, position: Int): Boolean {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return false
        if (position !in 0 until adapter.itemCount) return false
        return adapter.getItem(position) !is PreferenceCategory
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION || !isRow(parent, position)) return
        outRect.left = gutter
        outRect.right = gutter
        val last = !isRow(parent, position + 1)
        if (last) outRect.bottom = gapAfterCard
        clipToCard(view, first = !isRow(parent, position - 1), last = last)
    }

    /** Rounds the corners of the rows at the ends of a card, for their ripple. */
    private fun clipToCard(view: View, first: Boolean, last: Boolean) {
        if (!first && !last) {
            view.clipToOutline = false
            return
        }
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // the square edges reach past the view, so that only the card's corners round
                val top = if (first) 0 else -radius.toInt()
                val bottom = view.height + if (last) 0 else radius.toInt()
                outline.setRoundRect(0, top, view.width, bottom, radius)
            }
        }
        view.clipToOutline = true
        view.invalidateOutline()
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || !isRow(parent, position)) continue
            val top = if (isRow(parent, position - 1)) 0f else radius
            val bottom = if (isRow(parent, position + 1)) 0f else radius

            bounds.set(
                gutter.toFloat(),
                child.top + child.translationY,
                (parent.width - gutter).toFloat(),
                child.bottom + child.translationY
            )
            path.reset()
            path.addRoundRect(
                bounds,
                floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom),
                Path.Direction.CW
            )
            canvas.drawPath(path, fill)
            // each row outlined: between two rows, the shared edge is the hairline of the hub
            canvas.drawPath(path, outline)
        }
    }

    private companion object {
        const val CARD_GAP_DP = 6
    }
}
// endregion
