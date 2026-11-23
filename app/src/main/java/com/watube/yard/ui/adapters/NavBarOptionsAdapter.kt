package com.watube.yard.ui.adapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.databinding.NavOptionsItemBinding
import com.watube.yard.helpers.NavBarHelper
import com.watube.yard.ui.dialogs.NavBarItem
import com.watube.yard.ui.viewholders.NavBarOptionsViewHolder

// region Navigation
class NavBarOptionsAdapter(
    val items: MutableList<NavBarItem>,
    var selectedHomeTabId: Int
) : RecyclerView.Adapter<NavBarOptionsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavBarOptionsViewHolder {
        val binding = NavOptionsItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NavBarOptionsViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: NavBarOptionsViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            title.text = item.title
            checkbox.isChecked = item.isVisible
            // settings can be neither hidden nor moved (see NavBarHelper.isPinned)
            val isPinned = NavBarHelper.isPinned(item.itemId)
            checkbox.isEnabled = !isPinned
            dragView.isInvisible = isPinned
            // nor be the tab the app opens on
            home.isInvisible = isPinned
            home.setImageResource(
                if (item.itemId == selectedHomeTabId) R.drawable.ic_home_dark else R.drawable.ic_home_outlined
            )
            home.setOnClickListener {
                if (selectedHomeTabId == item.itemId) {
                    return@setOnClickListener
                }
                val oldSelection = items.indexOfFirst { it.itemId == selectedHomeTabId }
                selectedHomeTabId = item.itemId
                listOf(position, oldSelection).forEach {
                    notifyItemChanged(it)
                }
            }
            checkbox.setOnClickListener {
                // the bottom bar holds four tabs next to the two pinned ones
                val visibleUserTabs = items.count { it.isVisible && !NavBarHelper.isPinned(it.itemId) }
                if (checkbox.isChecked && visibleUserTabs >= NavBarHelper.MAX_VISIBLE_USER_TABS) {
                    checkbox.isChecked = false
                    Toast.makeText(
                        root.context,
                        root.context.getString(
                            R.string.navbar_max_tabs, NavBarHelper.MAX_VISIBLE_USER_TABS
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                item.isVisible = checkbox.isChecked
            }
            describeRow(isPinned, isStartTab = item.itemId == selectedHomeTabId)
        }
    }

    /**
     * The row is read and used as one checkbox: its title, whether the tab shows in the bottom
     * bar, and whether the app opens on it. Opening the app on it is an action of its own, as the
     * small house icon is for touch.
     */
    private fun NavOptionsItemBinding.describeRow(isPinned: Boolean, isStartTab: Boolean) {
        home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        checkbox.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val context = root.context
        ViewCompat.setAccessibilityDelegate(root, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = CheckBox::class.java.name
                info.isCheckable = true
                info.isChecked = checkbox.isChecked
                info.isEnabled = checkbox.isEnabled
                info.contentDescription = if (isStartTab) {
                    "${title.text}, ${context.getString(R.string.a11y_start_tab)}"
                } else {
                    title.text
                }
                if (checkbox.isEnabled) info.addAction(AccessibilityActionCompat.ACTION_CLICK)
                if (!isPinned && !isStartTab) {
                    info.addAction(
                        AccessibilityActionCompat(R.id.a11y_set_start_tab, context.getString(R.string.a11y_set_start_tab))
                    )
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?) = when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> checkbox.isEnabled && checkbox.performClick().also {
                    // the box itself is hidden from screen readers: the row tells its new state
                    host.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
                }
                R.id.a11y_set_start_tab -> home.performClick()
                else -> super.performAccessibilityAction(host, action, args)
            }
        })
    }
}
// endregion
