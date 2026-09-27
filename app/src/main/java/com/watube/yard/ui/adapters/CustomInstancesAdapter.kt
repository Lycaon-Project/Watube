package com.watube.yard.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.watube.yard.databinding.CustomInstanceRowBinding
import com.watube.yard.db.obj.CustomInstance
import com.watube.yard.ui.adapters.callbacks.DiffUtilItemCallback
import com.watube.yard.ui.viewholders.CustomInstancesViewHolder

class CustomInstancesAdapter(
    private val onClickInstance: (CustomInstance) -> Unit,
    private val onDeleteInstance: (CustomInstance) -> Unit
) : ListAdapter<CustomInstance, CustomInstancesViewHolder>(
    DiffUtilItemCallback(areItemsTheSame = { oldItem, newItem ->
        oldItem.name == newItem.name
    })
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomInstancesViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = CustomInstanceRowBinding.inflate(layoutInflater, parent, false)
        return CustomInstancesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomInstancesViewHolder, position: Int) {
        val instance = getItem(position)!!

        with (holder.binding) {
            instanceName.text = instance.name

            root.setOnClickListener {
                onClickInstance(instance)
            }

            deleteInstance.setOnClickListener {
                onDeleteInstance.invoke(instance)
            }
        }
    }
}