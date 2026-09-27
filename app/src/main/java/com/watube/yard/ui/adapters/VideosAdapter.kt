package com.watube.yard.ui.adapters

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.VideoRowBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.setFormattedDuration
import com.watube.yard.ui.extensions.setWatchProgressLength
import com.watube.yard.ui.sheets.VideoOptionsBottomSheet
import com.watube.yard.ui.viewholders.VideosViewHolder
import com.watube.yard.util.DeArrowUtil
import com.watube.yard.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideosAdapter(
    private val showChannelInfo: Boolean = true
) : ListAdapter<StreamItem, VideosViewHolder>(
    // identity is the video url so that only changed contents get rebound
    DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem ->
        oldItem.url == newItem.url && oldItem.type == newItem.type
    })
) {

    fun insertItems(newItems: List<StreamItem>) {
        val updatedList = currentList.toMutableList().also {
            it.addAll(newItems)
        }

        submitList(updatedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideosViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = VideoRowBinding.inflate(layoutInflater, parent, false)
        return VideosViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VideosViewHolder, position: Int) {
        val video = getItem(holder.bindingAdapterPosition)
        val videoId = video.url.orEmpty().toID()

        val context = holder.binding.root.context
        val activity = (context as BaseActivity)
        val fragmentManager = activity.supportFragmentManager

        with(holder.binding) {
            videoTitle.text = video.title
            videoInfo.text = TextUtils.formatViewsString(root.context, video.views ?: -1, video.uploaded)

            video.duration?.let { thumbnailDuration.setFormattedDuration(it, video.isShort, video.uploaded) }
            watchProgress.setWatchProgressLength(videoId, video.duration ?: 0L)
            ImageHelper.loadImage(video.thumbnail, thumbnail)

            if (showChannelInfo) {
                ImageHelper.loadImage(video.uploaderAvatar, channelImage, true)
                channelName.text = video.uploaderName

                channelContainer.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, video.uploaderUrl)
                }
            } else {
                channelImageContainer.isGone = true
            }

            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, PlayerData(videoId))
            }

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
                sheet.arguments = Bundle().apply {
                    putParcelable(IntentData.streamItem, video)
                }
                sheet.show(fragmentManager, VideosAdapter::class.java.name)
                true
            }

            // previous async work of this row is obsolete as soon as it is rebound
            holder.bindJob?.cancel()
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
                        if (title != null) holder.binding.videoTitle.text = title
                        if (thumbnail != null) ImageHelper.loadImage(thumbnail, holder.binding.thumbnail)
                    }
                }
            }
        }
    }

    /**
     * True while [holder] still displays the row of [videoId]: a recycled row must never
     * receive the download badge or the DeArrow title of another video.
     */
    private fun isStillBoundTo(holder: VideosViewHolder, videoId: String): Boolean {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return false
        return currentList.getOrNull(position)?.url.orEmpty().toID() == videoId
    }

    override fun onViewRecycled(holder: VideosViewHolder) {
        holder.bindJob?.cancel()
        holder.bindJob = null
        super.onViewRecycled(holder)
    }
}