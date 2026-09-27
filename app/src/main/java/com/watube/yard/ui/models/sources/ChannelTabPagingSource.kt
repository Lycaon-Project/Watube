package com.watube.yard.ui.models.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.obj.ChannelTab
import com.watube.yard.api.obj.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChannelTabPagingSource(
    private val tab: ChannelTab
): PagingSource<String, ContentItem>() {
    override fun getRefreshKey(state: PagingState<String, ContentItem>) = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ContentItem> {
        return try {
            val resp = withContext(Dispatchers.IO) {
                MediaServiceRepository.instance.getChannelTab(tab.data, params.key)
            }
            LoadResult.Page(resp.content, null, resp.nextpage)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}