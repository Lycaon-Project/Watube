package com.watube.yard.ui.extensions

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs
import kotlin.math.roundToInt

// region UI Components
/**
 * Lets the selected tab's pill be dragged along the tabs, the pages following under it: the
 * drag moves [pager] (a fake drag), and the TabLayoutMediator already moves the pill with the
 * pages. Let go, the pill settles on the tab it is nearer to, the pages with it (at once with
 * the system animations off). A touch that does not travel past the system's touch slop stays an ordinary tap
 * on a tab, so screen readers, switches and keyboards keep choosing tabs as before.
 *
 * Call it once the mediator is attached, so the tabs exist.
 */
fun TabLayout.dragPillWith(pager: ViewPager2) {
    val drag = TabPillDrag(this, pager)
    for (index in 0 until tabCount) getTabAt(index)?.view?.setOnTouchListener(drag)
}

// taps are left to the tab's own click: only a drag past the touch slop is taken here
@SuppressLint("ClickableViewAccessibility")
private class TabPillDrag(private val tabs: TabLayout, private val pager: ViewPager2) : View.OnTouchListener {
    private val touchSlop = ViewConfiguration.get(tabs.context).scaledTouchSlop
    private var downX = 0f
    private var lastX = 0f
    private var isDragging = false

    /** The tab the drag started on, how far the finger went (in tabs) and the tab under the pill. */
    private var startTab = 0
    private var travelledTabs = 0f
    private var nearestTab = 0

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && (abs(event.rawX - downX) < touchSlop || !startDrag(view, event))) return false
                follow(event.rawX)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (isDragging) {
                isDragging = false
                settleOn(if (event.actionMasked == MotionEvent.ACTION_UP) nearestTab else startTab)
                return true
            }
        }
        return false
    }

    /** Watches the pager while it settles after a drag, to bring it back to the chosen tab. */
    private var settling: ViewPager2.OnPageChangeCallback? = null

    /**
     * The pill settles on [target], the tab it is nearer to, like the download sheets' switch.
     * Ending the drag lets the pager fling, and it reads the drag, scaled up to page widths, as
     * a throw to the next page: whatever page that fling picks, the pager is sent on to [target].
     */
    private fun settleOn(target: Int) {
        val smooth = ValueAnimator.areAnimatorsEnabled()
        settling?.let(pager::unregisterOnPageChangeCallback)
        val watch = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position != target) pager.post { pager.setCurrentItem(target, smooth) }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) return
                // not from inside the callback loop that is running it
                pager.post { pager.unregisterOnPageChangeCallback(this) }
                if (settling === this) settling = null
            }
        }
        settling = watch
        pager.registerOnPageChangeCallback(watch)
        if (pager.isFakeDragging) pager.endFakeDrag()
        if (pager.currentItem != target) pager.setCurrentItem(target, smooth)
    }

    /**
     * False while the pages are being swiped themselves. A pager still settling from an earlier
     * drag is taken over where it is, so quick drags in a row are all followed.
     */
    private fun startDrag(view: View, event: MotionEvent): Boolean {
        if (!pager.beginFakeDrag()) return false
        // a new drag outranks the settle of the last one
        settling?.let(pager::unregisterOnPageChangeCallback)
        settling = null
        isDragging = true
        // counted from where the finger crossed the slop, not from this event: while the
        // screen is busy (a page built for the first time) moves arrive merged, and this one
        // can already be far along; the pill then catches up with the finger
        lastX = downX + if (event.rawX > downX) touchSlop else -touchSlop
        startTab = pager.currentItem
        nearestTab = startTab
        travelledTabs = 0f
        // the scroll of the tabs and the screen leave this horizontal drag alone
        view.parent?.requestDisallowInterceptTouchEvent(true)
        // the tab under the finger lets go of its press: this touch is not a tap
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        view.onTouchEvent(cancel)
        cancel.recycle()
        return true
    }

    private fun follow(rawX: Float) {
        val tabWidth = tabs.getTabAt(0)?.view?.width ?: 0
        if (tabWidth == 0) return
        val step = rawX - lastX
        lastX = rawX
        // the pill moves with the finger, so the pages move the other way, a page per tab
        pager.fakeDragBy(-step * pager.width / tabWidth)

        // a light tick when the pill reaches another tab (follows the system haptics setting);
        // in right-to-left layouts the next tab is on the left
        travelledTabs += step / tabWidth
        val direction = if (tabs.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        val reached = (startTab + direction * travelledTabs.roundToInt()).coerceIn(0, tabs.tabCount - 1)
        if (reached != nearestTab) {
            nearestTab = reached
            tabs.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
// endregion
