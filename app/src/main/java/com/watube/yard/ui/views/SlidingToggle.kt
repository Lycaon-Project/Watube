package com.watube.yard.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.abs

// region Downloads
/**
 * The Video / Audio switch of the download sheets: an accent [pill], laid behind the two
 * buttons of [group] in the same parent, slides to the chosen button (quick start, soft settle)
 * while the text and icon colours of both buttons blend. A new choice during the slide starts
 * from where the pill is; with the system animations off, the pill is set at once.
 * The pill can also be dragged from one side to the other (see [PillDrag]).
 */
class SlidingToggle(private val group: MaterialButtonToggleGroup, private val pill: View) {
    private val first = checkNotNull(group.getChildAt(0) as? MaterialButton) { "two buttons" }
    private val second = checkNotNull(group.getChildAt(1) as? MaterialButton) { "two buttons" }

    // the buttons' own selector, given back at rest (it keeps the disabled state)
    private val textColors: ColorStateList = first.textColors
    private val chosenColor = textColors.getColorForState(CHOSEN, textColors.defaultColor)
    private val idleColor = textColors.getColorForState(IDLE, textColors.defaultColor)

    /** 0 on the first button, 1 on the second. */
    private var position = targetOf(group.checkedButtonId)
    private var slide: ValueAnimator? = null

    init {
        group.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pill.post(::fitPill) }
        pill.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
        group.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) slideTo(targetOf(id))
        }
        val drag = PillDrag()
        first.setOnTouchListener(drag)
        second.setOnTouchListener(drag)
    }

    private fun targetOf(checkedId: Int) = if (checkedId == second.id) 1f else 0f

    /** The pill takes the size of a button: they share the group's width equally. */
    private fun fitPill() {
        val params = pill.layoutParams
        if (first.width == 0 || (params.width == first.width && params.height == first.height)) return
        params.width = first.width
        params.height = first.height
        pill.layoutParams = params
    }

    private fun place() {
        // the pill's own left differs between left-to-right and right-to-left layouts
        val start = group.left + first.left - pill.left
        val end = group.left + second.left - pill.left
        pill.translationX = start + (end - start) * position
    }

    private fun slideTo(target: Float) {
        val running = slide
        slide = null
        running?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled() || !pill.isLaidOut || position == target) {
            position = target
            place()
            rest()
            return
        }
        slide = ValueAnimator.ofFloat(position, target).apply {
            // shorter when a new choice comes halfway
            duration = (DownloadMotion.ENTER_MS * abs(target - position)).toLong()
            interpolator = DownloadMotion.EASE_OUT
            addUpdateListener { blend(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // a slide cut by a newer one leaves the colours to it
                    if (slide !== animation) return
                    slide = null
                    rest()
                }
            })
            start()
        }
    }

    private fun blend(value: Float) {
        position = value
        place()
        tint(first, ColorUtils.blendARGB(chosenColor, idleColor, value))
        tint(second, ColorUtils.blendARGB(idleColor, chosenColor, value))
    }

    private fun tint(button: MaterialButton, color: Int) {
        val colors = ColorStateList.valueOf(color)
        button.setTextColor(colors)
        button.iconTint = colors
    }

    private fun rest() {
        first.setTextColor(textColors)
        first.iconTint = textColors
        second.setTextColor(textColors)
        second.iconTint = textColors
    }

    /**
     * Dragging across the switch carries the pill under the finger, colours blending as it goes;
     * let go, it settles on the nearer button, which is then chosen as if tapped. A touch that
     * does not travel past the system's touch slop stays an ordinary tap, and the buttons keep
     * their click for screen readers, switches and keyboards.
     */
    private inner class PillDrag : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(group.context).scaledTouchSlop
        private var downX = 0f
        private var from = 0f
        private var isDragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    isDragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val travel = event.rawX - downX
                    if (!isDragging && (abs(travel) < touchSlop || !group.isEnabled)) return false
                    if (!isDragging) startDrag(view, event)
                    follow(event.rawX - downX)
                    return true
                }

                MotionEvent.ACTION_UP -> if (isDragging) {
                    isDragging = false
                    val chosen = if (position >= HALF) second else first
                    when {
                        !chosen.isEnabled || chosen.isChecked -> slideTo(targetOf(group.checkedButtonId))
                        else -> chosen.performClick()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> if (isDragging) {
                    isDragging = false
                    slideTo(targetOf(group.checkedButtonId))
                    return true
                }
            }
            return false
        }

        private fun startDrag(view: View, event: MotionEvent) {
            isDragging = true
            // counted from where the finger crossed the slop, not from this event: while the
            // screen is busy, moves arrive merged and this one can already be far along
            downX += if (event.rawX > downX) touchSlop else -touchSlop
            val running = slide
            slide = null
            running?.cancel()
            from = position
            // the sheet and its scroll leave this horizontal drag alone
            view.parent?.requestDisallowInterceptTouchEvent(true)
            // the button under the finger lets go of its press: this touch is not a tap
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            view.onTouchEvent(cancel)
            cancel.recycle()
        }

        private fun follow(travel: Float) {
            // negative in right-to-left layouts, where the second button sits on the left
            val span = (second.left - first.left).toFloat()
            if (span == 0f) return
            val before = position
            blend((from + travel / span).coerceIn(0f, 1f))
            // a light tick when the pill crosses the middle (follows the system haptics setting)
            if ((before < HALF) != (position < HALF)) {
                group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private companion object {
        val CHOSEN = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        val IDLE = intArrayOf(android.R.attr.state_enabled)

        /** Past the middle, a released pill settles on the second button. */
        const val HALF = 0.5f
    }
}
// endregion
