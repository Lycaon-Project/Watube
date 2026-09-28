package com.watube.yard.util

import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask

class PauseableTimer(
    private val onTick: () -> Unit,
    private val delayMillis: Long = 1000L
) {
    val handler: Handler = Handler(Looper.getMainLooper())

    private var timer: Timer? = null
    private var isRunning = false

    // Note: the timer intentionally does NOT start itself. Callers (e.g. the player
    // service) start it through resume() when there is actually something to tick,
    // so an idle service never keeps a timer chain alive.

    fun resume() {
        // only start a single tick chain, otherwise every call would add another one
        if (isRunning) return

        if (timer == null) {
            timer = Timer()
        }
        isRunning = true
        scheduleNext()
    }

    /**
     * Schedule the next tick with manual re-scheduling
     * This avoids the "catch-up" behavior of scheduleAtFixedRate when process is uncached
     */
    private fun scheduleNext() {
        if (!isRunning) return

        timer?.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    onTick()
                    // Re-schedule for next execution only if still running
                    if (isRunning) {
                        scheduleNext()
                    }
                }
            }
        }, delayMillis)
    }

    fun pause() {
        isRunning = false
        timer?.cancel()
        timer = null
    }

    fun destroy() {
        pause()
    }
}