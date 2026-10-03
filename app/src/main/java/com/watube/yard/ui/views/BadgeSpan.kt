package com.watube.yard.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.ReplacementSpan
import com.watube.yard.R
import kotlin.math.ceil

/**
 * Draws its text as a small rounded pill (e.g. the HD / 2K / 4K quality marker), vertically
 * centred on the surrounding line.
 */
class BadgeSpan(context: Context) : ReplacementSpan() {
    private val density = context.resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.watube_quality_badge)
    }
    private val textColor = context.getColor(R.color.watube_quality_badge_text)
    private val horizontalPadding = 5f * density
    private val verticalPadding = 1.5f * density
    private val marginStart = 6f * density
    private val rect = RectF()

    private fun badgePaint(paint: Paint) = TextPaint(paint).apply {
        textSize = paint.textSize * TEXT_SCALE
        typeface = Typeface.create(paint.typeface, Typeface.BOLD)
        color = textColor
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = ceil(
        marginStart + badgePaint(paint).measureText(text, start, end) + 2 * horizontalPadding
    ).toInt()

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val badgePaint = badgePaint(paint)
        val metrics = badgePaint.fontMetrics
        // centre of the regular text of this line, then the badge baseline around it
        val lineCenter = y + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
        val baseline = lineCenter - (metrics.ascent + metrics.descent) / 2

        val left = x + marginStart
        rect.set(
            left,
            baseline + metrics.ascent - verticalPadding,
            left + badgePaint.measureText(text, start, end) + 2 * horizontalPadding,
            baseline + metrics.descent + verticalPadding
        )
        val radius = rect.height() / 2
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        canvas.drawText(text, start, end, left + horizontalPadding, baseline, badgePaint)
    }

    private companion object {
        const val TEXT_SCALE = 0.68f
    }
}
