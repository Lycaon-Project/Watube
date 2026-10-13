package com.watube.yard.ui.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.databinding.AllCaughtUpRowBinding
import com.watube.yard.databinding.TrendingRowBinding
import kotlinx.coroutines.Job

// region UI Components
class VideoCardsViewHolder : RecyclerView.ViewHolder {
    var trendingRowBinding: TrendingRowBinding? = null
        private set
    var allCaughtUpBinding: AllCaughtUpRowBinding? = null
        private set

    /** Async work started while binding this row; cancelled when the row is recycled. */
    var bindJob: Job? = null

    constructor(binding: TrendingRowBinding) : super(binding.root) {
        trendingRowBinding = binding
    }

    constructor(binding: AllCaughtUpRowBinding) : super(binding.root) {
        allCaughtUpBinding = binding
    }
}
// endregion
