package com.watube.yard.ui.sheets

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.watube.yard.R
import com.watube.yard.api.obj.Subscription
import com.watube.yard.databinding.DialogEditChannelGroupBinding
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.db.obj.SubscriptionGroup
import com.watube.yard.ui.adapters.SubscriptionGroupChannelsAdapter
import com.watube.yard.ui.models.SubscriptionsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditChannelGroupSheet : ExpandedBottomSheet(R.layout.dialog_edit_channel_group) {
    private var _binding: DialogEditChannelGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubscriptionsViewModel by activityViewModels()
    private var channels = listOf<Subscription>()

    private lateinit var channelsAdapter: SubscriptionGroupChannelsAdapter

    /** last name validation, cancelled as soon as the user types again */
    private var nameValidationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = DialogEditChannelGroupBinding.bind(view)

        channelsAdapter = SubscriptionGroupChannelsAdapter(
            viewModel.groupToEdit!!
        ) {
            viewModel.groupToEdit = it
            updateConfirmStatus()
        }

        binding.channelsRV.adapter = channelsAdapter
        binding.groupName.setText(viewModel.groupToEdit?.name)
        val oldGroupName = viewModel.groupToEdit?.name.orEmpty()

        binding.channelsRV.layoutManager = LinearLayoutManager(context)
        binding.channelsRV.setHasFixedSize(true)

        binding.groupName.addTextChangedListener {
            updateConfirmStatus()
        }

        binding.searchInput.addTextChangedListener {
            showChannels(channels, it?.toString())
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }

        updateConfirmStatus()
        binding.confirm.setOnClickListener {
            val updatedGroup = viewModel.groupToEdit?.copy(
                name = binding.groupName.text.toString().ifEmpty { return@setOnClickListener }
            ) ?: return@setOnClickListener
            saveGroup(updatedGroup, oldGroupName)

            dismiss()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch(Dispatchers.IO) {
                    viewModel.fetchSubscriptions(requireContext())
                }
                launch {
                    viewModel.subscriptions.asFlow().collectLatest { subscriptions ->
                        subscriptions?.let {
                            channels = it
                            showChannels(it, null)
                        }
                    }
                }
            }
        }
    }

    private fun saveGroup(group: SubscriptionGroup, oldGroupName: String) {
        // delete the old instance if the group already existed and add the updated/new one
        viewModel.groups.value = viewModel.groups.value
            ?.filter { it.name != oldGroupName }
            ?.plus(group)

        CoroutineScope(Dispatchers.IO).launch {
            // delete the old version of the group first before updating it, as the name is the
            // primary key
            DatabaseHolder.Database.subscriptionGroupsDao().deleteGroup(oldGroupName)
            DatabaseHolder.Database.subscriptionGroupsDao().createGroup(group)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showChannels(channels: List<Subscription>, query: String?) {
        binding.subscriptionsContainer.isVisible = true
        binding.progress.isVisible = false

        channelsAdapter.submitList(
            channels.filter { query == null || it.name.lowercase().contains(query.lowercase()) }
        )
    }

    private fun updateConfirmStatus() {
        with(binding) {
            val name = groupName.text.toString()

            // the uniqueness check reads the database: never block the keystroke on it,
            // only the latest validation may update the field
            nameValidationJob?.cancel()
            nameValidationJob = viewLifecycleOwner.lifecycleScope.launch {
                val groupExists = if (name.isBlank()) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        DatabaseHolder.Database.subscriptionGroupsDao().exists(name)
                    }
                }

                val error = when {
                    name.isBlank() -> getString(R.string.group_name_error_empty)
                    groupExists && viewModel.groupToEdit?.name != name ->
                        getString(R.string.group_name_error_exists)
                    else -> null
                }

                groupName.error = error
                confirm.isEnabled =
                    error == null && !viewModel.groupToEdit?.channels.isNullOrEmpty()
            }
        }
    }
}
