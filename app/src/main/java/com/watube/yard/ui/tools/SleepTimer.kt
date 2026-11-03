package com.watube.yard.ui.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.media3.session.MediaController
import com.watube.yard.R
import com.watube.yard.helpers.BackgroundHelper
import com.watube.yard.helpers.ContextHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.ui.base.BaseActivity
import com.google.android.material.snackbar.Snackbar
import kotlin.math.pow

// region Sleep Timer
/**
 * Closes the app at a deadline, after fading the player's volume out.
 *
 * Battery: nothing runs until the fade starts (one delayed message), then a step every
 * [FADE_STEP_MILLIS] for the last [FADE_OUT_MILLIS]. A single connection to the player
 * carries the whole fade; nothing connects (or starts the player service) otherwise.
 */
object SleepTimer {
    private const val FADE_STEP_MILLIS = 500L
    private const val FADE_OUT_MILLIS = 10 * DateUtils.SECOND_IN_MILLIS
    private const val SNACKBAR_START_SECONDS = 5L
    private const val MAX_VOLUME = 1.0f
    private const val CLOSE_DELAY = 500L // Pause before closing

    /** [SystemClock.elapsedRealtime] of the end, 0 when no timer runs. */
    private var endRealtime = 0L

    val timeLeftMillis: Long
        get() = if (endRealtime == 0L) 0L
        else (endRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private var snackBar: Snackbar? = null
    private var shownSecond = 0L
    private val handler = Handler(Looper.getMainLooper())

    /** The player the fade is applied to, connected when the fade starts. */
    private var fadeController: MediaController? = null
    private var isConnecting = false
    private var fadeVolume = MAX_VOLUME

    /** Video the automatic timer last started for: once per video, a cancel holds until the next. */
    private var autoStartedFor: String? = null

    /**
     * Starts the timer with the last chosen duration when the user applies it to all videos,
     * unless one is already counting down (autoplay must not push the deadline back).
     */
    fun startForVideo(context: Context, videoId: String?) {
        if (!PlayerHelper.sleepTimerForAllVideos || timeLeftMillis > 0 || videoId == autoStartedFor) return
        autoStartedFor = videoId
        start(context, PlayerHelper.sleepTimerMinutes)
    }

    /**
     * Start the sleep timer that will close the app after the specified delay
     *
     * @param context This must not be the applicationContext, but an activity context!
     * @param delayInMinutes The delay in minutes before the timer ends
     */
    fun start(context: Context, delayInMinutes: Long) {
        if (delayInMinutes == 0L) return

        // Stop any existing timer first
        stop(context)

        PlayerHelper.sleepTimerMinutes = delayInMinutes
        endRealtime = SystemClock.elapsedRealtime() + delayInMinutes * DateUtils.MINUTE_IN_MILLIS
        step(context)
    }

    /**
     * Stop the sleep timer and restore the volume a fade had lowered
     */
    fun stop(context: Context) {
        handler.removeCallbacksAndMessages(null)
        snackBar?.dismiss()
        snackBar = null
        shownSecond = 0L
        endRealtime = 0L
        if (fadeController != null || isConnecting) fadeTo(context, MAX_VOLUME)
    }

    private fun step(context: Context) {
        val left = timeLeftMillis
        when {
            // sleep until the fade
            left > FADE_OUT_MILLIS -> handler.postDelayed({ step(context) }, left - FADE_OUT_MILLIS)

            // Smooth quadratic fade out during the last seconds
            left > 0 -> {
                fadeTo(context, (left / FADE_OUT_MILLIS.toFloat()).pow(2))
                updateSnackBar(context, left)
                handler.postDelayed({ step(context) }, FADE_STEP_MILLIS)
            }

            // Close app with a pause after fade out completes
            else -> {
                fadeTo(context, 0f)
                handler.postDelayed({ closeApp(context) }, CLOSE_DELAY)
            }
        }
    }

    private fun fadeTo(context: Context, volume: Float) {
        fadeVolume = volume
        fadeController?.let { controller ->
            controller.volume = volume
            if (volume == MAX_VOLUME) releaseFadeController()
            return
        }
        if (isConnecting) return

        isConnecting = true
        BackgroundHelper.startCurrentMediaService(context) { controller ->
            isConnecting = false
            fadeController = controller
            // the latest volume asked while connecting, which may already be the restored one
            fadeTo(context, fadeVolume)
        }
    }

    private fun releaseFadeController() {
        fadeController?.release()
        fadeController = null
    }

    /** Countdown on the last seconds, written once per second. */
    private fun updateSnackBar(context: Context, left: Long) {
        val secondsLeft = (left + DateUtils.SECOND_IN_MILLIS - 1) / DateUtils.SECOND_IN_MILLIS
        if (secondsLeft > SNACKBAR_START_SECONDS || secondsLeft == shownSecond) return
        val activity = ContextHelper.tryUnwrapActivity<BaseActivity>(context)
            ?.takeIf { !it.isFinishing && !it.isDestroyed } ?: return
        shownSecond = secondsLeft

        val message = "${activity.getString(R.string.take_a_break)}: $secondsLeft"
        if (snackBar?.isShownOrQueued == true) {
            snackBar?.setText(message)
        } else {
            snackBar = Snackbar.make(
                activity.window.decorView.rootView,
                message,
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.cancel) {
                stop(activity)
            }
            snackBar?.show()
        }
    }

    private fun closeApp(context: Context) {
        val activity = context.let { ContextHelper.tryUnwrapActivity<BaseActivity>(it) }

        activity?.finishAffinity()
        activity?.finish()
        Process.killProcess(Process.myPid())
    }
}
// endregion
