package com.watube.yard.api

import com.watube.yard.api.RetrofitInstance.PIPED_API_URL
import com.watube.yard.api.obj.Channel
import com.watube.yard.api.obj.ChannelTabResponse
import com.watube.yard.api.obj.CommentsPage
import com.watube.yard.api.obj.DeArrowContent
import com.watube.yard.api.obj.Message
import com.watube.yard.api.obj.Playlist
import com.watube.yard.api.obj.SearchResult
import com.watube.yard.api.obj.SegmentData
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.api.obj.Streams
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import retrofit2.HttpException

open class PipedMediaServiceRepository : MediaServiceRepository {
    // region Trends
    override fun getTrendingCategories(): List<TrendingCategory> = emptyList()

    override suspend fun getTrending(region: String, category: TrendingCategory): List<StreamItem> =
        api.getTrending(region)
    // endregion

    // region Video Player
    override suspend fun getStreams(videoId: String): Streams {
        return try {
            api.getStreams(videoId).also {
                it.isShort = it.videoStreams.firstOrNull()?.let { stream ->
                    (stream.height ?: 0) > (stream.width ?: 0)
                } ?: false
            }
        } catch (e: HttpException) {
            val errorMessage = e.response()?.errorBody()?.string()?.runCatching {
                JsonHelper.json.decodeFromString<Message>(this).message
            }?.getOrNull()

            throw Exception(errorMessage)
        }
    }
    // endregion

    // region Comments
    override suspend fun getComments(videoId: String): CommentsPage =
        api.getComments(videoId)

    override suspend fun getCommentsNextPage(videoId: String, nextPage: String): CommentsPage =
        api.getCommentsNextPage(videoId, nextPage)
    // endregion

    // region SponsorBlock
    override suspend fun getSegments(
        videoId: String,
        category: List<String>,
        actionType: List<String>?
    ): SegmentData = api.getSegments(
        videoId,
        JsonHelper.json.encodeToString(category),
        JsonHelper.json.encodeToString(actionType)
    )
    // endregion

    // region DeArrow
    override suspend fun getDeArrowContent(videoId: String): DeArrowContent? =
        api.getDeArrowContent(videoId)[videoId]
    // endregion

    // region Search
    override suspend fun getSearchResults(searchQuery: String, filter: String): SearchResult =
        api.getSearchResults(searchQuery, filter)

    override suspend fun getSearchResultsNextPage(
        searchQuery: String,
        filter: String,
        nextPage: String
    ): SearchResult = api.getSearchResultsNextPage(searchQuery, filter, nextPage)

    override suspend fun getSuggestions(query: String): List<String> =
        api.getSuggestions(query)
    // endregion

    // region Channels
    override suspend fun getChannel(channelId: String): Channel =
        api.getChannel(channelId)

    override suspend fun getChannelTab(data: String, nextPage: String?): ChannelTabResponse =
        api.getChannelTab(data, nextPage)

    override suspend fun getChannelByName(channelName: String): Channel =
        api.getChannelByName(channelName)

    override suspend fun getChannelNextPage(channelId: String, nextPage: String): Channel =
        api.getChannelNextPage(channelId, nextPage)
    // endregion

    // region Playlists
    override suspend fun getPlaylist(playlistId: String): Playlist =
        api.getPlaylist(playlistId)

    override suspend fun getPlaylistNextPage(playlistId: String, nextPage: String): Playlist =
        api.getPlaylistNextPage(playlistId, nextPage)
    // endregion

    // region Piped Instances
    companion object {
        val apiUrl get() = PreferenceHelper.getString(PreferenceKeys.FETCH_INSTANCE, PIPED_API_URL)

        private val api by resettableLazy(RetrofitInstance.apiLazyMgr) {
            RetrofitInstance.buildRetrofitInstance<PipedApi>(apiUrl)
        }
    }
    // endregion
}
