package com.watube.yard.ui.base

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.loadingindicator.LoadingIndicator
import com.watube.yard.R

// region Navigation
/**
 * The screens run on under the floating bottom bar, a glass pill: the videos show through it
 * and through the gaps around it instead of stopping on a band of background. So that nothing
 * is lost under it, every vertical list of a screen ends [height] pixels higher (with
 * clipToPadding off, its rows still scroll on under the bar) and the floating buttons ride
 * that much higher.
 *
 * MainActivity owns one instance for its navigation host: each screen is fitted as its view
 * is created, and every screen on show again whenever the bar changes height (system bars,
 * rotation, every tab hidden). Dialogs and bottom sheets live in their own windows and are
 * left alone, like the player, which is not hosted by the navigation host.
 */
class BottomBarOverlay(private val navHostId: Int) {

    /** Height of the bar and of the gap under it, in pixels; 0 when the bar is not shown. */
    var height = 0
        private set

    private val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            if (isScreen(f)) fit(v)
        }
    }

    /** Fits every screen [activity] creates from now on; call before its first screen. */
    fun attach(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
    }

    /** The bar is now [newHeight] pixels tall (gap included): refit the screens on show. */
    fun update(activity: FragmentActivity, newHeight: Int) {
        if (newHeight == height) return
        height = newHeight
        screensOf(activity.supportFragmentManager).forEach { screen -> screen.view?.let(::fit) }
    }

    private fun screensOf(fm: FragmentManager): List<Fragment> =
        fm.fragments.filter { it.isAdded }.flatMap { fragment ->
            listOf(fragment).filter(::isScreen) + screensOf(fragment.childFragmentManager)
        }

    /** A page of the navigation host (or a page nested in one), not a dialog or a sheet. */
    private fun isScreen(fragment: Fragment): Boolean {
        val chain = generateSequence(fragment) { it.parentFragment }.toList()
        if (chain.any { it is DialogFragment }) return false
        return chain.drop(1).any { it.id == navHostId }
    }

    private fun fit(view: View) {
        when {
            isVerticalList(view) -> applyBottom(view, height)
            // the floating buttons of a screen sit on its bottom edge, now under the bar
            view is FloatingActionButton || view is ExtendedFloatingActionButton ->
                view.translationY = -height.toFloat()

            // a loading indicator centred on the screen would sit half the bar too low:
            // centre it on the part of the screen the bar leaves visible
            view is LoadingIndicator && isCenteredVertically(view) ->
                view.translationY = -height / 2f

            view is ViewGroup -> view.children.forEach(::fit)
        }
    }

    private fun isCenteredVertically(view: View): Boolean {
        val gravity = when (val params = view.layoutParams) {
            is FrameLayout.LayoutParams -> params.gravity
            is CoordinatorLayout.LayoutParams -> params.gravity
            else -> return false
        }
        return gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.CENTER_VERTICAL
    }

    /**
     * A list that scrolls up and down, whatever its declared height: the home page's scroll
     * view and the search results are wrap_content, yet sized by their parent (a pull to
     * refresh, the rest of a column) they scroll and reach the bottom of the screen. Only the
     * outermost one of a screen is fitted, [fit] stops there, so the lists sized to their rows
     * inside a page that scrolls as a whole are left to it. A row of cards scrolling sideways
     * never reaches the bottom of the screen.
     */
    private fun isVerticalList(view: View): Boolean {
        return when (view) {
            is RecyclerView -> when (val manager = view.layoutManager) {
                is LinearLayoutManager -> manager.orientation == RecyclerView.VERTICAL
                is StaggeredGridLayoutManager -> manager.orientation == RecyclerView.VERTICAL
                else -> true
            }

            is NestedScrollView, is ScrollView, is AbsListView -> true
            else -> false
        }
    }

    companion object {
        /**
         * Keeps [extra] more pixels at the end of [list], on top of the bar, e.g. for the
         * docked mini player. Screens use this instead of setting the list's bottom padding
         * themselves, which would drop the bar's share (or the bar would drop theirs).
         */
        fun setExtraBottom(list: View, extra: Int) {
            list.setTag(R.id.bottom_bar_extra_padding, extra)
            applyBottom(list, list.getTag(R.id.bottom_bar_overlay_height) as? Int ?: 0)
        }

        private fun applyBottom(list: View, barHeight: Int) {
            val base = list.getTag(R.id.bottom_bar_base_padding) as? Int
                ?: list.paddingBottom.also { list.setTag(R.id.bottom_bar_base_padding, it) }
            val extra = list.getTag(R.id.bottom_bar_extra_padding) as? Int ?: 0
            list.setTag(R.id.bottom_bar_overlay_height, barHeight)
            (list as? ViewGroup)?.clipToPadding = false
            list.updatePadding(bottom = base + extra + barHeight)
        }
    }
}
// endregion
