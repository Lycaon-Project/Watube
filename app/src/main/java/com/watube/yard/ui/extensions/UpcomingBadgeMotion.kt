package com.watube.yard.ui.extensions

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.core.animation.doOnEnd
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import com.watube.yard.R
import com.watube.yard.helpers.ThemeHelper

// region UI Components
/*
 * The beat of an upcoming pill: its clock's hand turns one round while a glint crosses it.
 * Rebuilt from the 26.12 build after the emergency restore.
 */

private const val GLINT_DURATION_MS = 1100L
private const val GLINT_ALPHA_DARK = 0.3f
private const val GLINT_ALPHA_LIGHT = 0.6f

/** How many videos are remembered as already introduced; the oldest is forgotten first. */
private const val INTRODUCED_MEMORY = 256

/**
 * Plays the beat of one pill and owns its glint, kept as a tag of the pill ([of], [existing]).
 * The beat stops when the pill leaves the window, so a recycled row never animates off screen.
 */
class UpcomingBadgeMotion private constructor(
    private val badge: TextView
) : View.OnAttachStateChangeListener {
    private val glint = UpcomingGlintDrawable(glintColor(badge))
    private var glintAnimator: ValueAnimator? = null

    init {
        badge.foreground = glint
        badge.addOnAttachStateChangeListener(this)
    }

    override fun onViewAttachedToWindow(v: View) = Unit

    override fun onViewDetachedFromWindow(v: View) = stopBeat()

    fun beat() {
        if (!canAnimate(badge)) return
        clock()?.let {
            it.reset()
            it.start()
        }
        glintAnimator?.cancel()
        glintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLINT_DURATION_MS
            interpolator = AnimationUtils.loadInterpolator(
                badge.context,
                R.interpolator.watube_standard
            )
            addUpdateListener { glint.progress = it.animatedValue as Float }
            doOnEnd { glint.progress = UpcomingGlintDrawable.HIDDEN }
            start()
        }
    }

    /** Stops the beat for good: the pill no longer shows. */
    fun release() {
        stopBeat()
        badge.removeOnAttachStateChangeListener(this)
        badge.foreground = null
        badge.setTag(R.id.upcoming_badge_motion, null)
    }

    fun stopBeat() {
        glintAnimator?.cancel()
        glintAnimator = null
        glint.progress = UpcomingGlintDrawable.HIDDEN
        clock()?.let {
            it.stop()
            it.reset()
        }
    }

    private fun clock(): AnimatedVectorDrawable? =
        badge.compoundDrawablesRelative[0] as? AnimatedVectorDrawable

    companion object {
        /** Videos whose pill already came in, so scrolling back does not replay it. */
        private val introduced = LinkedHashSet<String>()

        /** True the first time [videoId] is seen since the app started. */
        @MainThread
        fun firstTime(videoId: String): Boolean {
            if (!introduced.add(videoId)) return false
            if (introduced.size > INTRODUCED_MEMORY) introduced.remove(introduced.first())
            return true
        }

        fun of(badge: TextView): UpcomingBadgeMotion =
            existing(badge) ?: UpcomingBadgeMotion(badge).also {
                badge.setTag(R.id.upcoming_badge_motion, it)
            }

        fun existing(badge: TextView): UpcomingBadgeMotion? =
            badge.getTag(R.id.upcoming_badge_motion) as? UpcomingBadgeMotion

        /** Animations are on in the system settings and power saving is off. */
        fun canAnimate(view: View): Boolean =
            ValueAnimator.areAnimatorsEnabled() &&
                view.context.getSystemService<PowerManager>()?.isPowerSaveMode != true

        @ColorInt
        private fun glintColor(view: View): Int {
            val alpha = if (ThemeHelper.isDarkMode(view.context)) {
                GLINT_ALPHA_DARK
            } else {
                GLINT_ALPHA_LIGHT
            }
            return ColorUtils.setAlphaComponent(Color.WHITE, (alpha * 255).toInt())
        }
    }
}

/**
 * A slanted band of light that crosses a pill from start to end as [progress] goes from 0 to 1,
 * clipped to the pill's round ends. Draws nothing while [progress] is [HIDDEN].
 */
class UpcomingGlintDrawable(@ColorInt private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Path()
    private val shift = Matrix()
    private var bandWidth = 0f

    var progress = HIDDEN
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        val height = bounds.height().toFloat()
        val radius = height / 2f
        outline.reset()
        outline.addRoundRect(RectF(bounds), radius, radius, Path.Direction.CW)
        bandWidth = height * BAND_TO_HEIGHT
        val clear = ColorUtils.setAlphaComponent(color, 0)
        paint.shader = LinearGradient(
            0f,
            0f,
            bandWidth,
            height * SLANT_TO_HEIGHT,
            intArrayOf(clear, color, clear),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val shader = paint.shader ?: return
        if (progress < 0f || bounds.isEmpty) return
        val travel = bounds.width() + bandWidth
        shift.setTranslate(bounds.left - bandWidth + travel * progress, bounds.top.toFloat())
        shader.setLocalMatrix(shift)
        val saved = canvas.save()
        canvas.clipPath(outline)
        canvas.drawRect(bounds, paint)
        canvas.restoreToCount(saved)
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

    companion object {
        const val HIDDEN = -1f
        private const val BAND_TO_HEIGHT = 1.6f
        private const val SLANT_TO_HEIGHT = 0.5f
    }
}
// endregion
