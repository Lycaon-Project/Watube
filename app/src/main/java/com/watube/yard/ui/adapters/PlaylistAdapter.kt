package com.watube.yard.ui.adapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.VideoRowBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.setFormattedDuration
import com.watube.yard.ui.extensions.setWatchProgressLength
import com.watube.yard.ui.sheets.VideoOptionsBottomSheet
import com.watube.yard.ui.sheets.VideoOptionsBottomSheet.Companion.VIDEO_OPTIONS_SHEET_REQUEST_KEY
import com.watube.yard.ui.viewholders.PlaylistViewHolder
import com.watube.yard.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaylistItem(
    val item: StreamItem,
    /**
     * The original index of the playlist item before sorting the feed.
     */
    val originalPlaylistIndex: Int,
)

class PlaylistAdapter(
    private val playlistId: String,
    private val onVideoClick: (StreamItem) -> Unit
) : ListAdapter<PlaylistItem, PlaylistViewHolder>(DiffUtilItemCallback(
    // the index is not relevant for whether the playlist videos are the same
    // hence only compare the videos themselves
    areItemsTheSame = { a, b -> a.item == b.item },
    areContentsTheSame = { a, b -> a.item == b.item },
)) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = VideoRowBinding.inflate(layoutInflater, parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val (streamItem, _) = getItem(position)!!
        val videoId = streamItem.url!!.toID()

        holder.binding.apply {
            videoTitle.text = streamItem.title
            videoInfo.text = TextUtils.formatViewsString(root.context, streamItem.views ?: -1, streamItem.uploaded)
            videoInfo.maxLines = 2

            // piped does not load channel avatars for playlist views
            channelImageContainer.isGone = true
            channelName.text = streamItem.uploaderName

            ImageHelper.loadImage(streamItem.thumbnail, thumbnail)
            thumbnailDuration.setFormattedDuration(streamItem.duration ?: -1, streamItem.isShort, streamItem.uploaded)

            root.setOnClickListener {
                onVideoClick(streamItem)
            }

            val activity = (root.context as BaseActivity)
            val fragmentManager = activity.supportFragmentManager
            root.setOnLongClickListener {
                fragmentManager.setFragmentResultListener(
                    VIDEO_OPTIONS_SHEET_REQUEST_KEY,
                    activity
                ) { _, _ ->
                    notifyItemChanged(position)
                }
                VideoOptionsBottomSheet().apply {
                    arguments = Bundle().apply {
                        putParcelable(IntentData.streamItem, streamItem)
                        putString(IntentData.playlistId, playlistId)
                    }
                }
                    .show(fragmentManager, VideoOptionsBottomSheet::class.java.name)
                true
            }

            if (!streamItem.uploaderUrl.isNullOrBlank()) {
                channelContainer.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, streamItem.uploaderUrl)
                }
            }

            streamItem.duration?.let { watchProgress.setWatchProgressLength(videoId, it) }

            CoroutineScope(Dispatchers.IO).launch {
                val isDownloaded =
                    DatabaseHolder.Database.downloadDao().exists(videoId)

                withContext(Dispatchers.Main) {
                    downloadBadge.isVisible = isDownloaded
                }
            }
        }
    }
}