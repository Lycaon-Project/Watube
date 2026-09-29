package com.watube.yard.db

import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.DatabaseHolder.Database
import com.watube.yard.db.obj.SearchHistoryItem
import com.watube.yard.db.obj.WatchHistoryItem
import com.watube.yard.db.obj.WatchPosition
import com.watube.yard.enums.ContentFilter
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object DatabaseHelper {
    private const val MAX_SEARCH_HISTORY_SIZE = 20

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    // can only mark as watched if less than 60s remaining
    private const val ABSOLUTE_WATCHED_THRESHOLD = 60.0f

    // can only mark as watched if at least 75% watched
    private const val RELATIVE_WATCHED_THRESHOLD = 0.75f

    private val watchPositionCache = ConcurrentHashMap<String, Long>()

    @Volatile
    private var watchPositionCacheLoaded = false

    suspend fun addToWatchHistory(watchHistoryItem: WatchHistoryItem) =
        withContext(Dispatchers.IO) {
            if (PreferenceHelper.isTouristModeEnabled()) return@withContext

            Database.watchHistoryDao().insert(
                watchHistoryItem.copy(watchedAt = System.currentTimeMillis())
            )
            applyWatchHistoryRetention()
        }

    /**
     * Deletes every watch history entry older than the configured retention period.
     * A period of 0 (the default) keeps the whole history.
     */
    suspend fun applyWatchHistoryRetention() {
        val retentionDays = PreferenceHelper.getHistoryRetentionDays()
        if (retentionDays <= 0) return

        val cutoff = System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY
        Database.watchHistoryDao().deleteOlderThan(cutoff)
    }

    /**
     * Everything a browsing session leaves on the device: watch history, search history
     * and playback positions. Used when tourist mode gets turned on.
     */
    suspend fun clearBrowsingData() = withContext(Dispatchers.IO) {
        Database.watchHistoryDao().deleteAll()
        Database.searchHistoryDao().deleteAll()
        clearWatchPositions()
    }

    suspend fun getWatchHistoryPage(page: Int, pageSize: Int): List<WatchHistoryItem> {
        val watchHistoryDao = Database.watchHistoryDao()
        val historySize = watchHistoryDao.getSize()

        if (historySize < pageSize * (page - 1)) return emptyList()

        val offset = historySize - (pageSize * page)
        val limit = if (offset < 0) {
            offset + pageSize
        } else {
            pageSize
        }
        return watchHistoryDao.getN(limit, maxOf(offset, 0)).reversed()
    }

    suspend fun addToSearchHistory(searchHistoryItem: SearchHistoryItem) {
        if (PreferenceHelper.isTouristModeEnabled()) return

        Database.searchHistoryDao().insert(searchHistoryItem)

        if (PreferenceHelper.getBoolean(PreferenceKeys.UNLIMITED_SEARCH_HISTORY, false)) return

        // delete the first watch history entry if the limit is reached
        val searchHistory = Database.searchHistoryDao().getAll().toMutableList()

        while (searchHistory.size > MAX_SEARCH_HISTORY_SIZE) {
            Database.searchHistoryDao().delete(searchHistory.first())
            searchHistory.removeAt(0)
        }
    }

    suspend fun getWatchPosition(videoId: String): Long? {
        if (watchPositionCacheLoaded) return watchPositionCache[videoId]

        return Database.watchPositionDao().findById(videoId)?.position
    }

    /**
     * Watch positions are read for every single row while scrolling, so they are kept
     * in memory instead of hitting Room (and blocking the main thread) on each bind.
     */
    fun getWatchPositionBlocking(videoId: String): Long? {
        if (watchPositionCacheLoaded) return watchPositionCache[videoId]

        return runBlocking(Dispatchers.IO) { getWatchPosition(videoId) }
    }

    /** Loads the watch positions into memory. Runs once, off the main thread. */
    suspend fun primeWatchPositionCache() {
        if (watchPositionCacheLoaded) return

        val positions = withContext(Dispatchers.IO) { Database.watchPositionDao().getAll() }
        positions.forEach { watchPositionCache[it.videoId] = it.position }
        watchPositionCacheLoaded = true
    }

    suspend fun saveWatchPosition(watchPosition: WatchPosition) {
        if (PreferenceHelper.isTouristModeEnabled()) return

        Database.watchPositionDao().insert(watchPosition)
        watchPositionCache[watchPosition.videoId] = watchPosition.position
    }

    suspend fun saveWatchPositions(watchPositions: List<WatchPosition>) {
        if (PreferenceHelper.isTouristModeEnabled()) return

        Database.watchPositionDao().insertAll(watchPositions)
        watchPositions.forEach { watchPositionCache[it.videoId] = it.position }
    }

    suspend fun deleteWatchPosition(videoId: String) {
        Database.watchPositionDao().deleteByVideoId(videoId)
        watchPositionCache.remove(videoId)
    }

    suspend fun clearWatchPositions() {
        Database.watchPositionDao().deleteAll()
        watchPositionCache.clear()
    }

    suspend fun isVideoWatched(videoId: String, duration: Long): Boolean =
        withContext(Dispatchers.IO) {
            val position = getWatchPosition(videoId) ?: return@withContext false

            return@withContext isVideoWatched(position, duration)
        }

    fun isVideoWatched(positionMillis: Long, durationSeconds: Long?): Boolean {
        if (durationSeconds == null) return false

        val progress = positionMillis / 1000

        return durationSeconds - progress <= ABSOLUTE_WATCHED_THRESHOLD && progress >= RELATIVE_WATCHED_THRESHOLD * durationSeconds
    }

    suspend fun filterUnwatched(streams: List<StreamItem>): List<StreamItem> {
        return streams.filter {
            !isVideoWatched(it.url.orEmpty().toID(), it.duration ?: 0)
        }
    }

    /**
     * @param unfinished If true, only returns unfinished videos. If false, only returns finished videos.
     */
    suspend fun filterByWatchStatus(
        watchHistoryItem: WatchHistoryItem,
        unfinished: Boolean = true
    ): Boolean {
        return unfinished xor isVideoWatched(watchHistoryItem.videoId, watchHistoryItem.duration ?: 0)
    }

    suspend fun filterByStreamTypeAndWatchPosition(
        streams: List<StreamItem>,
        hideWatched: Boolean,
        showUpcoming: Boolean
    ): List<StreamItem> {
        val streamItems = streams.filter {
            if (!showUpcoming && it.isUpcoming) return@filter false

            val isVideo = !it.isShort && !it.isLive
            return@filter when {
                !ContentFilter.SHORTS.isEnabled && it.isShort -> false
                !ContentFilter.VIDEOS.isEnabled && isVideo -> false
                !ContentFilter.LIVESTREAMS.isEnabled && it.isLive -> false
                else -> true
            }
        }
        if (!hideWatched) return streamItems

        return filterUnwatched(streamItems)
    }
}
