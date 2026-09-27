package com.watube.yard.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.VideoRowBinding

class VideosViewHolder(val binding: VideoRowBinding) : RecyclerView.ViewHolder(binding.root) {
    /** Async work started while binding this row; cancelled when the row is recycled. */
    var bindJob: kotlinx.coroutines.Job? = null
}