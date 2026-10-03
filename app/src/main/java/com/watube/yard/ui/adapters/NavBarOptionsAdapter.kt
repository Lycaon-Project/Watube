package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.databinding.NavOptionsItemBinding
import com.watube.yard.helpers.NavBarHelper
import com.watube.yard.ui.dialogs.NavBarItem
import com.watube.yard.ui.viewholders.NavBarOptionsViewHolder

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
                item.isVisible = checkbox.isChecked
            }
        }
    }
}
