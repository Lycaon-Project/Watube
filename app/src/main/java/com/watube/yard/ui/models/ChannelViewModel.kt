package com.watube.yard.ui.models

import androidx.lifecycle.ViewModel
import com.watube.yard.api.obj.StreamItem

// region Channels
class ChannelViewModel : ViewModel() {
    // These are used for the first tab ("Videos") if they were pre-fetched with the channel
    var relatedStreams: List<StreamItem>? = null
    var nextPage: String? = null
}
// endregion
