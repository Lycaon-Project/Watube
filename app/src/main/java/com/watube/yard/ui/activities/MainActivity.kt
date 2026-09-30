package com.watube.yard.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.motion.widget.Key
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.allViews
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.isNotEmpty
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.watube.yard.BuildConfig
import com.watube.yard.NavDirections
import com.watube.yard.R
import com.watube.yard.compat.PictureInPictureCompat
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.ActivityMainBinding
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.db.obj.SearchHistoryItem
import com.watube.yard.enums.ImportFormat
import com.watube.yard.enums.SearchType
import com.watube.yard.enums.TopLevelDestination
import com.watube.yard.extensions.anyChildFocused
import com.watube.yard.helpers.ImportHelper
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.NavBarHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.NetworkHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ThemeHelper
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.dialogs.ErrorDialog
import com.watube.yard.ui.dialogs.ImportTempPlaylistDialog
import com.watube.yard.ui.dialogs.RequireRestartDialog
import com.watube.yard.ui.extensions.onSystemInsets
import com.watube.yard.ui.fragments.DownloadsFragment
import com.watube.yard.ui.models.DownloadsViewModel
import com.watube.yard.ui.models.PlaylistViewModel
import com.watube.yard.ui.models.SearchViewModel
import com.watube.yard.ui.models.SubscriptionsViewModel
import com.watube.yard.ui.preferences.BackupRestoreSettings
import com.watube.yard.ui.preferences.BackupRestoreSettings.Companion.FILETYPE_ANY
import com.watube.yard.util.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Material "expanded" breakpoint: wider screens switch to a navigation rail */
private const val EXPANDED_WIDTH_DP = 600

/** Mirrors the 80dp width of the nav rail declared in res/layout/activity_main.xml */
private const val EXPANDED_RAIL_WIDTH_DP = 80f

class MainActivity : AbstractPlayerHostActivity() {
    private lateinit var binding: ActivityMainBinding

    lateinit var navController: NavController
    private var startFragmentId = R.id.homeFragment

    /** set once the navigation rail listeners are attached, so config changes don't stack them */
    private var railNavWired = false

    /** kept so that a rebuilt options menu doesn't stack a second destination listener */
    private var searchDestinationListener: NavController.OnDestinationChangedListener? = null

    private val subscriptionsViewModel: SubscriptionsViewModel by viewModels()

    // search related stuff
    private lateinit var searchView: SearchView
    private lateinit var searchItem: MenuItem
    private var savedSearchQuery: String? = null
    private var shouldOpenSuggestions = true
    private var currentSearchType: SearchType = SearchType.ONLINE
    private val searchViewModel: SearchViewModel by viewModels()
    private val downloadViewModel: DownloadsViewModel by viewModels()
    private val playlistViewModel: PlaylistViewModel by viewModels()

    // registering for activity results is only possible, this here should have been part of
    // PlaylistOptionsBottomSheet instead if Android allowed us to
    private var playlistExportFormat: ImportFormat = ImportFormat.NEWPIPE
    private var exportPlaylistId: String? = null

