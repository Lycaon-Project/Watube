package com.watube.yard.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.databinding.ItemSubscriptionRailBinding
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.ThemeHelper

// region Feed
/**
 * A channel of the subscriptions rail.
 *
 * [newCount] is the number of its videos published since the user's previous visit.
 */
data class RailChannel(
    val id: String,
    val url: String,
    val name: String,
    val avatar: String?,
    val newCount: Int,
)

/**
 * Channels above the subscriptions feed. A tap selects a channel ([onSelect] receives it, or
 * null when the selected one is tapped again), a long press opens the channel page. With
 * [compact], for the pinned bar of the header, only the avatars show, smaller.
 */
class SubscriptionRailAdapter(
    private val onSelect: (RailChannel?) -> Unit,
    private val compact: Boolean = false,
) : ListAdapter<RailChannel, SubscriptionRailAdapter.RailViewHolder>(RailDiff) {

    var selectedId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }

    class RailViewHolder(val binding: ItemSubscriptionRailBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RailViewHolder {
        val binding = ItemSubscriptionRailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        if (compact) binding.makeCompact()
        return RailViewHolder(binding)
    }

    /** Smaller avatars without their names; screen readers still get the name from the row. */
    private fun ItemSubscriptionRailBinding.makeCompact() {
        val res = root.resources
        root.updateLayoutParams {
            width = res.getDimensionPixelSize(R.dimen.watube_rail_item_width_compact)
        }
        (railAvatar.parent as View).updateLayoutParams {
            width = res.getDimensionPixelSize(R.dimen.watube_rail_avatar_size_compact)
            height = width
        }
        railName.isGone = true
    }

    override fun onBindViewHolder(holder: RailViewHolder, position: Int) {
        val channel = getItem(position)
        holder.binding.apply {
            railName.text = channel.name
            ImageHelper.loadImage(channel.avatar, railAvatar, true)

            railBadge.isVisible = channel.newCount > 0
            railBadge.text = if (channel.newCount > MAX_BADGE_COUNT) {
                root.context.getString(R.string.subs_badge_overflow, MAX_BADGE_COUNT)
            } else {
                channel.newCount.toString()
            }

            root.contentDescription = if (channel.newCount > 0) {
                root.resources.getQuantityString(
                    R.plurals.subs_rail_new_videos, channel.newCount, channel.name, channel.newCount
                )
            } else {
                channel.name
            }
            ViewCompat.setAccessibilityDelegate(root, RailActionsDelegate)

            root.setOnClickListener {
                onSelect(channel.takeUnless { it.id == selectedId })
            }
            root.setOnLongClickListener {
                NavigationHelper.navigateChannel(root.context, channel.url)
                true
            }
        }
        bindSelection(holder, channel)
    }

    override fun onBindViewHolder(
        holder: RailViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            bindSelection(holder, getItem(position))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    /** The selected channel takes the accent, the others step back while one is selected. */
    private fun bindSelection(holder: RailViewHolder, channel: RailChannel) {
        val selected = channel.id == selectedId
        val context = holder.binding.root.context
        holder.binding.apply {
            if (root.isSelected != selected) {
                root.isSelected = selected
                root.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
            }
            root.alpha = if (selectedId != null && !selected) DIMMED_ALPHA else 1f

            val ringAttr = if (selected || channel.newCount > 0) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOutlineVariant
            }
            railAvatar.strokeColor =
                ColorStateList.valueOf(ThemeHelper.getThemeColor(context, ringAttr))
            railAvatar.strokeWidth = context.resources.getDimension(
                if (selected) R.dimen.watube_rail_ring_selected else R.dimen.watube_rail_ring
            )

            val nameAttr = if (selected) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            railName.setTextColor(ThemeHelper.getThemeColor(context, nameAttr))
            railName.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /** Names the tap and long press actions and reports the selection to screen readers. */
    private object RailActionsDelegate : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.isSelected = host.isSelected
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_CLICK,
                    host.context.getString(R.string.subs_rail_action_show)
                )
            )
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    host.context.getString(R.string.subs_rail_action_open)
                )
            )
        }
    }

    private object RailDiff : DiffUtil.ItemCallback<RailChannel>() {
        override fun areItemsTheSame(oldItem: RailChannel, newItem: RailChannel) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RailChannel, newItem: RailChannel) =
            oldItem == newItem
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private const val MAX_BADGE_COUNT = 99
        private const val DIMMED_ALPHA = 0.55f
    }
}
// endregion
