package com.watube.yard.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.databinding.SubscriptionGroupRowBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.db.obj.SubscriptionGroup
import com.watube.yard.ui.models.SubscriptionsViewModel
import com.watube.yard.ui.sheets.EditChannelGroupSheet
import com.watube.yard.ui.viewholders.SubscriptionGroupsViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubscriptionGroupsAdapter(
    var groups: MutableList<SubscriptionGroup>,
    private val viewModel: SubscriptionsViewModel,
    private val parentFragmentManager: FragmentManager
) : RecyclerView.Adapter<SubscriptionGroupsViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SubscriptionGroupsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = SubscriptionGroupRowBinding.inflate(layoutInflater, parent, false)
        return SubscriptionGroupsViewHolder(binding)
    }

    override fun getItemCount() = groups.size

    override fun onBindViewHolder(holder: SubscriptionGroupsViewHolder, position: Int) {
        val subscriptionGroup = groups[position]
        holder.binding.apply {
            groupName.text = subscriptionGroup.name

            deleteGroup.setOnClickListener {
                showDeleteDialog(root.context, subscriptionGroup)
            }

            editGroup.setOnClickListener {
                viewModel.groupToEdit = subscriptionGroup
                EditChannelGroupSheet().show(parentFragmentManager, null)
            }
        }
    }

    private fun showDeleteDialog(context: Context, group: SubscriptionGroup) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete)
            .setMessage(R.string.irreversible)
            .setPositiveButton(R.string.okay) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseHolder.Database.subscriptionGroupsDao()
                        .deleteGroup(group.name)

                    withContext(Dispatchers.Main) {
                        // RecyclerView binds this list: only ever mutate it on the main thread
                        groups.removeAll { it.name == group.name }
                        viewModel.groups.postValue(groups.toList())
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
