package com.watube.yard.ui.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.FragmentSubscriptionsBinding
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.db.DatabaseHolder
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.obj.SelectableOption
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.adapters.VideoCardsAdapter
import com.watube.yard.ui.base.DynamicLayoutManagerFragment
import com.watube.yard.ui.models.SubscriptionsViewModel
import com.watube.yard.ui.sheets.ChannelGroupsSheet
import com.watube.yard.ui.sheets.FilterSortBottomSheet
import com.watube.yard.ui.sheets.FilterSortBottomSheet.Companion.FILTER_SORT_REQUEST_KEY
import com.watube.yard.ui.sheets.SubscriptionsBottomSheet
import com.watube.yard.util.PlayingQueue
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SubscriptionsFragment : DynamicLayoutManagerFragment(R.layout.fragment_subscriptions) {
    private var _binding: FragmentSubscriptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubscriptionsViewModel by activityViewModels()

    // -1: all
    // -2: ungrouped
    private var selectedFilterGroup
        set(value) = PreferenceHelper.putInt(PreferenceKeys.SELECTED_CHANNEL_GROUP, value)
        get() = PreferenceHelper.getInt(PreferenceKeys.SELECTED_CHANNEL_GROUP, -1)

    private var isAppBarFullyExpanded = true

    /** guards the feed progress bar hide animation against stale, still-pending end actions */
    private var feedProgressAnimToken = 0

    private var feedAdapter = VideoCardsAdapter()
    private var selectedSortOrder = PreferenceHelper.getInt(PreferenceKeys.FEED_SORT_ORDER, 0)
        set(value) {
            PreferenceHelper.putInt(PreferenceKeys.FEED_SORT_ORDER, value)
            field = value
        }

    private var hideWatched =
        PreferenceHelper.getBoolean(PreferenceKeys.HIDE_WATCHED_FROM_FEED, false)
        set(value) {
            PreferenceHelper.putBoolean(PreferenceKeys.HIDE_WATCHED_FROM_FEED, value)
            field = value
        }

    private var showUpcoming =
        PreferenceHelper.getBoolean(PreferenceKeys.SHOW_UPCOMING_IN_FEED, true)
        set(value) {
            PreferenceHelper.putBoolean(PreferenceKeys.SHOW_UPCOMING_IN_FEED, value)
            field = value
        }

    override fun setLayoutManagers(gridItems: Int) {
        _binding?.subFeed?.layoutManager = GridLayoutManager(context, gridItems)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSubscriptionsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        setupSortAndFilter()

        binding.subFeed.adapter = feedAdapter

        // Check if the AppBarLayout is fully expanded
        binding.subscriptionsAppBar.addOnOffsetChangedListener { _, verticalOffset ->
            isAppBarFullyExpanded = verticalOffset == 0
        }

        // Determine if the child can scroll up
        binding.subRefresh.setOnChildScrollUpCallback { _, _ ->
            !isAppBarFullyExpanded || binding.subFeed.canScrollVertically(-1)
        }

        binding.subRefresh.isEnabled = true
        binding.subProgress.isVisible = true

        if (viewModel.videoFeed.value == null) {
            viewModel.fetchFeed(requireContext(), forceRefresh = false)
        }

        // only restore the previous state (i.e. scroll position) the first time the feed is shown
        // any other feed updates are caused by manual refreshing and thus should reset the scroll
        // position to zero
        var alreadyShowedFeedOnce = false
        viewModel.videoFeed.observe(viewLifecycleOwner) { feed ->
            if (feed != null) {
                lifecycleScope.launch {
                    showFeed(!alreadyShowedFeedOnce)
                    // the "all caught up" separator inside showFeed must be computed with the
                    // PREVIOUS seen timestamp, so the new one is written only afterwards
                    feed.firstOrNull { !it.isUpcoming }?.uploaded?.let {
                        PreferenceHelper.updateLastFeedWatchedTime(it, true)
                    }
                }
                alreadyShowedFeedOnce = true
            }

            // ungrouped chip is hidden if the user doesn't use channel groups
            binding.chipUngrouped.isVisible = !viewModel.groups.value.isNullOrEmpty()
                    && filterUngroupedStreamItems(feed.orEmpty()).isNotEmpty()
        }

        viewModel.feedProgress.observe(viewLifecycleOwner) { progress ->
            // Every update invalidates a hide animation that is still in flight: its
            // end action is still scheduled (it also runs after cancel()) and used to
            // hide the bar again one frame after a new progress had re-shown it.
            val loading = progress != null && progress.currentProgress < progress.total
            if (loading) {
                feedProgressAnimToken++
                binding.feedProgressContainer.animate().cancel()
                binding.feedProgressContainer.isVisible = true
                binding.feedProgressContainer.alpha = 1f
                binding.feedProgressContainer.scaleY = 1f
                binding.feedProgressText.text = "${progress.currentProgress}/${progress.total}"
                if (progress.total > 0) {
                    binding.feedProgressBar.max = progress.total
                    binding.feedProgressBar.progress = progress.currentProgress
                }
            } else {
                if (!binding.feedProgressContainer.isVisible) return@observe

                val token = ++feedProgressAnimToken
                binding.feedProgressContainer.animate()
                    .alpha(0.5f)
                    .scaleY(0.5f)
                    .withEndAction {
                        val binding = _binding ?: return@withEndAction
                        if (token != feedProgressAnimToken) return@withEndAction
                        binding.feedProgressContainer.isGone = true
                        binding.feedProgressContainer.scaleY = 1f
                        binding.feedProgressContainer.alpha = 1f
                    }
                    .setDuration(200)
                    .start()
            }
        }

        binding.subRefresh.setOnRefreshListener {
            viewModel.fetchFeed(requireContext(), forceRefresh = true)
        }

        // header button of the mockup's .shd row: same refresh action as the swipe
        binding.headerRefresh.setOnClickListener {
            viewModel.fetchFeed(requireContext(), forceRefresh = true)
        }

        binding.toggleSubs.isVisible = true

        binding.toggleSubs.setOnClickListener {
            SubscriptionsBottomSheet()
                .show(childFragmentManager)
        }

        binding.channelGroups.setOnCheckedStateChangeListener { group, _ ->
            selectedFilterGroup = group.children.indexOfFirst { it.id == group.checkedChipId } - 1 // 0th index is "all" button

            lifecycleScope.launch {
                showFeed(restoreScrollState = false)
            }
        }

        viewModel.groups.observe(viewLifecycleOwner) {
            lifecycleScope.launch { initChannelGroups() }
        }

        binding.editGroups.setOnClickListener {
            ChannelGroupsSheet().show(childFragmentManager, null)
        }

        binding.subFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                viewModel.subFeedRecyclerViewState =
                    recyclerView.layoutManager?.onSaveInstanceState()?.takeIf {
                        recyclerView.computeVerticalScrollOffset() != 0
                    }
            }
        })

        lifecycleScope.launch(Dispatchers.IO) {
            val groups = DatabaseHolder.Database.subscriptionGroupsDao().getAll()
                .sortedBy { it.index }
            viewModel.groups.postValue(groups)
        }
    }

    private fun setupSortAndFilter() {
        binding.filterSort.setOnClickListener {
            childFragmentManager.setFragmentResultListener(
                FILTER_SORT_REQUEST_KEY,
                viewLifecycleOwner
            ) { _, resultBundle ->
                selectedSortOrder = resultBundle.getInt(IntentData.sortOptions)
                hideWatched = resultBundle.getBoolean(IntentData.hideWatched)
                showUpcoming = resultBundle.getBoolean(IntentData.showUpcoming)
                lifecycleScope.launch { showFeed() }
            }

            FilterSortBottomSheet()
                .apply {
                    arguments = bundleOf(
                        IntentData.sortOptions to fetchSortOptions(),
                        IntentData.hideWatched to hideWatched,
                        IntentData.showUpcoming to showUpcoming,
                    )
                }
                .show(childFragmentManager)
        }
    }

    private fun fetchSortOptions(): List<SelectableOption> {
        return resources.getStringArray(R.array.sortOptions)
            .mapIndexed { index, option ->
                SelectableOption(isSelected = index == selectedSortOrder, name = option)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun playByGroup(groupIndex: Int) {
        val streams = viewModel.videoFeed.value.orEmpty()
            .filterByGroup(groupIndex)
            .let {
                DatabaseHelper.filterByStreamTypeAndWatchPosition(it, hideWatched, showUpcoming)
            }
            .sortedBySelectedOrder()

        if (streams.isEmpty()) return

        PlayingQueue.setStreams(streams)

        NavigationHelper.navigateVideo(
            requireContext(),
            playerData = PlayerData(
                videoId = streams.first().url,
                keepQueue = true
            )
        )
    }

    private fun filterUngroupedStreamItems(streamItems: List<StreamItem>): List<StreamItem> {
        val groups = viewModel.groups.value.orEmpty()

        return streamItems.filter { streamItem ->
            groups.none { it.channels.contains(streamItem.uploaderUrl.orEmpty().toID()) }
        }
    }

    @SuppressLint("InflateParams")
    private fun initChannelGroups() {
        val binding = _binding ?: return

        val groups = viewModel.groups.value.orEmpty()

        binding.chipAll.isChecked = selectedFilterGroup == -1
        binding.chipAll.setOnLongClickListener {
            lifecycleScope.launch { playByGroup(0) }
            true
        }

        binding.chipUngrouped.isChecked = selectedFilterGroup == -2
        binding.chipUngrouped.setOnLongClickListener {
            lifecycleScope.launch { playByGroup(-1) }
            true
        }

        binding.channelGroups.removeAllViews()
        binding.channelGroups.addView(binding.chipAll)

        groups.forEachIndexed { index, group ->
            val chip = layoutInflater.inflate(R.layout.filter_chip, null) as Chip
            chip.apply {
                id = View.generateViewId()
                isCheckable = true
                text = group.name
                // filter_chip.xml is not part of the editable shell file set, so the
                // mockup metrics carried by @style/Watube.Chip.Subs are mirrored here
                setTextAppearance(R.style.WatubeChipLabel)
                setChipMinHeight(resources.getDimension(R.dimen.watube_chip_min_height))
                setChipStartPadding(0f)
                setTextStartPadding(resources.getDimension(R.dimen.watube_chip_h_padding))
                setTextEndPadding(resources.getDimension(R.dimen.watube_chip_h_padding))
                setChipEndPadding(0f)
                setOnLongClickListener {
                    // the index must be increased by one to skip the "all channels" group button
                    lifecycleScope.launch { playByGroup(index + 1) }
                    true
                }
            }

            binding.channelGroups.addView(chip)

            if (index == selectedFilterGroup) binding.channelGroups.check(chip.id)
        }

        // only show "ungrouped" chip category if there actually are any ungrouped subscriptions
        // not sure if it's worth loading them here
        binding.channelGroups.addView(binding.chipUngrouped)
    }

    private fun List<StreamItem>.filterByGroup(groupIndex: Int): List<StreamItem> {
        if (groupIndex == -1) return this
        if (groupIndex == -2) return filterUngroupedStreamItems(this)

        val group = viewModel.groups.value?.getOrNull(groupIndex)
        return filter {
            val channelId = it.uploaderUrl.orEmpty().toID()
            group?.channels?.contains(channelId) != false
        }
    }

    private fun List<StreamItem>.sortedBySelectedOrder() = when (selectedSortOrder) {
        // "most recent" must actually sort: the piped repositories return the api order
        0 -> this.sortedByDescending { it.uploaded }
        1 -> this.sortedBy { it.uploaded }
        2 -> this.sortedBy { it.views }.reversed()
        3 -> this.sortedBy { it.views }
        4 -> this.sortedBy { it.uploaderName }
        5 -> this.sortedBy { it.uploaderName }.reversed()
        else -> this
    }

    private suspend fun showFeed(restoreScrollState: Boolean = true) {
        val binding = _binding ?: return
        val videoFeed = viewModel.videoFeed.value ?: return

        val feed = videoFeed
            .filterByGroup(selectedFilterGroup)
            .let {
                DatabaseHelper.filterByStreamTypeAndWatchPosition(it, hideWatched, showUpcoming)
            }

        val sortedFeed = feed
            .sortedBySelectedOrder()
            .toMutableList()

        // add an "all caught up item"
        if (selectedSortOrder == 0) {
            val lastCheckedFeedTime = PreferenceHelper.getLastCheckedFeedTime(seenByUser = true)
            // the index must be looked up in the list that is actually displayed, otherwise
            // the separator lands at a wrong position for unsorted (piped) feeds
            val caughtUpIndex =
                sortedFeed.indexOfFirst { it.uploaded <= lastCheckedFeedTime && !it.isUpcoming }
            if (caughtUpIndex > 0 && !sortedFeed[caughtUpIndex - 1].isUpcoming) {
                sortedFeed.add(
                    caughtUpIndex,
                    StreamItem(type = VideoCardsAdapter.CAUGHT_UP_STREAM_TYPE)
                )
            }
        }

        binding.subProgress.isGone = true

        val notLoaded = viewModel.videoFeed.value.isNullOrEmpty()
        binding.subFeed.isGone = notLoaded
        binding.emptyFeed.isVisible = notLoaded

        binding.subRefresh.isRefreshing = false

        feedAdapter.submitList(sortedFeed) {
            if (restoreScrollState) {
                // manually restore the previous feed state
                binding.subFeed.layoutManager?.onRestoreInstanceState(viewModel.subFeedRecyclerViewState)
            } else {
                binding.subFeed.scrollToPosition(0)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // manually restore the recyclerview state after rotation due to https://github.com/material-components/material-components-android/issues/3473
        binding.subFeed.layoutManager?.onRestoreInstanceState(viewModel.subFeedRecyclerViewState)
    }

    fun removeItem(videoId: String) {
        feedAdapter.removeItemById(videoId)
    }
}
