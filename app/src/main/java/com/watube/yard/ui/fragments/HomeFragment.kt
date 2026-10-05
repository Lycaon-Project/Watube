package com.watube.yard.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.R
import com.watube.yard.api.MediaServiceRepository
import com.watube.yard.api.TrendingCategory
import com.watube.yard.api.obj.Playlists
import com.watube.yard.api.obj.StreamItem
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.constants.PreferenceKeys.HOME_TAB_CONTENT
import com.watube.yard.databinding.FragmentHomeBinding
import com.watube.yard.db.obj.PlaylistBookmark
import com.watube.yard.extensions.dpToPx
import com.watube.yard.extensions.toID
import com.watube.yard.helpers.FeedDensity
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.activities.SettingsActivity
import com.watube.yard.ui.adapters.CarouselPlaylist
import com.watube.yard.ui.adapters.CarouselPlaylistAdapter
import com.watube.yard.ui.adapters.VideoCardsAdapter
import com.watube.yard.ui.models.HomeViewModel
import com.watube.yard.ui.models.SubscriptionsViewModel
import com.watube.yard.ui.models.TrendsViewModel
import com.watube.yard.util.TextUtils
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.CarouselSnapHelper
import com.google.android.material.carousel.UncontainedCarouselStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.Hold


/** Left inset of a feed row (trending_row / ItemRow), so card edges land on the gutter */
private const val CARD_INSET_DP = 8f

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val subscriptionsViewModel: SubscriptionsViewModel by activityViewModels()
    private val trendsViewModel: TrendsViewModel by activityViewModels()

    private val trendingAdapter = VideoCardsAdapter()
    private val feedAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val watchingAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val bookmarkAdapter = CarouselPlaylistAdapter()
    private val playlistAdapter = CarouselPlaylistAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        binding.bookmarksRV.layoutManager = CarouselLayoutManager(UncontainedCarouselStrategy())
        binding.playlistsRV.layoutManager = CarouselLayoutManager(UncontainedCarouselStrategy())

        applyFeedDensity()

        val bookmarksSnapHelper = CarouselSnapHelper()
        bookmarksSnapHelper.attachToRecyclerView(binding.bookmarksRV)

        val playlistsSnapHelper = CarouselSnapHelper()
        playlistsSnapHelper.attachToRecyclerView(binding.playlistsRV)

        binding.trendingRV.adapter = trendingAdapter
        binding.featuredRV.adapter = feedAdapter
        binding.bookmarksRV.adapter = bookmarkAdapter
        binding.playlistsRV.adapter = playlistAdapter
        binding.playlistsRV.adapter?.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                if (itemCount == 0) {
                    binding.playlistsRV.isGone = true
                    binding.playlistsTV.isGone = true
                }
            }
        })
        binding.watchingRV.adapter = watchingAdapter

        with(homeViewModel) {
            trending.observe(viewLifecycleOwner, ::showTrending)
            feed.observe(viewLifecycleOwner, ::showFeed)
            bookmarks.observe(viewLifecycleOwner, ::showBookmarks)
            playlists.observe(viewLifecycleOwner, ::showPlaylists)
            continueWatching.observe(viewLifecycleOwner, ::showContinueWatching)
            isLoading.observe(viewLifecycleOwner, ::updateLoading)
        }

        binding.featuredTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_subscriptionsFragment)
        }

        // Solar search pill: opens the search screen, shows the LOCAL badge in local mode
        binding.searchPill.setOnClickListener { pill ->
            // the pill grows into the search field (container transform) instead of the
            // default fade: home stays visible underneath until the transform ends
            exitTransition = Hold()
            findNavController().navigate(
                R.id.openSearch,
                null,
                navOptions { popUpTo(R.id.homeFragment) { saveState = true } },
                FragmentNavigatorExtras(pill to pill.transitionName)
            )
        }
        binding.localBadge.isGone = !PlayerHelper.fullLocalMode

        binding.watchingTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_watchHistoryFragment)
        }

        binding.trendingTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_trendsFragment)
        }

        binding.playlistsTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_libraryFragment)
        }

        binding.bookmarksTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_libraryFragment)
        }

        binding.refresh.setOnRefreshListener {
            binding.refresh.isRefreshing = true
            fetchHomeFeed()
        }

        binding.trendingRegion.setOnClickListener {
            TrendsFragment.showChangeRegionDialog(requireContext()) {
                fetchHomeFeed()
            }
        }

        val trendingCategories = MediaServiceRepository.instance.getTrendingCategories()
        binding.trendingCategory.isVisible = trendingCategories.size > 1
        binding.trendingCategory.setOnClickListener {
            val currentTrendingCategoryPref = PreferenceHelper.getString(
                PreferenceKeys.TRENDING_CATEGORY,
                TrendingCategory.LIVE.name
            ).let { categoryName -> trendingCategories.first { it.name == categoryName } }

            val categories = trendingCategories.map { category ->
                category to getString(category.titleRes)
            }

            var selected = trendingCategories.indexOf(currentTrendingCategoryPref)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category)
                .setSingleChoiceItems(
                    categories.map { it.second }.toTypedArray(),
                    selected
                ) { _, checked ->
                    selected = checked
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay) { _, _ ->
                    PreferenceHelper.putString(
                        PreferenceKeys.TRENDING_CATEGORY,
                        trendingCategories[selected].name
                    )
                    fetchHomeFeed()
                }
                .show()
        }

        binding.refreshButton.setOnClickListener {
            fetchHomeFeed()
        }

        binding.changeInstance.setOnClickListener {
            redirectToIntentSettings()
        }

        setupFilterChips()
    }

    // --- feed density: mockup pad/cgap tokens, re-applied on every resume ----

    private fun applyFeedDensity() {
        val pad = FeedDensity.padPx(requireContext())
        val gap = FeedDensity.gapPx(requireContext())
        val cardInset = CARD_INSET_DP.dpToPx()
        val rowPadding = pad - cardInset

        fun setGutterMargins(view: View) {
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = pad
                marginEnd = pad
            }
        }

        setGutterMargins(binding.searchPill)
        setGutterMargins(binding.heroContainer)

        binding.filterChips.setPaddingRelative(
            pad,
            binding.filterChips.paddingTop,
            pad,
            binding.filterChips.paddingBottom
        )

        listOf(
            binding.watchingTitle,
            binding.trendingTitle,
            binding.featuredTitle,
            binding.bookmarksTitle,
            binding.playlistsTitle
        ).forEach { title ->
            title.setPaddingRelative(pad, title.paddingTop, title.paddingEnd, title.paddingBottom)
        }

        listOf(
            binding.watchingTV,
            binding.trendingTV,
            binding.featuredTV,
            binding.bookmarksTV,
            binding.playlistsTV
        ).forEach { section ->
            section.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = gap }
        }

        listOf(
            binding.watchingRV,
            binding.trendingRV,
            binding.featuredRV,
            binding.bookmarksRV,
            binding.playlistsRV
        ).forEach { rows ->
            rows.setPaddingRelative(rowPadding, rows.paddingTop, rowPadding, rows.paddingBottom)
        }
    }

    // --- filter chips: one checkable chip per home section -------------------

    private fun enabledSections(): Set<String> {
        val defaults = resources.getStringArray(R.array.homeTabItemsValues)
        return PreferenceHelper.getStringSet(HOME_TAB_CONTENT, defaults.toSet())
    }

    private fun isSectionEnabled(key: String): Boolean = enabledSections().contains(key)

    /**
     * Builds the horizontal filter chip row (community style) right under the header.
     * Each chip toggles one home section through the very same preference the
     * appearance settings use, so both entry points always agree.
     */
    private fun setupFilterChips() {
        val labels = resources.getStringArray(R.array.homeTabItems)
        val values = resources.getStringArray(R.array.homeTabItemsValues)
        if (labels.size != values.size) return

        val chipGroup = binding.filterChips
        chipGroup.removeAllViews()
        val inflater = android.view.LayoutInflater.from(requireContext())
        val enabled = enabledSections()

        values.forEachIndexed { index, value ->
            val chip = inflater.inflate(R.layout.filter_chip, chipGroup, false)
                as com.google.android.material.chip.Chip
            chip.text = labels[index]
            chip.isChecked = value in enabled
            chip.setOnCheckedChangeListener { _, isChecked ->
                onSectionToggled(value, isChecked)
            }
            chipGroup.addView(chip)
        }
    }

    private fun onSectionToggled(key: String, isChecked: Boolean) {
        val updated = enabledSections().toMutableSet()
        if (isChecked) updated.add(key) else updated.remove(key)
        PreferenceHelper.putStringSet(HOME_TAB_CONTENT, updated)

        if (isChecked) {
            // data may already be cached: reloading re-fires the observers,
            // which re-show the section (or keeps it hidden when disabled)
            fetchHomeFeed()
        } else {
            hideSection(key)
        }
    }

    private fun hideSection(key: String) {
        when (key) {
            "featured" -> {
                binding.featuredTV.isGone = true
                binding.featuredRV.isGone = true
                renderFeedLists()
            }

            "watching" -> {
                binding.watchingTV.isGone = true
                binding.watchingRV.isGone = true
            }

            "trending" -> {
                binding.trendingTV.isGone = true
                binding.trendingRV.isGone = true
                renderFeedLists()
            }

            "bookmarks" -> {
                binding.bookmarksTV.isGone = true
                binding.bookmarksRV.isGone = true
            }

            "playlists" -> {
                binding.playlistsTV.isGone = true
                binding.playlistsRV.isGone = true
            }
        }
    }

    /**
     * The search pill and the filter chips stay pinned above the feed, unless the persistent
     * search bar is turned off: they then scroll away with the feed, as they used to.
     */
    private fun placeHeader() {
        val header = binding.homeHeader
        val parent = if (PlayerHelper.persistentSearchBar) binding.homeLayout else binding.homeContent
        if (header.parent === parent) return
        (header.parent as ViewGroup).removeView(header)
        parent.addView(header, 0)
    }

    override fun onResume() {
        super.onResume()
        // the Hold only serves the search transform: other destinations keep their animations
        exitTransition = null
        // on resume: the setting may have changed while this screen stayed alive
        placeHeader()
        applyFeedDensity()

        // Avoid re-fetching when re-entering the screen if it was loaded successfully, except when
        // the value of trending region has changed
        val isTrendingRegionChanged = homeViewModel.trending.value?.let {
            it.second.region != PreferenceHelper.getTrendingRegion(requireContext())
        } == true

        if (homeViewModel.loadedSuccessfully.value == false || isTrendingRegionChanged) {
            fetchHomeFeed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun fetchHomeFeed() {
        binding.nothingHere.isGone = true
        val defaultItems = resources.getStringArray(R.array.homeTabItemsValues)
        val visibleItems = PreferenceHelper.getStringSet(HOME_TAB_CONTENT, defaultItems.toSet())

        homeViewModel.loadHomeFeed(
            context = requireContext(),
            subscriptionsViewModel = subscriptionsViewModel,
            visibleItems = visibleItems,
            onUnusualLoadTime = ::showChangeInstanceSnackBar
        )
    }

    private fun showTrending(trends: Pair<TrendingCategory, TrendsViewModel.TrendingStreams>?) {
        if (trends == null) return
        if (!isSectionEnabled("trending")) {
            hideSection("trending")
            return
        }
        val (category, trendingStreams) = trends

        // cache the loaded trends in the [TrendsViewModel] so that the trends don't need to be
        // reloaded there
        val region = PreferenceHelper.getTrendingRegion(requireContext())
        trendsViewModel.setStreamsForCategory(
            category,
            TrendsViewModel.TrendingStreams(region, trendingStreams.streams)
        )

        makeVisible(binding.trendingRV, binding.trendingTV)
        renderFeedLists()
    }

    private fun showFeed(streamItems: List<StreamItem>?) {
        if (streamItems == null) return
        if (!isSectionEnabled("featured")) {
            hideSection("featured")
            return
        }

        makeVisible(binding.featuredRV, binding.featuredTV)
        renderFeedLists()
    }

    /**
     * Hero card source: first item of the "featured" feed, else first item of the
     * "Pour vous" feed. Both feeds drop that item so a video never shows up twice.
     */
    private fun renderFeedLists() {
        val hero = resolveHero()
        bindHero(hero)
        val heroUrl = hero?.url

        homeViewModel.feed.value?.let { feed ->
            feedAdapter.submitList(withoutHero(feed.take(20), heroUrl))
        }
        homeViewModel.trending.value?.second?.streams?.let { streams ->
            trendingAdapter.submitList(withoutHero(streams.take(10), heroUrl))
        }
    }

    private fun withoutHero(items: List<StreamItem>, heroUrl: String?): List<StreamItem> =
        if (heroUrl == null) items else items.filterNot { it.url == heroUrl }

    private fun resolveHero(): StreamItem? {
        if (isSectionEnabled("featured")) {
            homeViewModel.feed.value?.firstOrNull()?.let { return it }
        }
        if (isSectionEnabled("trending")) {
            homeViewModel.trending.value?.second?.streams?.firstOrNull()?.let { return it }
        }
        return null
    }

    private fun bindHero(item: StreamItem?) {
        binding.heroContainer.isVisible = item != null
        if (item == null) return

        val context = requireContext()
        binding.heroTitle.text = item.title
        binding.heroMeta.text = TextUtils.formatViewsString(
            context,
            item.views ?: -1L,
            item.uploaded,
            item.uploaderName
        )
        ImageHelper.loadImage(item.thumbnail, binding.heroThumb)

        val openVideo = View.OnClickListener {
            NavigationHelper.navigateVideo(context, PlayerData(item.url.orEmpty().toID()))
        }
        binding.heroCard.setOnClickListener(openVideo)
        binding.heroPlay.setOnClickListener(openVideo)
    }

    private fun showBookmarks(bookmarks: List<PlaylistBookmark>?) {
        if (bookmarks == null) return
        if (!isSectionEnabled("bookmarks")) {
            hideSection("bookmarks")
            return
        }

        makeVisible(binding.bookmarksTV, binding.bookmarksRV)
        bookmarkAdapter.submitList(bookmarks.map { bookmark ->
            CarouselPlaylist(
                id = bookmark.playlistId,
                title = bookmark.playlistName,
                thumbnail = bookmark.thumbnailUrl
            )
        })
    }

    private fun showPlaylists(playlists: List<Playlists>?) {
        if (playlists == null) return
        if (!isSectionEnabled("playlists")) {
            hideSection("playlists")
            return
        }

        makeVisible(binding.playlistsRV, binding.playlistsTV)
        playlistAdapter.submitList(playlists.map { playlist ->
            CarouselPlaylist(
                id = playlist.id!!,
                thumbnail = playlist.thumbnail,
                title = playlist.name
            )
        })
    }

    private fun showContinueWatching(unwatchedVideos: List<StreamItem>?) {
        if (unwatchedVideos == null) return
        if (!isSectionEnabled("watching")) {
            hideSection("watching")
            return
        }

        makeVisible(binding.watchingRV, binding.watchingTV)
        watchingAdapter.submitList(unwatchedVideos)
    }

    private fun updateLoading(isLoading: Boolean) {
        if (isLoading) {
            showLoading()
        } else {
            hideLoading()
        }
    }

    private fun showLoading() {
        binding.progress.isVisible = !binding.refresh.isRefreshing
        binding.nothingHere.isVisible = false
        binding.scroll.alpha = 0.3f
    }

    private fun hideLoading() {
        binding.progress.isVisible = false
        binding.refresh.isRefreshing = false

        val hasContent = homeViewModel.loadedSuccessfully.value == true
        if (hasContent) {
            showContent()
        } else {
            showNothingHere()
        }
        binding.scroll.alpha = 1.0f
    }

    private fun showNothingHere() {
        binding.nothingHere.isVisible = true
        binding.scroll.isVisible = false
    }

    private fun showContent() {
        binding.nothingHere.isVisible = false
        binding.scroll.isVisible = true
    }

    private fun showChangeInstanceSnackBar() {
        val root = _binding?.root ?: return
        Snackbar
            .make(root, R.string.suggest_change_instance, Snackbar.LENGTH_LONG)
            .apply {
                setAction(R.string.change) {
                    redirectToIntentSettings()
                }
                show()
            }
    }

    private fun redirectToIntentSettings() {
        val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.REDIRECT_KEY, SettingsActivity.REDIRECT_TO_INTENT_SETTINGS)
        }
        startActivity(settingsIntent)
    }

    private fun makeVisible(vararg views: View) {
        views.forEach { it.isVisible = true }
    }
}