    /** garde one-shot : loadIntentData() peut être ré-entré par onNewIntent() suite à la
     *  relance auto PiP, et une boucle d'intents fait pin/unpin de la fenêtre PiP. */
    private var pipRelaunchPending = false
    private val createPlaylistsFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument(FILETYPE_ANY)
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        lifecycleScope.launch(Dispatchers.IO) {
            ImportHelper.exportPlaylists(
                this@MainActivity,
                uri,
                playlistExportFormat,
                selectedPlaylistIds = listOf(exportPlaylistId!!)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // show noInternet Activity if no internet available on app startup
        if (!NetworkHelper.isNetworkAvailable(this)) {
            val noInternetIntent = Intent(this, NoInternetActivity::class.java)
            startActivity(noInternetIntent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // manually apply additional padding for edge-to-edge compatibility
        // see https://developer.android.com/develop/ui/views/layout/edge-to-edge
        binding.root.onSystemInsets { _, systemBarInsets ->
            // there's a possibility that the paddings are not being applied properly when
            // exiting from player's fullscreen. Adding OnGlobalLayoutListener serves as
            // a workaround for this issue
            binding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    with(binding.appBarLayout) {
                        setPadding(
                            paddingLeft,
                            systemBarInsets.top,
                            paddingRight,
                            paddingBottom
                        )
                    }
                    binding.bottomNav.let { bar ->
                        // The bar floats above the bottom edge instead of bleeding behind the
                        // system bar: the inset is applied as a margin so the rounded corners
                        // and the gap below stay visible (1.1rem of the mockup).
                        val params = bar.layoutParams as ViewGroup.MarginLayoutParams
                        params.bottomMargin = systemBarInsets.bottom.coerceAtLeast(
                            bar.resources.getDimensionPixelSize(R.dimen.watube_nav_bottom_inset)
                        )
                        // assigning back is what triggers the relayout (and the keyframe refresh)
                        bar.layoutParams = params
                    }
                    with(binding.navRail) {
                        setPadding(
                            paddingLeft,
                            systemBarInsets.top,
                            paddingRight,
                            systemBarInsets.bottom
                        )
                    }
                    binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
        // manually update the bottom bar height in the mini player transition
        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // the bar floats above the system inset now, so the shift has to cover both
            val shift = binding.bottomNav.height +
                (binding.bottomNav.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            val transition = binding.root.getTransition(R.id.bottom_bar_transition)
            transition.keyFrameList.forEach { keyFrame ->
                // These frame positions are hardcoded in activity_main_scene.xml!
                for (key in keyFrame.getKeyFramesForView(binding.bottomNav.id)) {
                    if (key.framePosition == 1) key.setValue(
                        Key.TRANSLATION_Y,
                        shift
                    )
                }
                for (key in keyFrame.getKeyFramesForView(binding.container.id)) {
                    if (key.framePosition == 100) key.setValue(
                        Key.TRANSLATION_Y,
                        -shift
                    )
                }
            }
            binding.root.scene.setTransition(transition)
        }

        applyBottomBarNightStyle()

        // Header brand: icon + fallback colour first, then the gradient, which needs a
        // laid out text box and is therefore refreshed from the layout listener.
        applyBrandHeader()
        binding.brandWord.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyBrandWordGradient()
        }
        // Mockup #themeBtn flips light/dark in place. MainActivity declares uiMode in
        // configChanges, so AppCompat would only patch Resources and leave every already
        // inflated view on the previous theme: store the value Settings > Appearance
        // writes and reuse the restart dialog the app already shows for theme changes.
        binding.themeToggle.setOnClickListener {
            PreferenceHelper.putString(
                PreferenceKeys.THEME_MODE,
                if (isNightUiMode()) "L" else "D"
            )
            RequireRestartDialog().show(
                supportFragmentManager,
                RequireRestartDialog::class.java.name
            )
        }
        // Mockup #castBtn / #bellBtn toast too: the app ships no cast stack and no
        // notification screen, both stubs are a documented gap.
        binding.castBtn.setOnClickListener {
            Toast.makeText(this, R.string.toast_cast_unavailable, Toast.LENGTH_SHORT).show()
        }
        binding.bellBtn.setOnClickListener {
            Toast.makeText(this, R.string.toast_no_notifications, Toast.LENGTH_SHORT).show()
        }

        // Check update automatically
        if (PreferenceHelper.getBoolean(PreferenceKeys.AUTOMATIC_UPDATE_CHECKS, false)) {
            lifecycleScope.launch(Dispatchers.IO) {
                UpdateChecker(this@MainActivity).checkUpdate(false)
            }
        }

        // set the action bar for the activity
        setSupportActionBar(binding.toolbar)

        val navHostFragment = binding.fragment.getFragment<NavHostFragment>()
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // save start tab fragment id and apply navbar style
        startFragmentId = try {
            NavBarHelper.applyNavBarStyle(binding.bottomNav)
        } catch (_: Exception) {
            R.id.homeFragment
        }

        // set default tab as start fragment
        navController.graph = navController.navInflater.inflate(R.navigation.nav).also {
            it.setStartDestination(startFragmentId)
        }

        // Prevent duplicate entries into backstack, if selected item and current
        // visible fragment is different, then navigate to selected item.
        binding.bottomNav.setOnItemReselectedListener {
            if (it.itemId != navController.currentDestination?.id) {
                navigateToBottomSelectedItem(it)
            } else {
                // get the current fragment
                val fragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
                tryScrollToTop(fragment?.requireView())
            }
        }

        binding.bottomNav.setOnItemSelectedListener {
            navigateToBottomSelectedItem(it)
        }

        if (binding.bottomNav.menu.children.none { it.itemId == startFragmentId }) deselectBottomBarItems()

        setupExpandedLayout()

        // handle error logs
        PreferenceHelper.getErrorLog().ifBlank { null }?.let {
            if (!BuildConfig.DEBUG)
                ErrorDialog().show(supportFragmentManager, null)
        }

        setupSubscriptionsBadge()

        loadIntentData()

        showUserInfoDialogIfNeeded()
    }

    /**
     * Deselect all bottom bar items
     */
    private fun deselectBottomBarItems() {
        listOf(binding.bottomNav, binding.navRail).forEach { bar ->
            bar.menu.setGroupCheckable(0, true, false)
            for (child in bar.menu.children) {
                child.isChecked = false
            }
            bar.menu.setGroupCheckable(0, true, true)
        }
    }

    /**
     * Adaptive navigation: tablets and unfolded foldables (>= 600dp of width, the
     * Material "expanded" breakpoint) swap the bottom bar for a navigation rail pinned
     * to the start edge. Phones keep the exact previous behaviour.
     *
     * Idempotent, because it is called again on every configuration change: MainActivity
     * declares `orientation` in its configChanges, so rotating never recreates it.
     */
    private fun setupExpandedLayout() {
        val expanded = resources.configuration.screenWidthDp >= EXPANDED_WIDTH_DP

        binding.navRail.isVisible = false
        setContentMarginStart(0f)

        if (!expanded) {
            // compact layout: bring the bottom bar back, the nav preference still
            // decides whether it is visible at all (e.g. "hide every tab")
            try {
                NavBarHelper.applyNavBarStyle(binding.bottomNav)
            } catch (_: Exception) {
                binding.bottomNav.isVisible = true
            }
            return
        }

        binding.bottomNav.isVisible = false
        binding.navRail.isVisible = true

        // both bars must offer the very same (user ordered) tabs
        try {
            NavBarHelper.applyNavBarStyle(binding.navRail)
        } catch (_: Exception) {
            // a corrupted nav preference must never keep the rail from working
        }

        // "hide every tab" makes applyNavBarStyle drop the bar: keep it that way
        if (!binding.navRail.isVisible) return

        if (!railNavWired) {
            railNavWired = true
            binding.navRail.setupWithNavController(navController)
            binding.navRail.setOnItemReselectedListener {
                if (it.itemId != navController.currentDestination?.id) {
                    navigateToBottomSelectedItem(it)
                } else {
                    tryScrollToTop(
                        (supportFragmentManager.fragments.filterIsInstance<NavHostFragment>()
                            .firstOrNull()
                            ?.childFragmentManager?.fragments)?.firstOrNull()?.requireView()
                    )
                }
            }
            binding.navRail.setOnItemSelectedListener {
                navigateToBottomSelectedItem(it)
            }
        }

        // keep the content clear of the rail
        setContentMarginStart(EXPANDED_RAIL_WIDTH_DP)
    }

    /** Keeps the fragment container clear of the navigation rail (0dp on phones) */
    private fun setContentMarginStart(widthDp: Float) {
        (binding.fragment.layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
            layoutParams.marginStart = (widthDp * resources.displayMetrics.density).toInt()
            binding.fragment.layoutParams = layoutParams
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::binding.isInitialized) return
        setupExpandedLayout()
        // uiMode is part of this activity's configChanges, so a system night flip is
        // delivered here instead of through a recreation: both the bottom bar night
        // styling and the header brand have to follow it manually.
        applyBottomBarNightStyle()
        applyBrandHeader()
    }

    /**
     * Try to find a scroll or recycler view and scroll it back to the top
     */
    private fun tryScrollToTop(view: View?) {
        val scrollView = view?.allViews
            ?.firstOrNull { it is ScrollView || it is NestedScrollView || it is RecyclerView }
        when (scrollView) {
            is ScrollView -> scrollView.smoothScrollTo(0, 0)
            is NestedScrollView -> scrollView.smoothScrollTo(0, 0)
            is RecyclerView -> scrollView.smoothScrollToPosition(0)
        }
    }

    /**
     * Initialize the notification badge showing the amount of new videos
     */
    private fun setupSubscriptionsBadge() {
        if (!PreferenceHelper.getBoolean(
                PreferenceKeys.NEW_VIDEOS_BADGE,
                false
            )
        ) {
            return
        }

        // the badge counts new videos: the feed is what has to be loaded, not the channels
        subscriptionsViewModel.fetchFeed(this, forceRefresh = false)

        subscriptionsViewModel.videoFeed.observe(this) { feed ->
            val lastCheckedFeedTime = PreferenceHelper.getLastCheckedFeedTime(seenByUser = true)
            val lastSeenVideoIndex = feed.orEmpty()
                .filter { !it.isUpcoming }
                .indexOfFirst { it.uploaded <= lastCheckedFeedTime }
            if (lastSeenVideoIndex < 1) {
                // nothing new: drop a badge left over from a previous feed
                binding.bottomNav.removeBadge(R.id.subscriptionsFragment)
                binding.navRail.removeBadge(R.id.subscriptionsFragment)
                return@observe
            }

            // the badge is mirrored on both bars: only one of them is visible at a time
            listOf(binding.bottomNav, binding.navRail).forEach { bar ->
                bar.getOrCreateBadge(R.id.subscriptionsFragment).apply {
                    number = lastSeenVideoIndex
                    backgroundColor = ThemeHelper.getThemeColor(
                        this@MainActivity,
                        androidx.appcompat.R.attr.colorPrimary
                    )
                    badgeTextColor = ThemeHelper.getThemeColor(
                        this@MainActivity,
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                }
            }
        }
    }

    /** True while the resources resolve against the night uiMode. */
    private fun isNightUiMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * values-night sits outside the editable resource set, so the bottom bar styling is
     * resolved here instead: the night scrim fill and the selected tab ink (values-night
     * ships #35E08C for watube_nav_active, while the mockup uses its night --acc-text,
     * watube_acc_text, on the pill). The day branch restores the drawable and the tint
     * selector declared in activity_main.xml, so the method is idempotent in both
     * directions and can follow a system uiMode flip.
     *
     * The theme's own outline attribute keeps the unselected tabs correct in both modes,
     * so it is resolved instead of hard-coded.
     */
    private fun applyBottomBarNightStyle() {
        val nightMode = isNightUiMode()

        binding.bottomNav.setBackgroundResource(
            if (nightMode) R.drawable.watube_bottom_nav_background_night
            else R.drawable.watube_bottom_nav_background
        )

        val tint = if (nightMode) {
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    getColor(R.color.watube_acc_text),
                    ThemeHelper.getThemeColor(this, com.google.android.material.R.attr.colorOutline)
                )
            )
        } else {
            AppCompatResources.getColorStateList(this, R.color.watube_bottom_nav_item_tint)
        }
        binding.bottomNav.itemIconTintList = tint
        binding.bottomNav.itemTextColor = tint
    }

    /**
     * Header brand of the mockup: the theme button offers the mode that is not rendered
     * (sun while the UI is dark, moon while it is light) and the word gets its day/night
     * colour. Deliberately kept out of the brandWord layout listener: setTextColor and
     * setImageResource can trigger a relayout, and a listener that feeds requestLayout
     * back into itself would spin.
     *
     * values-night is outside the editable resource set, so the day/night pair of
     * --acc-text is selected here: watube_nav_active (#2C674A) by day, watube_acc_text
     * (#59E6A1) at night.
     */
    private fun applyBrandHeader() {
        binding.themeToggle.setImageResource(
            if (isNightUiMode()) R.drawable.watube_sun else R.drawable.watube_moon
        )
        binding.brandWord.setTextColor(
            getColor(
                if (isNightUiMode()) R.color.watube_acc_text else R.color.watube_nav_active
            )
        )
        applyBrandWordGradient()
    }

    /**
     * Paints the mockup .word gradient, linear-gradient(95deg, --acc-text, --acc 75%)
     * clipped to the text box (wrap_content makes the view bounds the text bounds). Only
     * the paint shader changes here, so it is safe to re-run from the layout listener.
     * The gradient end stays on colorPrimary so every accent follows.
     */
    private fun applyBrandWordGradient() {
        val word = binding.brandWord
        val width = word.width
        val height = word.height
        if (width == 0 || height == 0) return

        val start = getColor(
            if (isNightUiMode()) R.color.watube_acc_text else R.color.watube_nav_active
        )
        // CSS 95deg points right, tilted five degrees down; the length below is the
        // "cover the box corners" axis of a two point gradient, and the 0.75f stop is
        // the mockup's "--acc at 75%".
        val radians = Math.toRadians(95.0)
        val dx = Math.sin(radians)
        val dy = -Math.cos(radians)
        val length = Math.abs(width * dx) + Math.abs(height * dy)
        val x0 = width / 2.0 - dx * length / 2.0
        val y0 = height / 2.0 - dy * length / 2.0
        word.paint.shader = LinearGradient(
            x0.toFloat(),
            y0.toFloat(),
            (x0 + dx * length).toFloat(),
            (y0 + dy * length).toFloat(),
            intArrayOf(start, ThemeHelper.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary)),
            floatArrayOf(0f, 0.75f),
            Shader.TileMode.CLAMP
        )
        word.invalidate()
    }

    private fun isSearchInProgress(): Boolean {
        if (!this::navController.isInitialized) return false
        val id = navController.currentDestination?.id ?: return false

        return id in listOf(
            R.id.searchFragment,
            R.id.searchResultFragment,
            R.id.channelFragment,
            R.id.playlistFragment
        )
    }

    private fun addSearchQueryToHistory(query: String) {
        val searchHistoryEnabled =
            PreferenceHelper.getBoolean(PreferenceKeys.SEARCH_HISTORY_TOGGLE, true)
        if (searchHistoryEnabled && query.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val newItem = SearchHistoryItem(query.trim())
                DatabaseHelper.addToSearchHistory(newItem)
            }
        }
    }

