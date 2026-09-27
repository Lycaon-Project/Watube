package com.watube.yard.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.ChannelRowBinding
import com.watube.yard.databinding.PlaylistsRowBinding
import com.watube.yard.databinding.VideoRowBinding
import kotlinx.coroutines.Job

class SearchViewHolder : RecyclerView.ViewHolder {
    var videoRowBinding: VideoRowBinding? = null
    var channelRowBinding: ChannelRowBinding? = null
    var playlistRowBinding: PlaylistsRowBinding? = null

    /** Async work started while binding this row; cancelled when the row is recycled. */
    var bindJob: Job? = null

    constructor(binding: VideoRowBinding) : super(binding.root) {
        videoRowBinding = binding
    }

    constructor(binding: ChannelRowBinding) : super(binding.root) {
        channelRowBinding = binding
    }

    constructor(binding: PlaylistsRowBinding) : super(binding.root) {
        playlistRowBinding = binding
    }
}
