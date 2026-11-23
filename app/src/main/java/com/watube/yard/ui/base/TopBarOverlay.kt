package com.watube.yard.ui.base

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.watube.yard.R
import com.watube.yard.extensions.dpToPx

// region Navigation
/**
 * A screen that runs on under the top bar (brand, theme, bell, menu) instead of starting
 * below it: its own header covers the bar's room, so the bar's buttons sit on that header's
 * glass. [fitTopBar] gets the bar's height, status bar included, each time it changes.
 */
interface RunsUnderTopBar {
    fun fitTopBar(height: Int)
}

/**
 * What a [RunsUnderTopBar] screen with a collapsing header (a channel, the subscriptions)
 * needs to run on under the top bar: the header's background (Efecto's glass, or its plain
 * fill) covers the bar, and what scrolls up under the bar is hidden, so the bar's buttons only
 * ever sit on that background.
 */
object UnderTopBar {
    /** Where the pull-to-refresh circle comes to rest, below the top bar (its default). */
    private const val REFRESH_TARGET_DP = 64f

    /**
     * Keeps [topBar] pixels free at the top of [collapsing] (a CollapsingToolbarLayout) and
     * folds it no further than that room plus its [pinned] part (0 when nothing stays).
     */
    fun fitCollapsing(collapsing: View, topBar: Int, pinned: Int = 0) {
        if (collapsing.minimumHeight != topBar + pinned) collapsing.minimumHeight = topBar + pinned
        if (collapsing.paddingTop != topBar) collapsing.updatePadding(top = topBar)
    }

    /**
     * Hides the part of [content], the scrolling part of a collapsing header, that has gone up
     * under the top bar: the header has scrolled [verticalOffset] pixels (an AppBarLayout
     * offset, 0 or less) and its content starts right below the bar's room.
     */
    fun clip(content: View, verticalOffset: Int) {
        val hidden = (-verticalOffset).coerceIn(0, content.height)
        if (hidden == 0) {
            if (content.clipBounds != null) content.clipBounds = null
            return
        }
        val rect = content.getTag(R.id.watube_top_bar_clip) as? Rect
            ?: Rect().also { content.setTag(R.id.watube_top_bar_clip, it) }
        rect.set(0, hidden, content.width, content.height)
        content.clipBounds = rect
    }

    /** Brings the pull-to-refresh circle of [refresh] down below the top bar. */
    fun fitRefresh(refresh: SwipeRefreshLayout, topBar: Int) {
        val circle = refresh.progressCircleDiameter
        refresh.setProgressViewOffset(false, topBar - circle, topBar + REFRESH_TARGET_DP.dpToPx())
    }
}

/**
 * The screens are laid out from the top of the window, under the top bar, which has no
 * background of its own. Each screen starts [height] pixels lower, below the bar, as it
 * always did, except the screens that run on under it themselves ([RunsUnderTopBar]).
 *
 * MainActivity owns one instance for its navigation host: each screen is fitted as its view
 * is created, and the screens on show again whenever the bar changes height. Only the pages
 * of the host are fitted: dialogs live in their own windows and the pages nested in a screen
 * move with it.
 */
class TopBarOverlay(private val navHostId: Int) {

    /** Height of the top bar, status bar included, in pixels. */
    var height = 0
        private set

    private val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?
        ) {
            if (isScreen(f)) fit(f, v)
        }
    }

    /** Fits every screen [activity] creates from now on; call before its first screen. */
    fun attach(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
    }

    /** The bar is now [newHeight] pixels tall: refit the screens on show. */
    fun update(activity: FragmentActivity, newHeight: Int) {
        if (newHeight == height) return
        height = newHeight
        val host = activity.supportFragmentManager.findFragmentById(navHostId) ?: return
        host.childFragmentManager.fragments
            .filter { it.isAdded && isScreen(it) }
            .forEach { screen -> screen.view?.let { fit(screen, it) } }
    }

    private fun isScreen(fragment: Fragment) =
        fragment !is DialogFragment && fragment.parentFragment?.id == navHostId

    private fun fit(screen: Fragment, view: View) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val margin = if (screen is RunsUnderTopBar) 0 else height
        if (params.topMargin != margin) {
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = margin }
        }
        if (screen is RunsUnderTopBar) screen.fitTopBar(height)
    }
}
// endregion