    override fun invalidateMenu() {
        // Don't invalidate menu when in search in progress
        // this is a workaround as there is bug in android code
        // details of bug: https://issuetracker.google.com/issues/244336571
        if (isSearchInProgress()) {
            return
        }
        super.invalidateMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.action_bar, menu)

        // stuff for the search in the topBar
        val searchItem = menu.findItem(R.id.action_search)
        this.searchItem = searchItem
        searchView = searchItem.actionView as SearchView

        // automatically set a different search icon in the playlists
        searchDestinationListener?.let { navController.removeOnDestinationChangedListener(it) }
        val destinationListener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentSearchType = when (destination.id) {
                R.id.downloadsFragment -> SearchType.DOWNLOADS
                R.id.playlistFragment -> SearchType.PLAYLIST
                else -> SearchType.ONLINE
            }
            // clear query in unused page so that they're reset when visiting the page the next time
            if (currentSearchType != SearchType.DOWNLOADS) downloadViewModel.setQuery(null)
            if (currentSearchType != SearchType.PLAYLIST) playlistViewModel.setQuery(null)

            val searchIconResource = when (currentSearchType) {
                SearchType.DOWNLOADS -> R.drawable.ic_download_search
                SearchType.PLAYLIST -> R.drawable.ic_playlist_search
                SearchType.ONLINE -> R.drawable.ic_search
            }

