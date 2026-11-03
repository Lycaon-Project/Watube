package com.watube.yard.util

import android.os.Handler
import android.os.Looper

// region Common Utilities
/**
 * Runs [onTick] on the main thread every [delayMillis] while resumed.
 *
 * A single handler message, no thread of its own: the former java.util.Timer started a new
 * thread on every resume (each play after a pause) only to post the tick to the main thread.
 * It does not start by itself: callers resume it when there is something to tick.
 */
class PauseableTimer(
    private val onTick: () -> Unit,
    private val delayMillis: Long = 1000L
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, delayMillis)
        }
    }

    fun resume() {
        // only one tick chain, otherwise every call would add another one
        if (isRunning) return
        isRunning = true
        handler.postDelayed(tick, delayMillis)
    }

    fun pause() {
        isRunning = false
        handler.removeCallbacks(tick)
    }

    fun destroy() {
        pause()
    }
}
// endregion
