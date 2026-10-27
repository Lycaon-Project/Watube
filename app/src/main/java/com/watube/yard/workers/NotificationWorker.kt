package com.watube.yard.workers

import android.Manifest
import android.app.Notification
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.watube.yard.R
import com.watube.yard.WatubeApp.Companion.PUSH_CHANNEL_NAME
import com.watube.yard.api.SubscriptionHelper
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.enums.TopLevelDestination
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.ui.activities.MainActivity
import com.watube.yard.ui.views.TimePickerPreference
import com.watube.yard.util.DataSaverMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// region New Video Notifications
/**
 * Checks the subscriptions for new streams at the frequency chosen by the user and notifies
 * about them, while keeping both the noise and the resource usage low:
 * - the feed is really refreshed on every run (the local feed only re-reads the RSS of each
 *   channel and fully loads the channels which uploaded something new);
 * - a video is only notified once, even when its upload time moves or the run is retried;
 * - a few channels get one notification group each, more of them a single digest, so a busy
 *   morning never floods the shade, and only one thumbnail per channel is downloaded;
 * - the titles stay hidden on the lock screen, the run is skipped in battery saver mode.
 */
class NotificationWorker(appContext: Context, parameters: WorkerParameters) :
    CoroutineWorker(appContext, parameters) {

    private val notificationManager = NotificationManagerCompat.from(appContext)

    override suspend fun doWork(): Result {
        if (!checkTime()) {
            Log.d(TAG(), "Outside notification time window")
            return Result.success()
        }

        // the battery saver asks apps to defer background work: the next run catches up,
        // since the last check time only moves when a feed was actually read
        if (applicationContext.getSystemService<PowerManager>()?.isPowerSaveMode == true) {
            Log.d(TAG(), "Battery saver on, skipping this run")
            return Result.success()
        }

        if (!canPostNotifications()) {
            Log.w(TAG(), "Notifications are not allowed, skipping this run")
            return Result.success()
        }

        return if (checkForNewStreams()) Result.success() else Result.retry()
    }

    /**
     * Determine whether the time is valid to notify
     */
    private fun checkTime(): Boolean {
        if (!PreferenceHelper.getBoolean(PreferenceKeys.NOTIFICATION_TIME_ENABLED, false)) {
            return true
        }

        val start = getTimePickerPref(PreferenceKeys.NOTIFICATION_START_TIME)
        val end = getTimePickerPref(PreferenceKeys.NOTIFICATION_END_TIME)
        val currentTime = LocalTime.now()

        return if (start > end) {
            currentTime !in end..start
        } else {
            currentTime in start..end
        }
    }

    private fun getTimePickerPref(key: String): LocalTime {
        return try {
            LocalTime.parse(
                PreferenceHelper.getString(key, TimePickerPreference.DEFAULT_VALUE)
            )
        } catch (e: Exception) {
            Log.w(TAG(), "Failed to parse time preference for $key", e)
            LocalTime.of(0, 0)
        }
    }

    private fun canPostNotifications() = ContextCompat.checkSelfPermission(
        applicationContext,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED && notificationManager.areNotificationsEnabled()

    /**
     * check whether new streams are available in subscriptions
     *
     * @return false if the feed could not be read, so that the work is retried
     */
    private suspend fun checkForNewStreams(): Boolean {
        // forceRefresh: the cached local feed is only renewed once a day, reading it made every
        // hourly check miss the videos uploaded since. The system stops a work after 10
        // minutes: give up a bit before, a retry follows with a backoff.
        val videoFeed = try {
            withTimeoutOrNull(FEED_TIMEOUT) {
                withContext(Dispatchers.IO) {
                    SubscriptionHelper.getFeed(forceRefresh = true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG(), "Failed to fetch feed", e)
            null
        } ?: return false

        val feed = videoFeed.filter { !it.isUpcoming }
        val newestUpload = feed.maxOfOrNull { it.uploaded } ?: return true

        val lastFeedCheckMillis = PreferenceHelper.getLastCheckedFeedTime(seenByUser = false)

        // first run once notifications are enabled: only remember where the feed stands
        // (not every repository sorts the feed: the newest upload is the mark)
        val newVideos = if (lastFeedCheckMillis == 0L) {
            emptyList()
        } else {
            selectVideosToNotify(feed, lastFeedCheckMillis)
        }
        if (newVideos.isEmpty()) {
            PreferenceHelper.updateLastFeedWatchedTime(newestUpload, seenByUser = false)
            return true
        }

        // keyed by channel id, the channel with the newest upload first
        val channelGroups = newVideos.groupBy { requireNotNull(it.uploaderUrl).toID() }
        Log.d(TAG(), "${newVideos.size} new videos from ${channelGroups.size} channels")

        if (channelGroups.size > MAX_CHANNEL_GROUPS) {
            postDigest(newVideos, channelGroups.size)
        } else {
            channelGroups.forEach { (channelId, streams) ->
                postChannelGroup(channelId, streams.take(MAX_NOTIFICATIONS_PER_CHANNEL))
            }
        }

        // the mark only moves once everything was posted: a worker stopped on the way (lost
        // constraint, killed process) finds the same videos again, the ids avoid duplicates
        rememberNotifiedIds(newVideos.mapNotNull { it.url?.toID() })
        PreferenceHelper.updateLastFeedWatchedTime(newestUpload, seenByUser = false)
        return true
    }

    /** The videos worth a notification, newest first */
    private fun selectVideosToNotify(
        feed: List<StreamItem>,
        lastFeedCheckMillis: Long
    ): List<StreamItem> {
        val notifiedIds = readNotifiedIds().toSet()
        val channelsToIgnore = PreferenceHelper.getIgnorableNotificationChannels().toSet()
        val enableShortsNotification =
            PreferenceHelper.getBoolean(PreferenceKeys.SHORTS_NOTIFICATIONS, false)
        // a phone switched off for days must not get a flood of stale videos when it comes back
        val oldestNotifiable =
            System.currentTimeMillis() - MAX_NOTIFIED_VIDEO_AGE.inWholeMilliseconds

        return feed.asSequence()
            .filter { it.uploaded > lastFeedCheckMillis && it.uploaded > oldestNotifiable }
            .filter { enableShortsNotification || !it.isShort }
            .filter { stream ->
                val channelId = stream.uploaderUrl?.toID() ?: return@filter false
                channelId !in channelsToIgnore
            }
            .filter { stream -> stream.url?.toID()?.let { it !in notifiedIds } ?: false }
            .sortedByDescending { it.uploaded }
            .toList()
    }

    /**
     * A single notification for many channels: no image is downloaded and a single alert is
     * played, however many channels uploaded since the last check.
     */
    private fun postDigest(streams: List<StreamItem>, channelCount: Int) {
        val resources = applicationContext.resources
        val style = NotificationCompat.InboxStyle()
            .setSummaryText(
                resources.getQuantityString(
                    R.plurals.new_videos_digest_channels, channelCount, channelCount
                )
            )
        streams.take(MAX_DIGEST_LINES).forEach { stream ->
            style.addLine("${stream.uploaderName.orEmpty()} · ${stream.title.orEmpty()}")
        }
        if (streams.size > MAX_DIGEST_LINES) {
            style.addLine(
                applicationContext.getString(
                    R.string.new_videos_more,
                    streams.size - MAX_DIGEST_LINES
                )
            )
        }

        // opens the subscriptions feed, where all of them are listed
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setFlags(INTENT_FLAGS)
            .putExtra(IntentData.fragmentToOpen, TopLevelDestination.Subscriptions.route)
        val pendingIntent = PendingIntentCompat
            .getActivity(applicationContext, DIGEST_ID, intent, FLAG_UPDATE_CURRENT, false)

        val notification = createNotificationBuilder(DIGEST_GROUP)
            .setContentTitle(
                resources.getQuantityString(R.plurals.new_videos_digest, streams.size, streams.size)
            )
            .setContentText(streams.first().title)
            .setContentIntent(pendingIntent)
            .setStyle(style)
            .setNumber(streams.size)
            .setWhen(streams.first().uploaded)
            .setShowWhen(true)
            .build()

        notify(DIGEST_ID, notification)
    }

    /**
     * Group of notifications created when new streams are found in a given channel.
     */
    private suspend fun postChannelGroup(channelId: String, streams: List<StreamItem>) {
        if (streams.isEmpty()) return

        val summaryId = channelId.hashCode()
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setFlags(INTENT_FLAGS)
            .putExtra(IntentData.channelId, channelId)
        val pendingIntent = PendingIntentCompat
            .getActivity(applicationContext, summaryId, intent, FLAG_UPDATE_CURRENT, false)

        val newStreams = applicationContext.resources
            .getQuantityString(R.plurals.channel_new_streams, streams.size, streams.size)
        val summary = NotificationCompat.InboxStyle().setSummaryText(newStreams)
        streams.forEach { summary.addLine(it.title.orEmpty()) }

        val newest = streams.first()
        val summaryNotification = createNotificationBuilder(channelId)
            .setContentTitle(newest.uploaderName)
            .setContentText(newStreams)
            .setContentIntent(pendingIntent)
            .setGroupSummary(true)
            .setStyle(summary)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setLargeIcon(downloadImage(newest.uploaderAvatar))
            .build()

        // only the newest video gets its thumbnail: the others stay in the collapsed group
        // most of the time, downloading their pictures was wasted data and battery
        val notifications = streams.mapIndexedNotNull { index, stream ->
            createStreamNotification(channelId, stream, withThumbnail = index == 0)
        }
        if (notifications.isEmpty()) return

        try {
            notificationManager.notify(notifications)
        } catch (e: SecurityException) {
            // the permission can be revoked while the work runs
            Log.w(TAG(), "Could not post notifications", e)
            return
        }
        notify(summaryId, summaryNotification)
    }

    private suspend fun createStreamNotification(
        group: String,
        stream: StreamItem,
        withThumbnail: Boolean
    ): NotificationWithIdAndTag? {
        val videoId = stream.url?.toID() ?: return null
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setFlags(INTENT_FLAGS)
            .putExtra(IntentData.videoId, videoId)
        val notificationId = videoId.hashCode()
        val pendingIntent = PendingIntentCompat
            .getActivity(applicationContext, notificationId, intent, FLAG_UPDATE_CURRENT, false)

        val builder = createNotificationBuilder(group)
            .setContentTitle(stream.title)
            .setContentText(stream.uploaderName)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setWhen(stream.uploaded)
            .setShowWhen(true)

        if (withThumbnail) {
            downloadImage(stream.thumbnail)?.let { thumbnail ->
                builder.setLargeIcon(thumbnail)
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(thumbnail)
                            .bigLargeIcon(null as Bitmap?) // Hides the icon when expanding
                    )
            }
        }

        return NotificationWithIdAndTag(notificationId, builder.build())
    }

    /**
     * Download an image for a notification, unless the data saver is on (the setting is a
     * list - disabled / metered / enabled - reading it as a boolean threw a ClassCastException)
     */
    private suspend fun downloadImage(url: String?): Bitmap? {
        if (url.isNullOrBlank() || DataSaverMode.isEnabled(applicationContext)) return null

        return try {
            withTimeoutOrNull(IMAGE_TIMEOUT) {
                withContext(Dispatchers.IO) {
                    ImageHelper.getImage(applicationContext, url)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG(), "Failed to download a notification image", e)
            null
        }
    }

    private fun createNotificationBuilder(group: String): NotificationCompat.Builder {
        // what the lock screen shows instead: neither the channels nor the titles, which
        // would tell anybody glancing at the phone what its owner watches
        val publicVersion = NotificationCompat.Builder(applicationContext, PUSH_CHANNEL_NAME)
            .setSmallIcon(R.drawable.ic_launcher_lockscreen)
            .setContentTitle(applicationContext.getString(R.string.new_videos_hidden))
            .build()

        return NotificationCompat.Builder(applicationContext, PUSH_CHANNEL_NAME)
            .setSmallIcon(R.drawable.ic_launcher_lockscreen)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // no setOnlyAlertOnce: a digest or a channel summary still shown is replaced (same
            // id) when newer videos arrive, and that must alert; the children are silent
            .setAutoCancel(true)
            .setGroup(group)
            .setCategory(Notification.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            // the permission can be revoked while the work runs
            Log.w(TAG(), "Could not post a notification", e)
        }
    }

    private fun readNotifiedIds(): List<String> = PreferenceHelper
        .getString(PreferenceKeys.NOTIFIED_VIDEO_IDS, "")
        .split(ID_SEPARATOR)
        .filter { it.isNotBlank() }

    /** Keep the ids just notified first, then the older ones, bounded */
    private fun rememberNotifiedIds(newIds: List<String>) {
        val ids = (newIds + readNotifiedIds()).distinct().take(MAX_REMEMBERED_IDS)
        PreferenceHelper.putString(
            PreferenceKeys.NOTIFIED_VIDEO_IDS,
            ids.joinToString(ID_SEPARATOR)
        )
    }

    companion object {
        private const val INTENT_FLAGS = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        /** More channels than this with new videos are merged into a single digest */
        private const val MAX_CHANNEL_GROUPS = 3
        private const val MAX_NOTIFICATIONS_PER_CHANNEL = 5
        private const val MAX_DIGEST_LINES = 6
        private const val MAX_REMEMBERED_IDS = 300
        private const val ID_SEPARATOR = ","

        private const val DIGEST_GROUP = "watube_new_videos_digest"
        private val DIGEST_ID = DIGEST_GROUP.hashCode()

        private val FEED_TIMEOUT = 8.minutes
        private val IMAGE_TIMEOUT = 10.seconds
        private val MAX_NOTIFIED_VIDEO_AGE = 48.hours
    }
}
// endregion