            searchItem.setIcon(searchIconResource)
        }
        searchDestinationListener = destinationListener
        navController.addOnDestinationChangedListener(destinationListener)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()

                // playlist and download search don't do anything on submit
                // as they search while typing
                if (currentSearchType != SearchType.ONLINE) return true

                // handle inserted YouTube-like URLs and directly open the referenced
                // channel, playlist or video instead of showing search results
                if (query.toHttpUrlOrNull() != null) {
                    val queryIntent = IntentHelper.resolveType(query.toUri())

                    val didNavigate = navigateToMediaByIntent(queryIntent) {
                        navController.popBackStack(R.id.searchFragment, true)
                        searchItem.collapseActionView()
                    }
                    if (didNavigate) return true
                }

                navController.navigate(NavDirections.showSearchResults(query))

                addSearchQueryToHistory(query)

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                when (currentSearchType) {
                    SearchType.ONLINE -> {
                        if (!shouldOpenSuggestions) return true

                        // Prevent navigation when search view is collapsed
                        if (searchView.isIconified ||
                            binding.bottomNav.menu.children.any {
                                it.itemId == navController.currentDestination?.id
                            }
                        ) {
                            return true
                        }

                        // prevent malicious navigation when the search view is getting collapsed
                        if (navController.currentDestination?.id == R.id.searchResultFragment && newText == null) {
                            return false
                        }

                        if (navController.currentDestination?.id != R.id.searchFragment) {
                            val args = Bundle().apply {
                                putString(IntentData.query, newText)
                            }
                            navController.navigate(R.id.searchFragment, args)
                        } else {
                            searchViewModel.setQuery(newText)
                        }
                    }

                    SearchType.PLAYLIST -> {
                        playlistViewModel.setQuery(newText)
                    }

                    SearchType.DOWNLOADS -> {
                        downloadViewModel.setQuery(newText)
                    }
                }

                return true
            }
        })

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                if (currentSearchType == SearchType.ONLINE && navController.currentDestination?.id != R.id.searchResultFragment) {
                    searchViewModel.setQuery(null)
                    navController.navigate(R.id.openSearch)
                }
                item.setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
                )
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                // Handover back press to `BackPressedDispatcher` if not on a root destination
                if (navController.previousBackStackEntry != null) {
                    this@MainActivity.onBackPressedDispatcher.onBackPressed()
                }

                // Suppress collapsing of search when search in progress.
                return !isSearchInProgress()
            }
        })

        // handle search queries passed by the intent
        if (savedSearchQuery != null) {
            searchItem.expandActionView()
            searchView.setQuery(savedSearchQuery, true)
            savedSearchQuery = null
        }

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Update the query text in the search bar without opening the search suggestions
     */
    fun setQuerySilent(query: String) {
        if (!this::searchView.isInitialized) return

        shouldOpenSuggestions = false
        searchView.setQuery(query, false)
        shouldOpenSuggestions = true
    }

    /**
     * Update the query text in the search bar and load the search suggestions
     * @param submit whether to immediately load the search results (not suggestions)
     */
    fun setQuery(query: String, submit: Boolean) {
        if (::searchView.isInitialized) searchView.setQuery(query, submit)
    }

    private fun loadIntentData() {
        // If activity is running in PiP mode, then start it in front.
        // one-shot : sans cette garde, onNewIntent() rappelle cette méthode et la
        // relance auto s'exécute en boucle (la fenêtre PiP se re-border en permanence).
        if (!pipRelaunchPending &&
            PictureInPictureCompat.isInPictureInPictureMode(this)
        ) {
            pipRelaunchPending = true
            val nIntent = Intent(this, MainActivity::class.java)
            nIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(nIntent)
        }

        if (intent?.getBooleanExtra(IntentData.maximizePlayer, false) == true) {
            // attempt to open the current video player fragment
            if (intent?.getBooleanExtra(IntentData.audioOnly, false) == false) {
                runOnPlayerFragment { binding.playerMotionLayout.transitionToStart(); true }
                return
            }

            // if it's an audio only session, attempt to maximize the audio player or create a new one
            if (runOnAudioPlayerFragment { binding.playerMotionLayout.transitionToStart(); true }) return

            val offlinePlayer = intent!!.getBooleanExtra(IntentData.offlinePlayer, false)
            NavigationHelper.openAudioPlayerFragment(this, offlinePlayer = offlinePlayer)
            return
        }

        // navigate to (temporary) playlist or channel if available
        if (navigateToMediaByIntent(intent)) return

        // Get saved search query if available
        intent?.getStringExtra(IntentData.query)?.let {
            savedSearchQuery = it
        }

        // Open the Downloads screen if requested
        if (intent?.getBooleanExtra(IntentData.OPEN_DOWNLOADS, false) == true) {
            // downloads isn't a visible tab: no other tab may stay highlighted
            deselectBottomBarItems()
            navController.navigate(R.id.downloadsFragment)
            return
        }

        // Handle navigation from app shortcuts (Home, Trends, etc.)
        intent?.getStringExtra(IntentData.fragmentToOpen)?.let {
            ShortcutManagerCompat.reportShortcutUsed(this, it)
            // same for trends: the destination may be hidden in the bottom bar
            deselectBottomBarItems()
            when (it) {
                TopLevelDestination.Home.route -> navController.navigate(R.id.homeFragment)
                TopLevelDestination.Trends.route -> navController.navigate(R.id.trendsFragment)
                TopLevelDestination.Subscriptions.route -> navController.navigate(
                    R.id.subscriptionsFragment
                )
                TopLevelDestination.Library.route -> navController.navigate(R.id.libraryFragment)
            }
        }

        // Rebind the download service if the user is currently downloading
        if (intent?.getBooleanExtra(IntentData.downloading, false) == true) {
            (supportFragmentManager.fragments.find { it is NavHostFragment })
                ?.childFragmentManager?.fragments?.forEach { fragment ->
                    (fragment as? DownloadsFragment)?.bindDownloadService()
                }
        }
    }

    /**
     * Navigates to the channel, video or playlist provided in the [Intent] if available
     *
     * @return Whether the method handled the event and triggered the navigation to a new fragment
     */
    fun navigateToMediaByIntent(intent: Intent, actionBefore: () -> Unit = {}): Boolean {
        intent.getStringExtra(IntentData.channelId)?.let {
            actionBefore()
            navController.navigate(NavDirections.openChannel(channelId = it))
            return true
        }
        intent.getStringExtra(IntentData.channelName)?.let {
            actionBefore()
            navController.navigate(NavDirections.openChannel(channelName = it))
            return true
        }
        intent.getStringExtra(IntentData.playlistId)?.let {
            actionBefore()
            navController.navigate(NavDirections.openPlaylist(playlistId = it))
            return true
        }
        intent.getStringArrayExtra(IntentData.videoIds)?.let {
            actionBefore()
            ImportTempPlaylistDialog()
                .apply {
                    arguments = Bundle().apply {
                        putString(IntentData.playlistName, intent.getStringExtra(IntentData.playlistName))
                        putStringArray(IntentData.videoIds, it)
                    }
                }
                .show(supportFragmentManager, null)
            return true
        }

        intent.getStringExtra(IntentData.videoId)?.let {
            // the below explained work around only seems to work on Android 11 and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && binding.bottomNav.menu.isNotEmpty()) {
                // the bottom navigation bar has to be created before opening the video
                // otherwise the player layout measures aren't calculated properly
                // and the miniplayer is opened at a closed state and overlapping the navigation bar
                binding.bottomNav.viewTreeObserver.addOnGlobalLayoutListener(object :
                    ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        navigationVideo(it)

                        binding.bottomNav.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                })
            } else {
                navigationVideo(it)
            }

            return true
        }

        return false
    }

    private fun navigationVideo(videoId: String) {
        NavigationHelper.navigateVideo(
            context = this@MainActivity,
            playerData = PlayerData(
                videoId = videoId,
                timestamp = intent.getLongExtra(IntentData.timeStamp, 0L)
            ),
        )
    }

    private fun navigateToBottomSelectedItem(item: MenuItem): Boolean {
        if (item.itemId == R.id.subscriptionsFragment) {
            binding.bottomNav.removeBadge(R.id.subscriptionsFragment)
            binding.navRail.removeBadge(R.id.subscriptionsFragment)
        }

        // Remove focus from search view when navigating to bottom view.
        searchItem.collapseActionView()

        // settings are hosted by their own activity, not by this nav graph
        if (item.itemId == R.id.settingsFragment) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }

        return item.onNavDestinationSelected(navController)
    }

    /**
     * The settings tab opens a separate activity, so the checked item no longer matches
     * the current destination when coming back. Re-sync both bars, without triggering
     * [setOnItemSelectedListener] (a plain [MenuItem.isChecked] never does).
     */
    private fun syncBottomBarSelection() {
        if (!::binding.isInitialized) return
        val destinationId = navController.currentDestination?.id ?: return
        listOf(binding.bottomNav, binding.navRail).forEach { bar ->
            val item = bar.menu.findItem(destinationId) ?: return@forEach
            if (item.isVisible && !item.isChecked) item.isChecked = true
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        runOnPlayerFragment {
            onUserLeaveHint()
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (!::binding.isInitialized) return
        loadIntentData()
    }

    override fun onResume() {
        super.onResume()
        pipRelaunchPending = false
        syncBottomBarSelection()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // onCreate() peut avoir quitté avant d'initialiser binding (pas de réseau)
        if (!::binding.isInitialized) return super.onKeyUp(keyCode, event)
        // don't forward key events to the player while the search text input is used
        if (::searchItem.isInitialized && searchItem.isActionViewExpanded) return false

        if (runOnPlayerFragment { this@runOnPlayerFragment.onKeyUp(keyCode, event) }) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    fun startPlaylistExport(
        playlistId: String,
        playlistName: String,
        format: ImportFormat,
        includeTimestamp: Boolean
    ) {
        playlistExportFormat = format
        exportPlaylistId = playlistId

        val fileName =
            BackupRestoreSettings.getExportFileName(this, format, playlistName, includeTimestamp)
        createPlaylistsFile.launch(fileName)
    }

    private fun showUserInfoDialogIfNeeded() {
        // don't show the update information dialog for debug builds
        if (BuildConfig.DEBUG) return

        val lastShownVersionCode =
            PreferenceHelper.getInt(PreferenceKeys.LAST_SHOWN_INFO_MESSAGE_VERSION_CODE, -1)

        // mapping of version code to info message
        val infoMessages = emptyList<Pair<Int, String>>()

        val message =
            infoMessages.lastOrNull { (versionCode, _) -> versionCode > lastShownVersionCode }?.second
                ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_information)
            .setMessage(message)
            .setNegativeButton(R.string.okay, null)
            .setPositiveButton(R.string.never_show_again) { _, _ ->
                PreferenceHelper.putInt(
                    PreferenceKeys.LAST_SHOWN_INFO_MESSAGE_VERSION_CODE,
                    BuildConfig.VERSION_CODE
                )
            }
            .show()
    }

    override fun minimizePlayerContainerLayout() {
        binding.mainMotionLayout.transitionToEnd()
    }

    override fun maximizePlayerContainerLayout() {
        binding.mainMotionLayout.transitionToStart()
    }

    override fun setPlayerContainerProgress(progress: Float) {
        if (!NavBarHelper.hasTabs()) return

        binding.mainMotionLayout.progress = progress
    }

    /**
     * @return whether the search view focus was cleared successfully
     */
    override fun clearSearchViewFocus(): Boolean {
        if (!this::searchView.isInitialized || !searchView.anyChildFocused()) return false

        searchView.clearFocus()
        return true
    }
}