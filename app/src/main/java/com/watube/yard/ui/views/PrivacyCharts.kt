package com.watube.yard.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.watube.yard.helpers.ThemeHelper

// region Privacy
/** One decelerating draw-in shared by the privacy dashboard charts. */
private const val DRAW_IN_MS = 900L

private fun View.dp(value: Float) = value * resources.displayMetrics.density

private fun View.themeColor(attr: Int) = ThemeHelper.getThemeColor(context, attr)

/**
 * Draws [onFrame] from 0 to 1 once; the system "remove animations" setting makes it instant
 * (ValueAnimator follows the animator duration scale).
 */
private fun drawIn(onFrame: (Float) -> Unit): ValueAnimator =
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = DRAW_IN_MS
        interpolator = DecelerateInterpolator(2f)
        addUpdateListener { onFrame(it.animatedValue as Float) }
        start()
    }

/** A ring filled to a fraction: the share of protections that are on. */
class RingGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val strokeWidth = dp(10f)
    private val bounds = RectF()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@RingGaugeView.strokeWidth
        color = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@RingGaugeView.strokeWidth
        strokeCap = Paint.Cap.ROUND
        color = themeColor(androidx.appcompat.R.attr.colorPrimary)
    }
    private var fraction = 0f
    private var shown = 0f
    private var animator: ValueAnimator? = null

    fun setFraction(value: Float, animate: Boolean) {
        fraction = value.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate) {
            shown = fraction
            invalidate()
            return
        }
        animator = drawIn { progress ->
            shown = fraction * progress
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = strokeWidth / 2
        bounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(bounds, 0f, FULL_TURN, false, track)
        if (shown > 0f) canvas.drawArc(bounds, START_ANGLE, FULL_TURN * shown, false, arc)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * A donut of shares: one slot per category, in a fixed order and color, split by a small
 * gap of the surface so that neighbors stay apart without relying on color alone.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    data class Slice(val value: Float, val color: Int)

    private val strokeWidth = dp(16f)
    private val bounds = RectF()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@DonutChartView.strokeWidth
        color = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    }
    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@DonutChartView.strokeWidth
    }
    private var slices = emptyList<Slice>()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    fun setSlices(values: List<Slice>, animate: Boolean) {
        slices = values
        animator?.cancel()
        if (!animate) {
            progress = 1f
            invalidate()
            return
        }
        animator = drawIn {
            progress = it
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = strokeWidth / 2
        bounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(bounds, 0f, FULL_TURN, false, track)
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return

        val drawn = slices.filter { it.value > 0f }
        val gap = if (drawn.size > 1) SLICE_GAP_DEGREES else 0f
        var start = START_ANGLE
        drawn.forEach { slice ->
            val sweep = FULL_TURN * slice.value / total * progress
            if (sweep > gap) {
                slicePaint.color = slice.color
                canvas.drawArc(bounds, start + gap / 2, sweep - gap, false, slicePaint)
            }
            start += sweep
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Requests over time: a 2dp line over a soft area, faint guides, and the latest value marked.
 * The first draw reveals it from left to right.
 */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val accent = themeColor(androidx.appcompat.R.attr.colorPrimary)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = accent
    }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val guide = Paint().apply {
        strokeWidth = dp(1f)
        color = themeColor(com.google.android.material.R.attr.colorOutlineVariant)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = themeColor(android.R.attr.colorBackground)
    }
    private val linePath = Path()
    private val areaPath = Path()
    private var values = IntArray(0)
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    fun setValues(data: IntArray, animate: Boolean) {
        values = data
        animator?.cancel()
        if (!animate) {
            reveal = 1f
            invalidate()
            return
        }
        animator = drawIn {
            reveal = it
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        area.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            ColorUtils.setAlphaComponent(accent, AREA_TOP_ALPHA),
            ColorUtils.setAlphaComponent(accent, 0),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val top = dp(8f)
        val bottom = height - dp(2f)
        // three faint guides: the chart reads against them, they never compete with it
        for (step in 0..2) {
            val y = top + (bottom - top) * step / 2
            canvas.drawLine(0f, y, width.toFloat(), y, guide)
        }
        if (values.size < 2) return

        val max = values.max().coerceAtLeast(1)
        val stepX = (width - dp(6f)) / (values.size - 1)
        linePath.reset()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = bottom - (bottom - top) * value / max
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        areaPath.set(linePath)
        areaPath.lineTo((values.size - 1) * stepX, bottom)
        areaPath.lineTo(0f, bottom)
        areaPath.close()

        canvas.save()
        canvas.clipRect(0f, 0f, width * reveal, height.toFloat())
        canvas.drawPath(areaPath, area)
        canvas.drawPath(linePath, line)
        canvas.restore()

        if (reveal >= 1f) {
            val endX = (values.size - 1) * stepX
            val endY = bottom - (bottom - top) * values.last() / max
            canvas.drawCircle(endX, endY, dp(5f), dotRing)
            canvas.drawCircle(endX, endY, dp(3.5f), dot)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val SLICE_GAP_DEGREES = 3f
private const val AREA_TOP_ALPHA = 90
// endregion
