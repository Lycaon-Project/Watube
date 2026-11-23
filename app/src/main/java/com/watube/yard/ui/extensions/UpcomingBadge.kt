package com.watube.yard.ui.extensions

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.watube.yard.R
import com.watube.yard.util.TextUtils

// region UI Components
/*
 * "In 3 days" pill under the title of a video that is not out yet.
 * Rebuilt from the 26.12 build after the emergency restore.
 */

private const val ENTRANCE_DURATION_MS = 350L
private const val ENTRANCE_START_SCALE = 0.8f

/** Material 3 emphasized decelerate: the pill settles in rather than popping. */
private val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

/**
 * Shows the pill with the time left before [upcomingAt], or hides it when the video is out
 * (null). The row's bottom space moves from [metaLine] to the row itself while the pill shows,
 * so the pill sits inside the row's usual margin. The entrance (fade, scale, clock round and
 * glint) plays once per video and only when animations are on and power saving is off.
 */
fun TextView.bindUpcomingBadge(videoId: String, upcomingAt: Long?, metaLine: TextView) {
    val rowSpace = resources.getDimensionPixelSize(R.dimen.watube_row_bottom_space)
    val row = parent as View

    if (upcomingAt == null) {
        animate().cancel()
        resetEntrance()
        UpcomingBadgeMotion.existing(this)?.release()
        (compoundDrawablesRelative[0] as? AnimatedVectorDrawable)?.stop()
        setTag(R.id.upcoming_badge, null)
        isVisible = false
        metaLine.updatePadding(bottom = rowSpace)
        row.updatePadding(bottom = 0)
        return
    }

    val relative = TextUtils.formatRelativeDate(upcomingAt).toString()
    val locale = resources.configuration.locales[0]
    text = relative.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }
    contentDescription = "${context.getString(R.string.upcoming)}, $relative"
    metaLine.updatePadding(bottom = 0)
    row.updatePadding(bottom = rowSpace)

    val isNewVideo = !isVisible || getTag(R.id.upcoming_badge) != videoId
    setTag(R.id.upcoming_badge, videoId)
    isVisible = true
    val clock = clockDrawable()
    if (!isNewVideo) return

    if (UpcomingBadgeMotion.firstTime(videoId) && UpcomingBadgeMotion.canAnimate(this)) {
        playEntrance(clock, UpcomingBadgeMotion.of(this))
    } else {
        animate().cancel()
        resetEntrance()
        UpcomingBadgeMotion.existing(this)?.stopBeat()
        clock?.reset()
    }
}

/** The pill's clock, set once per view from a mutated copy so rows never share its state. */
private fun TextView.clockDrawable(): AnimatedVectorDrawable? {
    (compoundDrawablesRelative[0] as? AnimatedVectorDrawable)?.let { return it }
    val clock = context.getDrawable(R.drawable.avd_upcoming_clock)?.mutate()
        as? AnimatedVectorDrawable ?: return null
    setCompoundDrawablesRelativeWithIntrinsicBounds(clock, null, null, null)
    return clock
}

private fun TextView.playEntrance(clock: AnimatedVectorDrawable?, motion: UpcomingBadgeMotion) {
    animate().cancel()
    clock?.reset()
    alpha = 0f
    scaleX = ENTRANCE_START_SCALE
    scaleY = ENTRANCE_START_SCALE
    // Grows from the clock's side, which is the end in a right-to-left layout.
    doOnPreDraw {
        pivotX = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width.toFloat() else 0f
        pivotY = height / 2f
    }
    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(ENTRANCE_DURATION_MS)
        .setInterpolator(EMPHASIZED_DECELERATE)
        .withEndAction { motion.beat() }
        .start()
}

private fun View.resetEntrance() {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
}
// endregion
