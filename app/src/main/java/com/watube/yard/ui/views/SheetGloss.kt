package com.watube.yard.ui.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.google.android.material.shape.MaterialShapeDrawable

// region Navigation
/**
 * The light reflection of Watube's glass on a bottom sheet: the gradient of the mini-player's
 * gloss (watube_miniplayer_background), over the top [glossHeight] pixels of the sheet. Laid
 * over the sheet's own [shape] and under its content, it follows that shape's top corners live:
 * rounded, then squared once the sheet fills the screen.
 */
class SheetGloss(
    private val shape: MaterialShapeDrawable,
    private val glossHeight: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val area = RectF()
    private val radii = FloatArray(CORNER_VALUES)

    override fun onBoundsChange(bounds: Rect) {
        val top = bounds.top.toFloat()
        val bottom = top + minOf(glossHeight, bounds.height())
        paint.shader = LinearGradient(0f, top, 0f, bottom, COLORS, STOPS, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        // the sheet's corners as drawn: their size times the behavior's interpolation
        val left = shape.topLeftCornerResolvedSize * shape.interpolation
        val right = shape.topRightCornerResolvedSize * shape.interpolation
        radii[0] = left
        radii[1] = left
        radii[2] = right
        radii[3] = right
        area.set(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            (bounds.top + minOf(glossHeight, bounds.height())).toFloat()
        )
        path.rewind()
        path.addRoundRect(area, radii, Path.Direction.CW)
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        // top left, top right, bottom right, bottom left: x and y of each
        const val CORNER_VALUES = 8

        // the mini-player's gloss: white 16 %, 4 % at 45 %, then nothing
        val COLORS = intArrayOf(0x29FFFFFF, 0x0AFFFFFF, 0x00FFFFFF)
        val STOPS = floatArrayOf(0f, 0.45f, 1f)
    }
}
// endregion
