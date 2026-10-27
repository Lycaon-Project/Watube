package com.watube.yard.ui.tools

import android.os.Handler
import android.os.Looper
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.dialogs.RestModeDialog
import java.lang.ref.WeakReference
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

// region Rest Mode
/**
 * Scheduled rest mode: on the chosen days, from [start] to [end] (the next day when the end
 * comes before the start), playback is paused and every screen is covered by [RestModeDialog].
 * A single delayed message waits for the next start or end: nothing polls.
 */
object RestMode {
    private const val DEFAULT_START_MINUTES = 21 * 60
    private const val DEFAULT_END_MINUTES = 7 * 60
    private const val MINUTES_PER_DAY = 24 * 60

    private val handler = Handler(Looper.getMainLooper())
    private var resumedActivity = WeakReference<BaseActivity>(null)

    /** Set by the running player service, so that a period starting mid-video pauses it. */
    var pausePlayback: (() -> Unit)? = null

    private val isScheduled get() = PreferenceHelper.getBoolean(PreferenceKeys.REST_MODE_SCHEDULE, false)

    val days: Set<DayOfWeek>
        get() = PreferenceHelper.getStringSet(PreferenceKeys.REST_MODE_DAYS, emptySet())
            .mapNotNull { day -> day.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of) }
            .toSet()

    var start: LocalTime
        get() = readTime(PreferenceKeys.REST_MODE_START, DEFAULT_START_MINUTES)
        set(value) = writeTime(PreferenceKeys.REST_MODE_START, value)

    var end: LocalTime
        get() = readTime(PreferenceKeys.REST_MODE_END, DEFAULT_END_MINUTES)
        set(value) = writeTime(PreferenceKeys.REST_MODE_END, value)

    private fun readTime(key: String, default: Int) = LocalTime.ofSecondOfDay(
        PreferenceHelper.getInt(key, default).coerceIn(0, MINUTES_PER_DAY - 1) * 60L
    )

    private fun writeTime(key: String, time: LocalTime) =
        PreferenceHelper.putInt(key, time.hour * 60 + time.minute)

    /** End of the rest period [now] falls in, null outside of one. */
    fun activeUntil(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!isScheduled) return null
        val days = days
        val start = start
        val end = end
        // a period opens on a chosen day: yesterday's one may still run past midnight
        return listOf(1L, 0L).map { now.toLocalDate().minusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(end).plusDays(if (end > start) 0 else 1) to it.atTime(start) }
            .firstOrNull { (to, from) -> !now.isBefore(from) && now.isBefore(to) }
            ?.first
    }

    private fun nextChange(now: LocalDateTime): LocalDateTime? {
        if (!isScheduled) return null
        return activeUntil(now) ?: (0L..7L).map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(start) }
            .firstOrNull { it.isAfter(now) }
    }

    /** Arms the check of the next start or end: at launch and after each settings change. */
    fun schedule() {
        handler.removeCallbacksAndMessages(null)
        val now = LocalDateTime.now()
        val next = nextChange(now) ?: return
        // one extra second, so that the check lands inside the new state
        handler.postDelayed(::onChange, Duration.between(now, next).toMillis() + 1000)
    }

    private fun onChange() {
        if (activeUntil() != null) pausePlayback?.invoke()
        resumedActivity.get()?.let(::enforce)
        schedule()
    }

    fun onResume(activity: BaseActivity) {
        resumedActivity = WeakReference(activity)
        enforce(activity)
    }

    fun onPause(activity: BaseActivity) {
        if (resumedActivity.get() === activity) resumedActivity.clear()
    }

    /** Shows the blocking dialog during a period and removes it once the period is over. */
    private fun enforce(activity: BaseActivity) {
        val until = activeUntil()
        val fragmentManager = activity.supportFragmentManager
        val dialog = fragmentManager.findFragmentByTag(RestModeDialog.TAG) as? RestModeDialog
        when {
            until == null -> dialog?.dismissAllowingStateLoss()
            dialog == null && !fragmentManager.isStateSaved -> {
                pausePlayback?.invoke()
                RestModeDialog.newInstance(until.toLocalTime())
                    .show(fragmentManager, RestModeDialog.TAG)
            }
        }
    }

    val isActive get() = activeUntil() != null
}
// endregion
