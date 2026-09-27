package com.watube.yard.ui.extensions

import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.watube.yard.R
import com.watube.yard.api.SubscriptionHelper
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun TextView.setupSubscriptionButton(
    channelId: String?,
    channelName: String,
    channelAvatar: String?,
    channelVerified: Boolean,
    notificationBell: MaterialButton? = null,
    isSubscribed: Boolean? = null,
    onIsSubscribedChange: (Boolean) -> Unit = {}
) {
    if (channelId == null) return

    // remember which channel this button shows: a recycled row must never receive the
    // subscription state of another channel
    setTag(R.id.bound_item_id, channelId)

    val notificationsEnabled = PreferenceHelper
        .getBoolean(PreferenceKeys.NOTIFICATION_ENABLED, true)
    var subscribed = false

    fun updateUIStateAndNotifyObservers() {
        onIsSubscribedChange(subscribed)

        this@setupSubscriptionButton.text =
            if (subscribed) context.getString(R.string.unsubscribe)
            else context.getString(R.string.subscribe)

        notificationBell?.isVisible = subscribed && notificationsEnabled
        this@setupSubscriptionButton.isVisible = true
    }

    // bound to the screen when there is one, so no request outlives it
    val scope = findViewTreeLifecycleOwner()?.lifecycleScope
        ?: CoroutineScope(Dispatchers.Main)

    // a tap decides the state: a late answer of the initial lookup must never overwrite it
    var userDecided = false

    scope.launch(Dispatchers.IO) {
        val remote = isSubscribed ?: SubscriptionHelper.isSubscribed(channelId) ?: false

        withContext(Dispatchers.Main) {
            if (getTag(R.id.bound_item_id) != channelId || userDecided) return@withContext
            subscribed = remote
            updateUIStateAndNotifyObservers()
        }
    }

    notificationBell?.setupNotificationBell(channelId)

    val setSubscriptionState : (Boolean) -> Unit = { subscribe ->
        userDecided = true
        CoroutineScope(Dispatchers.IO).launch {
            if (subscribe)
                SubscriptionHelper.subscribe(
                    channelId,
                    channelName,
                    channelAvatar,
                    channelVerified
                )
            else
                SubscriptionHelper.unsubscribe(channelId)
        }
        subscribed = subscribe

        updateUIStateAndNotifyObservers()
    }

    setOnClickListener {
        CoroutineScope(Dispatchers.Main).launch {
            if (subscribed) {
                Snackbar
                    .make(
                        context,
                        rootView,
                        context.getString(R.string.unsubscribe_snackbar_message, channelName),
                        1000
                    )
                    .setAction(R.string.undo, {
                        setSubscriptionState(true)
                    }).show()
            }
            setSubscriptionState(!subscribed)
        }
    }
}