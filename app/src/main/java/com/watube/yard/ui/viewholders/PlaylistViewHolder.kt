package com.watube.yard.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.VideoRowBinding
import kotlinx.coroutines.Job

class PlaylistViewHolder(
    val binding: VideoRowBinding
) : RecyclerView.ViewHolder(binding.root) {
    var bindJob: Job? = null
}
