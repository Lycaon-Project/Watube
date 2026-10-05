package com.watube.yard.api

import androidx.annotation.StringRes
import com.watube.yard.R
import com.watube.yard.api.obj.Channel
import com.watube.yard.api.obj.ChannelTabResponse
import com.watube.yard.api.obj.CommentsPage
import com.watube.yard.api.obj.DeArrowContent
import com.watube.yard.api.obj.Playlist
import com.watube.yard.api.obj.SearchResult
import com.watube.yard.api.obj.SegmentData
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.api.obj.Streams
import com.watube.yard.helpers.PlayerHelper

interface MediaServiceRepository {
    fun getTrendingCategories(): List<TrendingCategory>

    suspend fun getTrending(region: String, category: TrendingCategory): List<StreamItem>
    suspend fun getStreams(videoId: String): Streams

    /**
     * A new streaming session for [streams], nothing else fetched again (see
     * [com.watube.yard.player.SabrAttestationException]); null when the backend has none to offer.
     */
    suspend fun renewStreamingSession(videoId: String, streams: Streams): Streams? = null
    suspend fun getComments(videoId: String): CommentsPage
    suspend fun getSegments(
        videoId: String,
        category: List<String>,
        actionType: List<String>? = null
    ): SegmentData

    suspend fun getDeArrowContent(videoId: String): DeArrowContent?
    suspend fun getCommentsNextPage(videoId: String, nextPage: String): CommentsPage
    suspend fun getSearchResults(searchQuery: String, filter: String): SearchResult
    suspend fun getSearchResultsNextPage(
        searchQuery: String,
        filter: String,
        nextPage: String
    ): SearchResult

    suspend fun getSuggestions(query: String): List<String>
    suspend fun getChannel(channelId: String): Channel
    suspend fun getChannelTab(data: String, nextPage: String? = null): ChannelTabResponse
    suspend fun getChannelByName(channelName: String): Channel
    suspend fun getChannelNextPage(channelId: String, nextPage: String): Channel
    suspend fun getPlaylist(playlistId: String): Playlist
    suspend fun getPlaylistNextPage(playlistId: String, nextPage: String): Playlist

    companion object {
        val instance: MediaServiceRepository
            get() = when {
                PlayerHelper.fullLocalMode -> NewPipeMediaServiceRepository()
                PlayerHelper.localStreamExtraction -> LocalStreamsExtractionPipedMediaServiceRepository()
                else -> PipedMediaServiceRepository()
            }
    }
}

enum class TrendingCategory(@param:StringRes val titleRes: Int) {
    GAMING(R.string.gaming),
    TRAILERS(R.string.trailers),
    PODCASTS(R.string.podcasts),
    MUSIC(R.string.music),
    LIVE(R.string.live)
}