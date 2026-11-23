package com.watube.yard.ui.views

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.LayoutAnimationController
import android.view.animation.PathInterpolator
import android.view.animation.TranslateAnimation
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.watube.yard.R
import com.watube.yard.extensions.dpToPx

// region Downloads
/**
 * The motion of the download sheets, kept quiet: one entrance for the cards when they arrive
 * (a short rise, a few ms apart), a soft pulse on the placeholders while loading, a crossfade
 * between the steps of a playlist. Every animation starts from a visible state, can be cut at
 * any time, and is skipped when the system turns animations off (the final state is set at
 * once then).
 */
object DownloadMotion {
    // exponential ease-out: quick start, long settle
    internal val EASE_OUT = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    internal const val ENTER_MS = 240L
    private const val EXIT_MS = 150L

    /** Delay between two cards, as a share of [ENTER_MS] (about 35 ms). */
    private const val STAGGER = 0.15f
    private const val RISE_DP = 8f
    private const val SHIFT_DP = 20f
    private const val PULSE_MS = 850L
    private const val PULSE_LOW = 0.45f
    private const val PRESSED_SCALE = 0.97f

    private val enabled get() = ValueAnimator.areAnimatorsEnabled()

    /** The cards of [list] rise in one after the other on their next layout. */
    fun staggerIn(list: RecyclerView) {
        if (!enabled) {
            list.layoutAnimation = null
            return
        }
        val rise = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f))
            addAnimation(TranslateAnimation(0f, 0f, RISE_DP.dpToPx().toFloat(), 0f))
            duration = ENTER_MS
            interpolator = EASE_OUT
        }
        list.layoutAnimation = LayoutAnimationController(rise, STAGGER)
        list.scheduleLayoutAnimation()
    }

    /** Placeholders pulse while their content loads; [stopPulse] ends it. */
    fun pulse(view: View) {
        stopPulse(view)
        view.isVisible = true
        if (!enabled) {
            view.alpha = PULSE_LOW + (1 - PULSE_LOW) / 2
            return
        }
        val animator = ValueAnimator.ofFloat(1f, PULSE_LOW).apply {
            duration = PULSE_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { view.alpha = it.animatedValue as Float }
        }
        view.setTag(R.id.watube_download_pulse, animator)
        // a sheet closed while loading must not leave the loop running
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                stopPulse(v)
            }
        })
        animator.start()
    }

    fun stopPulse(view: View) {
        (view.getTag(R.id.watube_download_pulse) as? ValueAnimator)?.cancel()
        view.setTag(R.id.watube_download_pulse, null)
        view.alpha = 1f
    }

    /** [from] fades out, [to] fades in where it stands: a content swap in the same place. */
    fun crossfade(from: View, to: View) = swap(from, to, shift = 0f)

    /**
     * Goes from one step of a sheet to the next ([forward]) or back: the step left drifts
     * the way it goes as it fades, the new one comes from the other side.
     */
    fun step(from: View, to: View, forward: Boolean) =
        swap(from, to, shift = SHIFT_DP.dpToPx().toFloat() * if (forward) 1 else -1)

    /**
     * More options opens or closes [panel]; the chevron of [button] points the way it will go
     * next, and the panel fades in where it appears.
     */
    fun disclose(button: MaterialButton, panel: View) {
        val open = !panel.isVisible
        panel.isVisible = open
        button.setIconResource(if (open) R.drawable.watube_chevron_up else R.drawable.ic_arrow_down)
        panel.animate().cancel()
        if (!open || !enabled) {
            panel.alpha = 1f
            return
        }
        panel.alpha = 0f
        panel.translationY = -RISE_DP.dpToPx().toFloat()
        panel.animate().alpha(1f).translationY(0f).setDuration(ENTER_MS)
            .setInterpolator(EASE_OUT).start()
    }

    /** A chosen card answers the tap with a short give. */
    fun press(view: View) {
        view.animate().cancel()
        if (!enabled) return
        view.scaleX = PRESSED_SCALE
        view.scaleY = PRESSED_SCALE
        view.animate().scaleX(1f).scaleY(1f).setDuration(ENTER_MS).setInterpolator(EASE_OUT).start()
    }

    private fun swap(from: View, to: View, shift: Float) {
        from.animate().cancel()
        to.animate().cancel()
        if (!enabled) {
            from.isVisible = false
            to.isVisible = true
            to.alpha = 1f
            to.translationX = 0f
            return
        }
        from.animate().alpha(0f).translationX(-shift).setDuration(EXIT_MS)
            .setInterpolator(EASE_OUT)
            .withEndAction {
                from.isVisible = false
                from.alpha = 1f
                from.translationX = 0f
                to.alpha = 0f
                to.translationX = shift
                to.isVisible = true
                to.animate().alpha(1f).translationX(0f).setDuration(ENTER_MS)
                    .setInterpolator(EASE_OUT).start()
            }
            .start()
    }
}
// endregion
