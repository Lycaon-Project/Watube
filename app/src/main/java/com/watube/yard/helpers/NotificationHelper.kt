package com.watube.yard.helpers

import android.content.Context
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

        val checkingFrequency = PreferenceHelper.getString(
            PreferenceKeys.CHECKING_FREQUENCY,
            "60"
        ).toLong()

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
