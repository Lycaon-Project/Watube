package com.watube.yard.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.AllCaughtUpRowBinding
import com.watube.yard.databinding.TrendingRowBinding
import kotlinx.coroutines.Job

class VideoCardsViewHolder : RecyclerView.ViewHolder {
    var trendingRowBinding: TrendingRowBinding? = null
    var allCaughtUpBinding: AllCaughtUpRowBinding? = null

    /** Async work started while binding this row; cancelled when the row is recycled. */
    var bindJob: Job? = null

    constructor(binding: TrendingRowBinding) : super(binding.root) {
        trendingRowBinding = binding
    }

    constructor(binding: AllCaughtUpRowBinding) : super(binding.root) {
        allCaughtUpBinding = binding
    }
}
