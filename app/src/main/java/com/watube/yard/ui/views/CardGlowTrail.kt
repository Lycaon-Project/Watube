package com.watube.yard.ui.views

import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.atan2

// region About
/**
 * A light that runs round the edge of a rounded card (the About header), its tail fading into
 * neighbouring hues of [accent], with a soft halo under the line. Laps forever while started;
 * [start] does nothing when animations are off or power saving is on.
 *
 * Rebuilt from the 26.12 build after the emergency restore.
 */
class CardGlowTrail(
    density: Float,
    @ColorInt accent: Int,
    private val cornerRadius: Float
) : Drawable() {
    private val lineWidth = LINE_DP * density

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = HALO_DP * density
        maskFilter = BlurMaskFilter(HALO_BLUR_DP * density, BlurMaskFilter.Blur.NORMAL)
    }

    /** Clear for the first half of the turn, then the tail, brightening up to the head. */
    private val trail = intArrayOf(
        0,
        0,
        withHue(accent, -TAIL_HUE_SHIFT, 0),
        withHue(accent, -TAIL_HUE_SHIFT / 2, TAIL_ALPHA),
        accent or OPAQUE,
        0
    )
    private val stops = floatArrayOf(0f, 0.5f, 0.6f, 0.86f, HEAD_AT, 1f)

    private val edge = Path()
    private val measure = PathMeasure()
    private var edgeLength = 0f
    private val head = FloatArray(2)
    private val turn = Matrix()
    private var centerX = 0f
    private var centerY = 0f
    private var sweep: SweepGradient? = null

    /** Where the head is on the edge, from 0 to 1, or null while stopped. */
    private var lap: Float? = null
    private var startedAt = 0L
    private var alphaShare = 1f

    private val run = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = LAP_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            lap = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun start(powerSave: Boolean) {
        if (run.isStarted || powerSave || !ValueAnimator.areAnimatorsEnabled()) return
        startedAt = SystemClock.uptimeMillis()
        run.start()
    }

    fun stop() {
        run.cancel()
        lap = null
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        // the line is drawn on the card's edge, not half outside it
        val inset = lineWidth / 2
        val area = RectF(bounds).apply { inset(inset, inset) }
        val radius = (cornerRadius - inset).coerceAtLeast(0f)
        edge.reset()
        edge.addRoundRect(area, radius, radius, Path.Direction.CW)
        measure.setPath(edge, true)
        edgeLength = measure.length
        centerX = area.centerX()
        centerY = area.centerY()
        sweep = SweepGradient(centerX, centerY, trail, stops).also {
            line.shader = it
            halo.shader = it
        }
    }

    override fun draw(canvas: Canvas) {
        val at = lap ?: return
        val shader = sweep ?: return
        if (edgeLength <= 0f) return

        // The head follows the edge at an even speed, so the sweep is turned to wherever it is
        // rather than at an even angle, which would rush along the card's short sides.
        measure.getPosTan(edgeLength * at, head, null)
        val angle = Math.toDegrees(atan2(head[1] - centerY, head[0] - centerX).toDouble())
        turn.setRotate(angle.toFloat() - FULL_TURN * HEAD_AT, centerX, centerY)
        shader.setLocalMatrix(turn)

        val fade = ((SystemClock.uptimeMillis() - startedAt) / FADE_IN_MS)
            .coerceIn(0f, 1f) * alphaShare
        halo.alpha = (HALO_ALPHA * fade).toInt()
        line.alpha = (LINE_ALPHA * fade).toInt()
        canvas.drawPath(edge, halo)
        canvas.drawPath(edge, line)
    }

    override fun setAlpha(alpha: Int) {
        alphaShare = alpha / OPAQUE_ALPHA
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        line.colorFilter = colorFilter
        halo.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val LAP_MS = 6400L
        const val FADE_IN_MS = 900f
        const val FULL_TURN = 360f

        /** Where the head sits in the sweep, just before its end. */
        const val HEAD_AT = 0.985f
        const val LINE_DP = 1.5f
        const val HALO_DP = 9f
        const val HALO_BLUR_DP = 7f
        const val LINE_ALPHA = 200
        const val HALO_ALPHA = 70
        const val TAIL_ALPHA = 110
        const val TAIL_HUE_SHIFT = 50f
        const val OPAQUE = 0xFF000000.toInt()
        const val OPAQUE_ALPHA = 255f

        @ColorInt
        fun withHue(@ColorInt color: Int, shift: Float, alpha: Int): Int {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            hsl[0] = (hsl[0] + shift + FULL_TURN) % FULL_TURN
            return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), alpha)
        }
    }
}
// endregion
