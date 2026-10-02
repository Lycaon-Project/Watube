package com.watube.yard.ui.sheets

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.databinding.DialogAddChannelToGroupBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.ui.adapters.AddChannelToGroupAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.fragment.app.activityViewModels
import com.watube.yard.ui.models.SubscriptionsViewModel

class AddChannelToGroupSheet : ExpandedBottomSheet(R.layout.dialog_add_channel_to_group) {
    private lateinit var channelId: String

    private val viewModel: SubscriptionsViewModel by activityViewModels()

    private val addToGroupAdapter by lazy(LazyThreadSafetyMode.NONE) {
        AddChannelToGroupAdapter(channelId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        channelId = arguments?.getString(IntentData.channelId)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = DialogAddChannelToGroupBinding.bind(view)

        binding.groupsRV.adapter = addToGroupAdapter

        binding.cancel.setOnClickListener {
            requireDialog().dismiss()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val subGroupsDao = DatabaseHolder.Database.subscriptionGroupsDao()
            val subscriptionGroups = subGroupsDao.getAll().sortedBy { it.index }.toMutableList()

            withContext(Dispatchers.Main) {
                addToGroupAdapter.submitList(subscriptionGroups)

                binding.okay.setOnClickListener {
                    requireDialog().hide()

                    lifecycleScope.launch(Dispatchers.IO) {
                        subGroupsDao.updateAll(subscriptionGroups)

                        viewModel.groups.postValue(subscriptionGroups)

                        withContext(Dispatchers.Main) {
                            dialog?.dismiss()
                        }
                    }
                }
            }
        }
    }
}
