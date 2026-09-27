package com.watube.yard.obj

import android.graphics.Bitmap
import com.watube.yard.api.obj.Streams

data class DownloadedFile(
    val name: String,
    val size: Long,
    var metadata: Streams? = null,
    var thumbnail: Bitmap? = null
)
