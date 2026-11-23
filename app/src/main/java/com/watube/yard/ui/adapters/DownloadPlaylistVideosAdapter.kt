package com.watube.yard.ui.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.databinding.ItemDownloadPlaylistVideoBinding
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ImageHelper

// region Downloads
/**
 * The videos of a playlist to download, each with its box. [checked] (video ids) belongs to
 * the sheet; [onToggle] hears each change made here.
 */
class DownloadPlaylistVideosAdapter(
    private val checked: MutableSet<String>,
    private val onToggle: () -> Unit
) : RecyclerView.Adapter<DownloadPlaylistVideosAdapter.VideoViewHolder>() {
    private var videos: List<StreamItem> = emptyList()

    class VideoViewHolder(val binding: ItemDownloadPlaylistVideoBinding) :
        RecyclerView.ViewHolder(binding.root)

    fun submit(newVideos: List<StreamItem>) {
        videos = newVideos
        @Suppress("NotifyDataSetChanged") // loaded once per sheet
        notifyDataSetChanged()
    }

    /** Every box shows [checked] again (after Select all / Select none). */
    fun refreshChecks() = notifyItemRangeChanged(0, videos.size, PAYLOAD_CHECKED)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemDownloadPlaylistVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding).also { holder ->
            binding.root.setOnClickListener {
                val video = videos.getOrNull(holder.bindingAdapterPosition) ?: return@setOnClickListener
                val id = video.url?.toID() ?: return@setOnClickListener
                if (!checked.remove(id)) checked.add(id)
                bindCheck(holder, id)
                onToggle()
            }
            // the whole row is one checkbox for the screen reader
            binding.root.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CheckBox::class.java.name
                    info.isCheckable = true
                    info.isChecked = binding.videoCheck.isChecked
                }
            }
        }
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        val id = video.url?.toID().orEmpty()
        with(holder.binding) {
            videoTitle.text = video.title
            val duration = video.duration?.takeIf { it > 0 }?.let(DateUtils::formatElapsedTime)
            videoMeta.text = listOfNotNull(duration, video.uploaderName).joinToString(" · ")
            ImageHelper.loadImage(video.thumbnail, videoThumbnail)
        }
        bindCheck(holder, id)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_CHECKED)) {
            bindCheck(holder, videos[position].url?.toID().orEmpty())
        } else {
            onBindViewHolder(holder, position)
        }
    }

    private fun bindCheck(holder: VideoViewHolder, id: String) {
        holder.binding.videoCheck.isChecked = id in checked
    }

    override fun getItemCount() = videos.size

    private companion object {
        const val PAYLOAD_CHECKED = "checked"
    }
}
// endregion
