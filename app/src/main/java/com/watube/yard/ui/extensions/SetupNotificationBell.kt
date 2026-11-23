package com.watube.yard.ui.extensions

import androidx.core.view.isGone
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import com.google.android.material.button.MaterialButton

// region New Video Notifications
fun MaterialButton.setupNotificationBell(channelId: String) {
    if (!PreferenceHelper.getBoolean(PreferenceKeys.NOTIFICATION_ENABLED, true)) {
        isGone = true
        return
    }

    var isIgnorable = PreferenceHelper.isChannelNotificationIgnorable(channelId)
    showBellState(isIgnorable)

    setOnClickListener {
        isIgnorable = !isIgnorable
        PreferenceHelper.toggleIgnorableNotificationChannel(channelId)
        showBellState(isIgnorable)
    }
}

/** Shows whether the channel's new videos are notified: by the icon, and in words for screen readers. */
private fun MaterialButton.showBellState(isIgnorable: Boolean) {
    setIconResource(iconResource(isIgnorable))
    stateDescription = context.getString(if (isIgnorable) R.string.disabled else R.string.enabled)
}

private fun iconResource(isIgnorable: Boolean) =
    if (isIgnorable) R.drawable.ic_bell else R.drawable.ic_notification
// endregion
