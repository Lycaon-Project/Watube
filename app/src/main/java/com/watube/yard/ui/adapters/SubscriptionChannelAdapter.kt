package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.api.obj.Subscription
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.ChannelSubscriptionRowBinding
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.ContextHelper
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.extensions.setupSubscriptionButton
import com.watube.yard.ui.sheets.ChannelOptionsBottomSheet
import com.watube.yard.ui.viewholders.SubscriptionChannelViewHolder

class SubscriptionChannelAdapter :
    ListAdapter<Subscription, SubscriptionChannelViewHolder>(
        DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem ->
            oldItem.url == newItem.url
        })
    ) {

    // Track recently unsubscribed channels to preserve their unsubscribed state when
    // [onBindViewHolder] is re-called on these channels while scrolling the [RecyclerView]
    private val recentlyUnsubscribedList = mutableListOf<String>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SubscriptionChannelViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ChannelSubscriptionRowBinding.inflate(layoutInflater, parent, false)
        return SubscriptionChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionChannelViewHolder, position: Int) {
        val subscription = getItem(holder.bindingAdapterPosition)

        holder.binding.apply {
            subscriptionChannelName.text = subscription.name
            ImageHelper.loadImage(subscription.avatar, subscriptionChannelImage, true)

            root.setOnClickListener {
                NavigationHelper.navigateChannel(root.context, subscription.url)
            }
            root.setOnLongClickListener {
                val channelOptionsSheet = ChannelOptionsBottomSheet()
                channelOptionsSheet.arguments = bundleOf(
                    IntentData.channelId to subscription.url.toID(),
                    IntentData.channelName to subscription.name,
                    IntentData.isSubscribed to true
                )
                val activity = ContextHelper.unwrapActivity<BaseActivity>(root.context)
                channelOptionsSheet.show(activity.supportFragmentManager)
                true
            }

            val channelId = subscription.url.toID()
            val isRecentlyUnsubscribed = recentlyUnsubscribedList.any { it == channelId }
            subscriptionSubscribe.setupSubscriptionButton(
                channelId,
                subscription.name,
                subscription.avatar,
                subscription.verified,
                notificationBell,
                !isRecentlyUnsubscribed
            ) { isSubscribed ->
                when (isSubscribed) {
                    true -> if (isRecentlyUnsubscribed) recentlyUnsubscribedList.remove(channelId)
                    false -> if (!isRecentlyUnsubscribed) recentlyUnsubscribedList.add(channelId)
                }
            }
        }
    }
}
