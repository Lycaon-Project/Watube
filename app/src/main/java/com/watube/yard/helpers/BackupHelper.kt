package com.watube.yard.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.api.JsonHelper
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.db.DatabaseHolder.Database
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.obj.BackupFile
import com.watube.yard.obj.PreferenceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Backup and restore the preferences
 */
object BackupHelper {
    /**
     * Write a [BackupFile] containing the database content as well as the preferences
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun createAdvancedBackup(context: Context, uri: Uri, backupFile: BackupFile) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                JsonHelper.json.encodeToStream(backupFile, outputStream)
            }
            context.toastFromMainDispatcher(R.string.backup_creation_success)
        } catch (e: Exception) {
            Log.e(TAG(), "Error while writing backup: $e")
            context.toastFromMainDispatcher(R.string.backup_creation_failed)
        }
    }

    /**
     * Restore data from a [BackupFile]
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreAdvancedBackup(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val backupFile = context.contentResolver.openInputStream(uri)?.use {
            JsonHelper.json.decodeFromStream<BackupFile>(it)
        } ?: return@withContext

        // tourist mode keeps browsing data off the device, even while restoring a backup;
        // entries without a watch time are stamped so the retention purge can age them
        if (!PreferenceHelper.isTouristModeEnabled()) {
            val restoredAt = System.currentTimeMillis()
            Database.watchHistoryDao().insertAll(
                backupFile.watchHistory.orEmpty().map { item ->
                    if (item.watchedAt > 0) item else item.copy(watchedAt = restoredAt)
                }
            )
            Database.searchHistoryDao().insertAll(backupFile.searchHistory.orEmpty())
            DatabaseHelper.saveWatchPositions(backupFile.watchPositions.orEmpty())
        }
        Database.localSubscriptionDao().insertAll(backupFile.subscriptions.orEmpty())
        Database.customInstanceDao().insertAll(backupFile.customInstances.orEmpty())
        Database.playlistBookmarkDao().insertAll(backupFile.playlistBookmarks.orEmpty())
        Database.subscriptionGroupsDao().insertAll(backupFile.groups.orEmpty())

        backupFile.localPlaylists?.forEach {
            // the playlist will be created with an id of 0, so that Room will auto generate a
            // new playlist id to avoid conflicts with existing local playlists
            val playlistId = Database.localPlaylistsDao().createPlaylist(it.playlist.copy(id = 0))
            it.videos.forEach { playlistItem ->
                playlistItem.playlistId = playlistId.toInt()
                Database.localPlaylistsDao().addPlaylistVideo(playlistItem.copy(id = 0))
            }
        }

        restorePreferences(context, backupFile.preferences)
    }

    /**
     * Restore the shared preferences from a backup file
     */
    private fun restorePreferences(context: Context, preferences: List<PreferenceItem>?) {
        if (preferences == null) return

        var ignoredSensitive = 0
        var ignoredType = 0

        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            // clear the previous settings
            clear()

            // decide for each preference which type it is and save it to the preferences
            preferences.forEach { (key, jsonValue) ->
                // security: a backup is an untrusted file, so the keys that steer a
                // network destination or hold identity/privacy state are never imported
                // (see SENSITIVE_PREFERENCE_KEYS). Without this, a crafted file could point
                // selectAuthInstance at an attacker server and leak the Piped token, which
                // is sent as Authorization on the auth endpoints. After clear() these keys
                // simply fall back to their privacy preserving defaults.
                if (key != null && key in SENSITIVE_PREFERENCE_KEYS) {
                    ignoredSensitive++
                    return@forEach
                }

                val value = if (jsonValue.isString) {
                    jsonValue.content
                } else {
                    jsonValue.booleanOrNull
                        ?: jsonValue.intOrNull
                        ?: jsonValue.longOrNull
                        ?: jsonValue.floatOrNull
                }

                // security: SharedPreferences never coerces types, a value stored under the
                // wrong type crashes the next read (ClassCastException), so it is dropped
                // instead of restored.
                if (!hasExpectedType(key, value)) {
                    ignoredType++
                    return@forEach
                }

                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Long -> putLong(key, value)
                    is Int -> {
                        // we only use integers for SponsorBlock colors and the start fragment
                        if (key == PreferenceKeys.START_FRAGMENT || "_color" in key.orEmpty()) {
                            putInt(key, value)
                        } else {
                            putLong(key, value.toLong())
                        }
                    }

                    is String -> {
                        if (
                            key == PreferenceKeys.HOME_TAB_CONTENT ||
                            key == PreferenceKeys.SELECTED_FEED_FILTERS
                        ) {
                            putStringSet(key, value.split(",").toSet())
                        } else {
                            putString(key, value)
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && (ignoredSensitive > 0 || ignoredType > 0)) {
            Log.d(
                TAG(),
                "restorePreferences: ignored $ignoredSensitive sensitive, $ignoredType mistyped"
            )
        }

        // re-schedule the notification worker as some settings related to it might have changed
        NotificationHelper.enqueueWork(context, ExistingPeriodicWorkPolicy.UPDATE)
    }

    /**
     * Expected stored type of a preference value.
     *
     * SharedPreferences performs no type coercion: reading a key with the wrong getter
     * throws ClassCastException, so an unexpected type is a crash vector when the value
     * comes from an untrusted backup file.
     */
    private enum class RestoredType { BOOLEAN, STRING, STRING_SET, NUMBER }

    /**
     * Keys that are never restored from a backup file, and therefore never exported either
     * (see [com.watube.yard.ui.dialogs.BackupDialog]).
     *
     * Security: these decide where network traffic is sent (Piped instances, image proxy,
     * external downloader) or hold identity / privacy state. Restoring them from an
     * untrusted file could redirect the Piped auth token to an attacker instance or turn
     * privacy_hardening off. They are simply not imported, so they fall back to the app
     * defaults after the clear() above.
     */
    internal val SENSITIVE_PREFERENCE_KEYS = setOf(
        // network destinations
        PreferenceKeys.FETCH_INSTANCE,             // Piped API base url
        PreferenceKeys.AUTH_INSTANCE,              // auth API base url (receives the token)
        PreferenceKeys.AUTH_INSTANCE_TOGGLE,       // enables the auth instance override
        PreferenceKeys.IMAGE_PROXY_URL,            // host used to proxy thumbnails
        PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER, // app the video is handed over to
        // stamp of the proxy refresh above: keeping it would suppress the re-fetch of the
        // (blocked) image_proxy_url from the instance actually selected after the restore
        PreferenceKeys.LAST_PROXY_FETCH_INSTANCE,
        PreferenceKeys.LAST_PROXY_FETCH_TIME,
        // privacy controls, all defaulting to the privacy preserving side
        PreferenceKeys.PRIVACY_HARDENING,
        PreferenceKeys.PRIVACY_NEUTRAL_REGION,
        PreferenceKeys.PRIVACY_ROTATION_FREQUENCY,
        PreferenceKeys.RESET_SB_UUID_DAILY,
        // rotating identifiers, they are regenerated on the device instead
        PreferenceKeys.SB_USER_ID,
        PreferenceKeys.SB_USER_ID_CYCLE,
        // credentials live in auth.xml and must never land in the default preferences
        PreferenceKeys.TOKEN,
        PreferenceKeys.USERNAME,
        // crash logs may contain video ids and urls, they have their own preference file
        PreferenceKeys.ERROR_LOG,
    )

    /**
     * Type expected for the keys the app reads with a typed getter / preference widget.
     * Keys absent from this map (action rows, obsolete keys) keep the legacy behavior and
     * are restored as parsed.
     */
    private val EXPECTED_PREFERENCE_TYPES: Map<String, RestoredType> = buildMap {
        // SwitchPreferenceCompat rows and explicit getBoolean call sites
        listOf(
            PreferenceKeys.AUDIO_ONLY_MODE,
            PreferenceKeys.AUTOMATIC_UPDATE_CHECKS,
            PreferenceKeys.ALTERNATIVE_PIP_CONTROLS,
            PreferenceKeys.AUTH_INSTANCE_TOGGLE,
            PreferenceKeys.AUTO_FULLSCREEN,
            PreferenceKeys.AUTO_FULLSCREEN_SHORTS,
            PreferenceKeys.AUTOPLAY,
            PreferenceKeys.AUTOPLAY_COUNTDOWN,
            PreferenceKeys.CONTRIBUTE_TO_DEARROW,
            PreferenceKeys.CONTRIBUTE_TO_SB,
            PreferenceKeys.DEARROW,
            PreferenceKeys.DOUBLE_TAP_TO_SEEK,
            PreferenceKeys.FULLSCREEN_GESTURES,
            PreferenceKeys.FULL_LOCAL_MODE,
            PreferenceKeys.HIDE_WATCHED_FROM_FEED,
            PreferenceKeys.INCLUDE_TIMESTAMP_IN_BACKUP_FILENAME,
            PreferenceKeys.LOCAL_FEED_EXTRACTION,
            PreferenceKeys.LOCAL_RYD,
            PreferenceKeys.LOCAL_STREAM_EXTRACTION,
            PreferenceKeys.LONG_PRESS_FAST_FORWARD,
            PreferenceKeys.NEW_VIDEOS_BADGE,
            PreferenceKeys.NOTIFICATION_ENABLED,
            PreferenceKeys.NOTIFICATION_TIME_ENABLED,
            PreferenceKeys.PAUSE_ON_SCREEN_OFF,
            PreferenceKeys.PLAYER_PINCH_CONTROL,
            PreferenceKeys.PLAYER_SWIPE_CONTROLS,
            PreferenceKeys.PLAY_AUTOMATICALLY,
            PreferenceKeys.PRIVACY_HARDENING,
            PreferenceKeys.PRIVACY_NEUTRAL_REGION,
            PreferenceKeys.PURE_THEME,
            PreferenceKeys.RELATED_STREAMS,
            PreferenceKeys.RESET_SB_UUID_DAILY,
            PreferenceKeys.RICH_CAPTION_RENDERING,
            PreferenceKeys.SEARCH_HISTORY_TOGGLE,
            PreferenceKeys.SEARCH_SUGGESTIONS,
            PreferenceKeys.SHARE_WITH_TIME_CODE,
            PreferenceKeys.SHORTS_NOTIFICATIONS,
            PreferenceKeys.SHOW_TIME_LEFT,
            PreferenceKeys.SHOW_UPCOMING_IN_FEED,
            PreferenceKeys.SKIP_SILENCE,
            PreferenceKeys.SYSTEM_CAPTION_STYLE,
            PreferenceKeys.TOURIST_MODE,
            PreferenceKeys.UNLIMITED_SEARCH_HISTORY,
            PreferenceKeys.WATCH_HISTORY_TOGGLE,
            // rows declared with a literal key in sponsorblock_settings.xml
            "sb_enabled_key",
            "sb_notifications_key",
            "sb_enable_custom_colors",
        ).forEach { put(it, RestoredType.BOOLEAN) }

        // ListPreference / EditTextPreference rows and explicit putString call sites
        listOf(
            PreferenceKeys.ACCENT_COLOR,
            PreferenceKeys.APP_FONT,
            PreferenceKeys.APP_ICON,
            PreferenceKeys.AUTH_INSTANCE,
            PreferenceKeys.BUFFERING_GOAL,
            PreferenceKeys.CAPTIONS_SIZE,
            PreferenceKeys.CHECKING_FREQUENCY,
            PreferenceKeys.DATA_SAVER_MODE,
            PreferenceKeys.DEFAULT_RESOLUTION,
            PreferenceKeys.DEFAULT_RESOLUTION_MOBILE,
            PreferenceKeys.DEFAULT_SUBTITLE,
            PreferenceKeys.EXTERNAL_DOWNLOAD_PROVIDER,
            PreferenceKeys.FEED_DENSITY,
            PreferenceKeys.FETCH_INSTANCE,
            PreferenceKeys.FULLSCREEN_ORIENTATION,
            PreferenceKeys.GRID_COLUMNS_LANDSCAPE,
            PreferenceKeys.GRID_COLUMNS_PORTRAIT,
            PreferenceKeys.HISTORY_RETENTION_DAYS,
            PreferenceKeys.IGNORED_NOTIFICATION_CHANNELS,
            PreferenceKeys.IMAGE_PROXY_URL,
            PreferenceKeys.LANGUAGE,
            PreferenceKeys.LAST_PROXY_FETCH_INSTANCE,
            PreferenceKeys.NAVBAR_ITEMS,
            PreferenceKeys.NEUTRAL_REGION_ACTIVE,
            PreferenceKeys.NEUTRAL_REGION_CYCLE,
            PreferenceKeys.NOTIFICATION_END_TIME,
            PreferenceKeys.NOTIFICATION_START_TIME,
            PreferenceKeys.ORIENTATION,
            PreferenceKeys.PLAYER_AUDIO_QUALITY,
            PreferenceKeys.PLAYER_AUDIO_QUALITY_MOBILE,
            PreferenceKeys.PLAYBACK_SPEED,
            PreferenceKeys.PLAYLISTS_ORDER,
            PreferenceKeys.PRIVACY_ROTATION_FREQUENCY,
            PreferenceKeys.REGION,
            PreferenceKeys.REQUIRED_NETWORK,
            PreferenceKeys.SB_USER_ID,
            PreferenceKeys.SB_USER_ID_CYCLE,
            PreferenceKeys.SEEK_INCREMENT,
            PreferenceKeys.THEME_MODE,
            PreferenceKeys.TRENDING_CATEGORY,
            PreferenceKeys.WATCH_POSITIONS,
            // ListPreference rows without a PreferenceKeys constant
            "default_res_no_fullscreen",
            "default_res_mobile_no_fullscreen",
        ).forEach { put(it, RestoredType.STRING) }

        // exported as a single comma separated string, written back as a StringSet
        listOf(
            PreferenceKeys.HOME_TAB_CONTENT,
            PreferenceKeys.SELECTED_FEED_FILTERS,
        ).forEach { put(it, RestoredType.STRING_SET) }

        // putInt / putLong / SeekBarPreference: Int and Long are both accepted because
        // PreferenceHelper.getInt falls back to getLong, but a Float or String would crash
        listOf(
            PreferenceKeys.FEED_SORT_ORDER,
            PreferenceKeys.FONT_SCALE,
            PreferenceKeys.LAST_LOCAL_FEED_REFRESH_TIMESTAMP_MILLIS,
            PreferenceKeys.LAST_PROXY_FETCH_TIME,
            PreferenceKeys.LAST_REFRESHED_FEED_TIME,
            PreferenceKeys.LAST_SHOWN_INFO_MESSAGE_VERSION_CODE,
            PreferenceKeys.LAST_UPDATE_CHECK_TIME,
            PreferenceKeys.LAST_USER_SEEN_FEED_TIME,
            PreferenceKeys.PLAYER_RESIZE_MODE,
            PreferenceKeys.PLAYLIST_SORT_ORDER,
            PreferenceKeys.PREFERENCE_VERSION,
            PreferenceKeys.REPEAT_MODE,
            PreferenceKeys.SELECTED_CHANNEL_GROUP,
            PreferenceKeys.SELECTED_DOWNLOAD_PLAYLIST_SORT_TYPE,
            PreferenceKeys.SELECTED_DOWNLOAD_SORT_TYPE,
            PreferenceKeys.SELECTED_HISTORY_STATUS_FILTER,
            PreferenceKeys.START_FRAGMENT,
        ).forEach { put(it, RestoredType.NUMBER) }
    }

    /**
     * Type of the dynamic SponsorBlock keys built as "&lt;category&gt;_category" /
     * "&lt;category&gt;_color" (SbSpinnerPreference persists a String, ColorPreference an Int).
     */
    private fun dynamicType(key: String): RestoredType? = when {
        key.endsWith("_category") -> RestoredType.STRING
        key.endsWith("_color") -> RestoredType.NUMBER
        else -> null
    }

    /**
     * @return false when the parsed [value] does not match the type the app reads for [key]
     */
    private fun hasExpectedType(key: String?, value: Any?): Boolean {
        if (key == null) return true
        val expected = EXPECTED_PREFERENCE_TYPES[key] ?: dynamicType(key) ?: return true
        return when (expected) {
            RestoredType.BOOLEAN -> value is Boolean
            RestoredType.STRING -> value is String
            RestoredType.STRING_SET -> value is String
            RestoredType.NUMBER -> value is Int || value is Long
        }
    }
}
