package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.watube.yard.extensions.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.api.JsonHelper
import com.watube.yard.api.obj.ContentItem
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.ChannelRowBinding
import com.watube.yard.databinding.PlaylistsRowBinding
import com.watube.yard.databinding.VideoRowBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.enums.PlaylistType
import com.watube.yard.extensions.formatShort
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.setFormattedDuration
import com.watube.yard.ui.extensions.setWatchProgressLength
import com.watube.yard.ui.extensions.setupSubscriptionButton
import com.watube.yard.ui.sheets.ChannelOptionsBottomSheet
import com.watube.yard.ui.sheets.PlaylistOptionsBottomSheet
import com.watube.yard.ui.sheets.VideoOptionsBottomSheet
import com.watube.yard.ui.viewholders.SearchViewHolder
import com.watube.yard.util.DeArrowUtil
import com.watube.yard.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class SearchResultsAdapter(
    private val timeStamp: Long = 0
) : PagingDataAdapter<ContentItem, SearchViewHolder>(
    DiffUtilItemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.url == newItem.url },
        areContentsTheSame = { _, _ -> true },
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            1 -> SearchViewHolder(
                ChannelRowBinding.inflate(layoutInflater, parent, false)
            )

            2 -> SearchViewHolder(
                PlaylistsRowBinding.inflate(layoutInflater, parent, false)
            )

            // streams and unknown/future result types are rendered as a video row:
            // an unexpected type from the API must never crash the search screen
            else -> SearchViewHolder(
                VideoRowBinding.inflate(layoutInflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val searchItem = getItem(position) ?: return

        // previous async work of this row is obsolete as soon as it is rebound
        holder.bindJob?.cancel()
        holder.bindJob = null

        val videoRowBinding = holder.videoRowBinding
        val channelRowBinding = holder.channelRowBinding
        val playlistRowBinding = holder.playlistRowBinding

        if (videoRowBinding != null) {
            bindVideo(searchItem, videoRowBinding, holder)
        } else if (channelRowBinding != null) {
            bindChannel(searchItem, channelRowBinding)
        } else if (playlistRowBinding != null) {
            bindPlaylist(searchItem, playlistRowBinding)
        }
    }

    override fun onViewRecycled(holder: SearchViewHolder) {
        holder.bindJob?.cancel()
        holder.bindJob = null
        super.onViewRecycled(holder)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)?.type) {
            StreamItem.TYPE_CHANNEL -> 1
            StreamItem.TYPE_PLAYLIST -> 2
            // streams and unknown types fall back to the video row (see onCreateViewHolder)
            else -> 0
        }
    }

    private fun bindVideo(item: ContentItem, binding: VideoRowBinding, holder: SearchViewHolder) {
        binding.apply {
            ImageHelper.loadImage(item.thumbnail, thumbnail)

            thumbnailDuration.setFormattedDuration(item.duration, item.isShort, item.uploaded)
            videoTitle.text = item.title

            videoInfo.text = TextUtils.formatViewsString(root.context, item.views, item.uploaded)
            videoInfo.isVisible = !videoInfo.text.isNullOrEmpty()

            channelContainer.isGone = item.uploaderAvatar.isNullOrEmpty()
            channelName.text = item.uploaderName
            ImageHelper.loadImage(item.uploaderAvatar, channelImage, true)

            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, PlayerData(item.url, timestamp = timeStamp))
            }

            val videoId = item.url.toID()
            val activity = (root.context as BaseActivity)
            val fragmentManager = activity.supportFragmentManager
            root.setOnLongClickListener {
                fragmentManager.setFragmentResultListener(
                    VideoOptionsBottomSheet.VIDEO_OPTIONS_SHEET_REQUEST_KEY,
                    activity
                ) { _, _ ->
                    // the row may have moved or been removed while the sheet was open
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(currentPosition)
                    }
                }
                val sheet = VideoOptionsBottomSheet()
                val contentItemString = JsonHelper.json.encodeToString(item)
                val streamItem: StreamItem = JsonHelper.json.decodeFromString(contentItemString)
                sheet.arguments = bundleOf(IntentData.streamItem to streamItem)
                sheet.show(fragmentManager, SearchResultsAdapter::class.java.name)
                true
            }
            channelContainer.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, item.uploaderUrl)
            }
            watchProgress.setWatchProgressLength(videoId, item.duration)

            holder.bindJob = CoroutineScope(Dispatchers.IO).launch {
                val isDownloaded =
                    DatabaseHolder.Database.downloadDao().exists(videoId)

                withContext(Dispatchers.Main) {
                    if (isStillBoundTo(holder, videoId)) {
                        downloadBadge.isVisible = isDownloaded
                    }
                }

                DeArrowUtil.deArrowVideoId(videoId)?.let { (title, thumbnail) ->
                    withContext(Dispatchers.Main) {
                        if (!isStillBoundTo(holder, videoId)) return@withContext
                        if (title != null) binding.videoTitle.text = title
                        if (thumbnail != null) ImageHelper.loadImage(thumbnail, binding.thumbnail)
                    }
                }
            }
        }
    }

    /**
     * True while [holder] still displays the row of [videoId]: a recycled row must never
     * receive the download badge or the DeArrow title of another video.
     */
    private fun isStillBoundTo(holder: SearchViewHolder, videoId: String): Boolean {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return false
        return peek(position)?.url.orEmpty().toID() == videoId
    }

    private fun bindChannel(item: ContentItem, binding: ChannelRowBinding) {
        binding.apply {
            ImageHelper.loadImage(item.thumbnail, searchChannelImage, true)
            searchChannelName.text = item.name

            val subscribers = item.subscribers.formatShort()
            searchViews.text = if (item.subscribers >= 0 && item.videos >= 0) {
                root.context.getString(R.string.subscriberAndVideoCounts, subscribers, item.videos)
            } else if (item.subscribers >= 0) {
                root.context.getString(R.string.subscribers, subscribers)
            } else if (item.videos >= 0) {
                root.context.getString(R.string.videoCount, item.videos)
            } else {
                ""
            }

            root.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, item.url)
            }

            var subscribed = false
            binding.searchSubButton.setupSubscriptionButton(
                item.url.toID(),
                item.name.orEmpty(),
                item.thumbnail,
                item.uploaderVerified ?: false
            ) {
                subscribed = it
            }

            root.setOnLongClickListener {
                val channelOptionsSheet = ChannelOptionsBottomSheet()
                channelOptionsSheet.arguments = bundleOf(
                    IntentData.channelId to item.url.toID(),
                    IntentData.channelName to item.name,
                    IntentData.isSubscribed to subscribed
                )
                channelOptionsSheet.show((root.context as BaseActivity).supportFragmentManager)
                true
            }
        }
    }

    private fun bindPlaylist(item: ContentItem, binding: PlaylistsRowBinding) {
        binding.apply {
            ImageHelper.loadImage(item.thumbnail, playlistThumbnail)
            // the count band must not survive view recycling with a stale value
            if (item.videos >= 0) {
                playlistSide.isVisible = true
                videoCount.text = item.videos.toString()
            } else {
                playlistSide.isVisible = false
            }
            playlistTitle.text = item.name
            playlistDescription.text = item.uploaderName
            root.setOnClickListener {
                NavigationHelper.navigatePlaylist(root.context, item.url, PlaylistType.PUBLIC)
            }

            root.setOnLongClickListener {
                val sheet = PlaylistOptionsBottomSheet()
                sheet.arguments = bundleOf(
                    IntentData.playlistId to item.url.toID(),
                    IntentData.playlistName to item.name.orEmpty(),
                    IntentData.playlistType to PlaylistType.PUBLIC
                )
                sheet.show(
                    (root.context as BaseActivity).supportFragmentManager,
                    PlaylistOptionsBottomSheet::class.java.name
                )
                true
            }
        }
    }
}
