package com.watube.yard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.watube.yard.services.DownloadService

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        val serviceIntent = Intent(context, DownloadService::class.java)
            .setAction(intent.action)

        val id = intent.getIntExtra("id", -1)
        if (id == -1) return
        serviceIntent.putExtra("id", id)

        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_DOWNLOAD_RESUME =
            "com.watube.yard.receivers.NotificationReceiver.ACTION_DOWNLOAD_RESUME"
        const val ACTION_DOWNLOAD_PAUSE =
            "com.watube.yard.receivers.NotificationReceiver.ACTION_DOWNLOAD_PAUSE"
        const val ACTION_DOWNLOAD_STOP =
            "com.watube.yard.receivers.NotificationReceiver.ACTION_DOWNLOAD_STOP"
    }
}
