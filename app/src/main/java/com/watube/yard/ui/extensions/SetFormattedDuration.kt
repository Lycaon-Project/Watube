package com.watube.yard.ui.extensions

import android.text.format.DateUtils
import android.widget.TextView
import com.watube.yard.R

// region UI Components
fun TextView.setFormattedDuration(duration: Long, isShort: Boolean?, uploadDate: Long) {
    this.text = when {
        isShort == true -> context.getString(R.string.yt_shorts)
        // before the live check: a scheduled live has no duration either, but is not on air
        uploadDate > System.currentTimeMillis() -> context.getString(R.string.upcoming)
        duration < 0L -> context.getString(R.string.live)
        else -> DateUtils.formatElapsedTime(duration)
    }
}
// endregion
