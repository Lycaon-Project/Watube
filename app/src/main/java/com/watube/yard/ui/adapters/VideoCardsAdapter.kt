package com.watube.yard.ui.adapters

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.api.SponsorBlockLabelHelper
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.AllCaughtUpRowBinding
import com.watube.yard.databinding.TrendingRowBinding
import com.watube.yard.extensions.dpToPx
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.setFormattedDuration
import com.watube.yard.ui.extensions.setWatchProgressLength
import com.watube.yard.ui.sheets.VideoOptionsBottomSheet
import com.watube.yard.ui.viewholders.VideoCardsViewHolder
import com.watube.yard.util.DeArrowUtil
import com.watube.yard.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoCardsAdapter(private val columnWidthDp: Float? = null) :
    ListAdapter<StreamItem, VideoCardsViewHolder>(
        // identity is the video url so that only changed contents get rebound
        DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem ->
            oldItem.url == newItem.url && oldItem.type == newItem.type
        })
    ) {

    override fun getItemViewType(position: Int): Int {
        return if (currentList[position].type == CAUGHT_UP_STREAM_TYPE) CAUGHT_UP_TYPE else NORMAL_TYPE
    }

    fun removeItemById(videoId: String) {
        val index = currentList.indexOfFirst {
            it.url?.toID() == videoId
        }.takeIf { it > 0 } ?: return
        val updatedList = currentList.toMutableList().also {
            it.removeAt(index)
        }

        submitList(updatedList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoCardsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when {
            viewType == CAUGHT_UP_TYPE -> VideoCardsViewHolder(
                AllCaughtUpRowBinding.inflate(layoutInflater, parent, false)
            )

            else -> VideoCardsViewHolder(
                TrendingRowBinding.inflate(layoutInflater, parent, false)
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VideoCardsViewHolder, position: Int) {
        val video = getItem(holder.bindingAdapterPosition)
        val videoId = video.url.orEmpty().toID()

        val context = (holder.trendingRowBinding ?: holder.allCaughtUpBinding)!!.root.context
        val activity = (context as BaseActivity)
        val fragmentManager = activity.supportFragmentManager

        holder.trendingRowBinding?.apply {
            // set a fixed width for better visuals
            if (columnWidthDp != null) {
                root.updateLayoutParams {
                    width = columnWidthDp.dpToPx()
                }
            }
            watchProgress.setWatchProgressLength(videoId, video.duration ?: 0L)

            textViewTitle.text = video.title
            textViewChannel.text = TextUtils.formatViewsString(
                root.context,
                video.views ?: -1,
                video.uploaded,
                video.uploaderName
            )

            video.duration?.let {
                thumbnailDuration.setFormattedDuration(
                    it,
                    video.isShort,
                    video.uploaded
                )
            }
            ImageHelper.loadImage(video.thumbnail, thumbnail)

            if (video.uploaderAvatar != null) {
                channelImageContainer.isVisible = true
                ImageHelper.loadImage(video.uploaderAvatar, channelImage, true)
                channelImage.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, video.uploaderUrl)
                }
            } else {
                channelImageContainer.isGone = true
                textViewChannel.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, video.uploaderUrl)
                }
            }
            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, PlayerData(videoId))
            }

            root.setOnLongClickListener {
                fragmentManager.setFragmentResultListener(
                    VideoOptionsBottomSheet.VIDEO_OPTIONS_SHEET_REQUEST_KEY,
                    activity
                ) { _, _ ->
                    notifyItemChanged(position)
                }
                val sheet = VideoOptionsBottomSheet()
                sheet.arguments = Bundle().apply {
                    putParcelable(IntentData.streamItem, video)
                }
                sheet.show(fragmentManager, VideoCardsAdapter::class.java.name)
                true
            }

            // always hide the icon, to avoid issues where the icon is recycled and shown until the web requests succeeds
            sponsorBadgeCard.isVisible = false
            CoroutineScope(Dispatchers.IO).launch {
                if (PlayerHelper.sponsorBlockEnabled) {
                    val sponsor = SponsorBlockLabelHelper.getVideoLabels(videoId)
                    withContext(Dispatchers.Main) {
                        val category = sponsor?.segments?.firstOrNull()?.category
                        sponsorBadgeCard.isVisible = category != null
                        SponsorBlockLabelHelper.categoryIcon(category)?.let {
                            sponsorBadgeIcon.setImageDrawable(
                                AppCompatResources.getDrawable(context, it)
                            )
                        }
                        sponsorBadgeIcon.tooltipText =
                            SponsorBlockLabelHelper.categoryLabel(category)
                                ?.let { context.getString(it) }
                    }
                }

                DeArrowUtil.deArrowVideoId(videoId)?.let { (title, thumbnail) ->
                    withContext(Dispatchers.Main) {
                        if (title != null) this@apply.textViewTitle.text = title
                        if (thumbnail != null) ImageHelper.loadImage(thumbnail, this@apply.thumbnail)
                    }
                }
            }
        }
    }

    companion object {
        private const val NORMAL_TYPE = 0
        private const val CAUGHT_UP_TYPE = 1

        const val CAUGHT_UP_STREAM_TYPE = "caught"
    }
}