package com.watube.yard.helpers

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.getSystemService
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.ui.activities.AppLockActivity
import java.util.concurrent.TimeUnit

// region Privacy
/**
 * Optional app lock: once the app has been in the background for the chosen delay, the
 * [AppLockActivity] covers it until the user unlocks with a biometric or the device credential
 * (PIN, pattern, password). Nothing is stored: the system checks the user, the app only learns
 * success.
 */
object AppLock {
    const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** visible screens of the app, the lock screen included */
    private var startedActivities = 0
    private var wentBackgroundAt = 0L
    private var unlockedOnce = false
    private var skipNextCheck = false

    var isLocked = false
        private set

    private val isEnabled: Boolean
        get() = PreferenceHelper.getBoolean(PreferenceKeys.APP_LOCK, false)

    private val delayMs: Long
        get() = PreferenceHelper.getString(PreferenceKeys.APP_LOCK_DELAY, "0")
            .toLongOrNull()
            ?.let(TimeUnit.SECONDS::toMillis)
            ?: 0L

    /**
     * The phone has a screen lock (PIN, pattern, password), which the prompt can always fall
     * back to. Deliberately not BiometricManager.canAuthenticate: it also fails while the
     * biometrics are temporarily locked out after repeated attempts, and a lockout must never
     * be read as "nothing to ask for".
     */
    fun canLock(context: Context): Boolean =
        context.getSystemService<KeyguardManager>()?.isDeviceSecure == true

    /** Turning the lock on from the settings must not lock the screen the user is on. */
    fun onEnabled() {
        unlockedOnce = true
        isLocked = false
    }

    fun onUnlocked() {
        unlockedOnce = true
        isLocked = false
    }

    /** Called by every screen of the app when it becomes visible. */
    fun onStart(activity: Activity) {
        val firstVisible = startedActivities++ == 0
        when {
            // a rotation stops and restarts the screen: the app never left the foreground
            skipNextCheck -> skipNextCheck = false
            firstVisible && isEnabled -> {
                val away = SystemClock.elapsedRealtime() - wentBackgroundAt
                if (!unlockedOnce || away >= delayMs) isLocked = true
            }
        }
        // turned off meanwhile, or the screen lock of the phone removed: nothing left to ask for
        if (!isEnabled || !canLock(activity)) isLocked = false
        // any screen of the app that shows up while locked brings the lock screen back on top
        // (a relaunch from the launcher can show the main screen first)
        if (isLocked) {
            activity.startActivity(
                Intent(activity, AppLockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
        }
    }

    fun onStop(activity: Activity) {
        startedActivities--
        if (activity.isChangingConfigurations) {
            skipNextCheck = true
        } else if (startedActivities == 0) {
            wentBackgroundAt = SystemClock.elapsedRealtime()
        }
    }

    /** The lock screen counts as a visible screen, without checking the lock itself. */
    fun onLockScreenStarted() {
        startedActivities++
    }

    fun onLockScreenStopped(activity: Activity) = onStop(activity)
}
// endregion
