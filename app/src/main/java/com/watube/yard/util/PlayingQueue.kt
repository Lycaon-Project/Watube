package com.watube.yard.util

import androidx.media3.common.Player
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.PlaylistsHelper
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.extensions.move
import com.watube.yard.extensions.runCatchingIO
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.util.PlayingQueue.queueMode
import kotlinx.coroutines.Job
import java.util.Collections

// region Playing Queue
object PlayingQueue {
    // queue is a synchronized list to be safely accessible from different coroutine threads
    private val queue = Collections.synchronizedList(mutableListOf<StreamItem>())
    private var currentStream: StreamItem? = null

    /**
     * The video autoplay goes on with once the queue has nothing left: the first suggestion of the
     * current video. Kept apart from the queue, so that turning autoplay off takes effect at once.
     */
    private var autoplayStream: StreamItem? = null

    private val queueJobs = mutableListOf<Job>()

    /**
     * Current use case of the queue. Do NOT add any offline videos while the [queueMode] is online
     * or vice versa.
     */
    var queueMode: PlayingQueueMode = PlayingQueueMode.ONLINE

    // wrapper around PlayerHelper#repeatMode for compatibility
    var repeatMode: Int
        get() = PlayerHelper.repeatMode
        set(value) {
            PlayerHelper.repeatMode = value
        }

    private fun clearJobs() {
        queueJobs.forEach {
            it.cancel()
        }
        queueJobs.clear()
    }

    fun clear() = synchronized(queue) {
        clearJobs()
        queue.clear()
        currentStream = null
        autoplayStream = null
    }

    /**
     * Remove all items after the current [StreamItem] from the queue
     *
     * I.e., the current and all previous streams are kept
     */
    fun clearAfterCurrent() {
        clearJobs()
        synchronized(queue) {
            val newQueue = queue.filterIndexed { index, _ -> index <= currentIndex() }
            setStreams(newQueue)
        }
    }

    /**
     * @param skipExisting Whether to skip the [streamItem] if it's already part of the queue
     */
    fun add(vararg streamItem: StreamItem, skipExisting: Boolean = false) = synchronized(queue) {
        for (stream in streamItem) {
            if ((skipExisting && contains(stream)) || stream.title.isNullOrBlank()) continue

            queue.remove(stream)
            queue.add(stream)
        }
    }

    fun addAsNext(streamItem: StreamItem): Unit = synchronized(queue) {
        if (currentStream == streamItem) return
        if (queue.contains(streamItem)) queue.remove(streamItem)
        queue.add(currentIndex() + 1, streamItem)
    }

    // return the next item, or if repeating enabled and no video left, the first one of the queue,
    // else the autoplay suggestion
    fun getNext(): String? = synchronized(queue) {
        val autoplay = autoplayStream?.takeIf {
            queueMode == PlayingQueueMode.ONLINE &&
                PlayerHelper.autoPlayEnabled && PlayerHelper.relatedStreamsEnabled
        }
        return nextVideoId(queue, currentIndex(), repeatMode == Player.REPEAT_MODE_ALL, autoplay)
    }

    /** Whether the queue itself has a video after the current one, autoplay left aside. */
    fun hasQueuedNext(): Boolean = synchronized(queue) {
        queue.getOrNull(currentIndex() + 1) != null
    }

    /** Sets the video autoplay plays after the queue, from the suggestions of the current video. */
    fun setAutoplaySuggestions(suggestions: List<StreamItem>) = synchronized(queue) {
        autoplayStream = pickAutoplayStream(suggestions, queue)
    }

    /** The suggestion belonged to the previous video. */
    fun clearAutoplaySuggestion() = synchronized(queue) {
        autoplayStream = null
    }

    // return the previous item, or if repeating enabled and no video left, the last one of the queue
    fun getPrev(): String? = synchronized(queue) {
        val prevItem = queue.getOrNull(currentIndex() - 1)
        if (prevItem != null) return prevItem.url?.toID()

        if (repeatMode == Player.REPEAT_MODE_ALL) return queue.lastOrNull()?.url?.toID()

        return null
    }

    fun hasPrev() = getPrev() != null

    fun hasNext() = getNext() != null

    fun updateCurrent(streamItem: StreamItem) = synchronized(queue) {
        currentStream = streamItem

        if (!contains(streamItem)) add(streamItem)
    }

    fun isNotEmpty() = queue.isNotEmpty()

    fun isEmpty() = queue.isEmpty()

    fun size() = queue.size

    fun currentIndex(): Int = synchronized(queue) {
        return queue.indexOfFirst {
            it.url?.toID() == currentStream?.url?.toID()
        }.takeIf { it >= 0 } ?: 0
    }

    fun getCurrent(): StreamItem? = currentStream

    fun contains(streamItem: StreamItem) = synchronized(queue) {
        queue.any { it.url?.toID() == streamItem.url?.toID() }
    }

    // only returns a copy of the queue, no write access
    fun getStreams() = queue.toList()

