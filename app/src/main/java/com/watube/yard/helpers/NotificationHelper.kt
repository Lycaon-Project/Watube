package com.watube.yard.helpers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.workers.NotificationWorker
import java.util.concurrent.TimeUnit

// region New Video Notifications
object NotificationHelper {
    private const val NOTIFICATION_WORK_NAME = "NotificationService"
    private const val DEFAULT_FREQUENCY_MINUTES = 60L
    private const val MIN_FREQUENCY_MINUTES = 15L
    private const val RETRY_BACKOFF_MINUTES = 10L

    /**
     * Bump it whenever [enqueueWork] builds a request that differs for every user (worker,
     * constraints, backoff), so that the next app start applies it once.
     */
    private const val WORK_SPEC_VERSION = 2

    /**
     * Called at every app start. The process is also started by the system to run this very
     * work: an UPDATE from there stopped the worker that had just begun (it was cancelled by
     * the app), so the check never completed unless the app happened to be open. KEEP leaves
     * the running schedule alone; the spec is only replaced once after it changed.
     */
    fun scheduleOnAppStart(context: Context) {
        PreferenceHelper.initialize(context)
        val isCurrent =
            PreferenceHelper.getInt(PreferenceKeys.NOTIFICATION_WORK_SPEC_VERSION, 0) == WORK_SPEC_VERSION
        enqueueWork(
            context,
            if (isCurrent) ExistingPeriodicWorkPolicy.KEEP else ExistingPeriodicWorkPolicy.UPDATE
        )
        PreferenceHelper.putInt(PreferenceKeys.NOTIFICATION_WORK_SPEC_VERSION, WORK_SPEC_VERSION)
    }

    /**
     * Enqueue the work manager task
     */
    fun enqueueWork(context: Context, existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy) {
        // get the notification preferences
        PreferenceHelper.initialize(context)
        val notificationsEnabled = PreferenceHelper.getBoolean(
            PreferenceKeys.NOTIFICATION_ENABLED,
            true
        )

        // schedule the work manager request if logged in and notifications enabled
        if (!notificationsEnabled) {
            // cancel the work if notifications are disabled or the user is not logged in
            WorkManager.getInstance(context)
                .cancelUniqueWork(NOTIFICATION_WORK_NAME)
            return
        }

        // a corrupted value must not crash the app start; WorkManager never runs a periodic
        // work more often than every 15 minutes anyway
        val checkingFrequency = PreferenceHelper.getString(
            PreferenceKeys.CHECKING_FREQUENCY,
            DEFAULT_FREQUENCY_MINUTES.toString()
        ).toLongOrNull()?.coerceAtLeast(MIN_FREQUENCY_MINUTES) ?: DEFAULT_FREQUENCY_MINUTES

        // required network type for the work
        val networkType = when (
            PreferenceHelper.getString(PreferenceKeys.REQUIRED_NETWORK, "all")
        ) {
            "all" -> NetworkType.CONNECTED
            "wifi" -> NetworkType.UNMETERED
            "metered" -> NetworkType.METERED
            else -> NetworkType.CONNECTED
        }

        // requirements for the work: the network, and (battery) not when the battery is low,
        // since a check fetches the feed of every subscribed channel
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        // create the worker
        val notificationWorker = PeriodicWorkRequestBuilder<NotificationWorker>(
            checkingFrequency,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            // a failed check (offline, YouTube throttling) is retried after 10, 20, 40...
            // minutes instead of the default 30 seconds, which kept waking the radio up
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        // enqueue the task to the work manager instance
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                NOTIFICATION_WORK_NAME,
                existingPeriodicWorkPolicy,
                notificationWorker
            )
    }
}
// endregion