    fun setStreams(streams: List<StreamItem>) = synchronized(queue) {
        queue.clear()

        queue.addAll(streams)
    }

    fun remove(index: Int) = synchronized(queue) {
        queue.removeAt(index)
        return@synchronized
    }

    fun move(from: Int, to: Int) = synchronized(queue) {
        queue.move(from, to)
    }

    /**
     * Adds a list of videos to the current queue while updating the position of the current stream
     * @param isMainList whether the videos are part of the list that initially has been used to
     * start the queue, either from a channel or playlist. If it's false, the current stream won't
     * be touched, since it's an independent list.
     */
    private fun addToQueueAsync(
        streams: List<StreamItem>, currentStreamItem: StreamItem? = null, isMainList: Boolean = true
    ): Unit = synchronized(queue) {
        if (!isMainList) {
            add(*streams.toTypedArray())
            return
        }
        val currentStream = currentStreamItem ?: this.currentStream
        // if the stream already got added to the queue earlier, although it's not yet
        // been found in the playlist, remove it and re-add it later
        var reAddStream = true
        if (currentStream != null && streams.any { it.url?.toID() == currentStream.url?.toID() }) {
            queue.removeAll { it.url?.toID() == currentStream.url?.toID() }
            reAddStream = false
        }
        // add all new stream items to the queue
        add(*streams.toTypedArray())

        if (currentStream != null && reAddStream) {
            // re-add the stream to the end of the queue
            updateCurrent(currentStream)
        }
    }

    private suspend fun fetchMoreFromPlaylist(
        playlistId: String,
        nextPage: String?,
        isMainList: Boolean
    ) {
        var playlistNextPage = nextPage
        while (playlistNextPage != null) {
            MediaServiceRepository.instance.getPlaylistNextPage(playlistId, playlistNextPage).run {
                addToQueueAsync(relatedStreams, isMainList = isMainList)
                playlistNextPage = this.nextpage
            }
        }
    }

    fun insertPlaylist(playlistId: String, newCurrentStream: StreamItem?) = runCatchingIO {
        val playlist = PlaylistsHelper.getPlaylist(playlistId)
        val isMainList = newCurrentStream != null
        addToQueueAsync(playlist.relatedStreams, newCurrentStream, isMainList)
        if (playlist.nextpage == null) return@runCatchingIO
        fetchMoreFromPlaylist(playlistId, playlist.nextpage, isMainList)
    }.let { queueJobs.add(it) }

    private suspend fun fetchMoreFromChannel(channelId: String, nextPage: String?) {
        var channelNextPage = nextPage
        var pageIndex = 1
        while (channelNextPage != null && pageIndex < 10) {
            MediaServiceRepository.instance.getChannelNextPage(channelId, channelNextPage).run {
                addToQueueAsync(relatedStreams)
                channelNextPage = this.nextpage
                pageIndex++
            }
        }
    }

    private fun insertChannel(channelId: String, newCurrentStream: StreamItem) = runCatchingIO {
        val channel = MediaServiceRepository.instance.getChannel(channelId)
        addToQueueAsync(channel.relatedStreams, newCurrentStream)
        if (channel.nextpage == null) return@runCatchingIO
        fetchMoreFromChannel(channelId, channel.nextpage)
    }.let { queueJobs.add(it) }

    fun insertByVideoId(videoId: String) = runCatchingIO {
        val streams = MediaServiceRepository.instance.getStreams(videoId.toID())
        add(streams.toStreamItem(videoId))
    }

    fun updateQueue(
        streamItem: StreamItem,
        playlistId: String?,
        channelId: String?,
    ) {
        updateCurrent(streamItem)

        if (playlistId != null) {
            insertPlaylist(playlistId, streamItem)
        } else if (channelId != null) {
            insertChannel(channelId, streamItem)
        }
    }
}

/**
 * The video after the one at [currentIndex]: the next one of [queue], the first one again when the
 * whole queue repeats, else the [autoplay] suggestion (null when autoplay is off).
 */
internal fun nextVideoId(
    queue: List<StreamItem>,
    currentIndex: Int,
    repeatAll: Boolean,
    autoplay: StreamItem?
): String? {
    queue.getOrNull(currentIndex + 1)?.let { return it.url?.toID() }
    if (repeatAll) queue.firstOrNull()?.let { return it.url?.toID() }
    return autoplay?.url?.toID()
}

/**
 * The first playable suggestion that is not already in [queue]: no live stream (it never ends),
 * and no video played earlier, which would make two videos suggesting each other alternate.
 */
internal fun pickAutoplayStream(suggestions: List<StreamItem>, queue: List<StreamItem>): StreamItem? {
    val queued = queue.mapNotNull { it.url?.toID() }.toSet()
    return suggestions.firstOrNull { stream ->
        val id = stream.url?.toID()
        id != null && !stream.isLive && !stream.isUpcoming && id !in queued
    }
}

enum class PlayingQueueMode {
    ONLINE,
    OFFLINE
}
// endregion
