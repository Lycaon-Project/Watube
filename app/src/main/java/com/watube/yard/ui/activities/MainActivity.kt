package com.watube.yard.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.LinearGradient
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Shader
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.navigation.NavDestination
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
import com.watube.yard.enums.TopLevelDestination
import com.watube.yard.helpers.GridColumns
import com.watube.yard.helpers.ImportHelper
import com.watube.yard.helpers.IntentHelper
import com.watube.yard.helpers.NavBarHelper
import com.watube.yard.helpers.NavigationHelper
import com.watube.yard.helpers.NetworkHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ThemeHelper
import com.watube.yard.parcelable.PlayerData
import com.watube.yard.ui.dialogs.ImportTempPlaylistDialog
import com.watube.yard.ui.extensions.onSystemInsets
import com.watube.yard.ui.fragments.DownloadsFragment
import com.watube.yard.ui.models.SubscriptionsViewModel
import com.watube.yard.ui.preferences.BackupRestoreSettings
import com.watube.yard.ui.preferences.BackupRestoreSettings.Companion.FILETYPE_ANY
import com.watube.yard.util.MediaIds
import com.watube.yard.util.UpdateChecker
import com.watube.yard.ui.base.BottomBarOverlay
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import com.watube.yard.ui.base.ThemeTransition
import com.watube.yard.ui.base.TopBarOverlay
import com.watube.yard.ui.views.GlassBackground
import com.watube.yard.ui.views.SingleViewTouchableMotionLayout
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// region Navigation
/** Player progress at which the bottom bar is fully pushed off screen */
private const val BAR_HIDDEN_PROGRESS = 0.01f

/** Player progress from which the mini player starts lifting above the bottom bar */
private const val CONTAINER_LIFT_START = 0.35f
// endregion

class MainActivity : AbstractPlayerHostActivity() {
    // region Navigation
    private var startFragmentId = R.id.homeFragment

    /** set once the navigation rail listeners are attached, so config changes don't stack them */
    private var railNavWired = false

    /** New videos of the subscriptions feed shown as a badge on its tab (0: no badge). */
    private var newVideosCount = 0
    // endregion

    // region Appearance
    /** Keeps the lists of the screens clear of the floating bottom bar they run under. */
    private val bottomBarOverlay = BottomBarOverlay(R.id.fragment)

    /** Starts the screens below the top bar, except those that run on under it. */
    private val topBarOverlay = TopBarOverlay(R.id.fragment)

    /** Glass of the bottom bar while the setting is on, else null (its plain fill). */
    private var glassBar: GlassBackground? = null

    /** Glass setting applied last, to follow a change made in the settings. */
    private var glassBarApplied: Boolean? = null

    /** Night state the views were inflated with, see [onConfigurationChanged] */
    private var inflatedNightUiMode = false

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

        val background = if (nightMode) R.drawable.watube_bottom_nav_background_night
        else R.drawable.watube_bottom_nav_background

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
        // the rail is the same bar stood on its side
        listOf(binding.bottomNav, binding.navRail).forEach { bar ->
            bar.setBackgroundResource(background)
            bar.itemIconTintList = tint
            bar.itemTextColor = tint
        }
        // tone and ground of the glass follow the theme
        applyBottomBarGlass()
    }

    /**
     * Frosted glass for the bottom bar (Appearance > Glass effect, on by default): the bar
     * shows the screens under it blurred instead of its plain fill. The rail is not over the
     * screens and keeps its fill.
     */
    private fun applyBottomBarGlass() {
        glassBar?.release()
        glassBar = null
        val enabled = !PreferenceHelper.getBoolean(PreferenceKeys.EFECTO_OFF, false)
        glassBarApplied = enabled
        val night = isNightUiMode()
        val plain = if (night) R.drawable.watube_bottom_nav_background_night
        else R.drawable.watube_bottom_nav_background
        val glaze = AppCompatResources.getDrawable(this, plain)?.mutate() as? LayerDrawable
        if (enabled && glaze != null) {
            // the XML fill is the plain one (Efecto off): the glass keeps its own, lighter one
            glaze.setGlassFill(
                getColorStateList(if (night) R.color.watube_nav_scrim_night else R.color.watube_nav_scrim)
            )
            glassBar = GlassBackground(
                source = binding.backdrop,
                host = binding.bottomNav,
                glaze = glaze,
                tone = getColor(if (night) R.color.watube_nav_scrim_night else R.color.watube_nav_scrim),
                ground = ThemeHelper.getThemeColor(this, android.R.attr.colorBackground),
                night = night,
                blurRadius = resources.getDimension(R.dimen.watube_glass_blur_radius),
                cornerRadius = resources.getDimension(R.dimen.watube_bottom_nav_radius),
            ).also { binding.bottomNav.background = it }
        } else {
            binding.bottomNav.setBackgroundResource(plain)
        }
        updateBottomBarOverlay()
        refreshMiniPlayerGlass()
    }

    /** The docked mini player's card in glass, behind the same switch as the bottom bar. */
    override fun miniPlayerBackground(card: View): Drawable? {
        if (glassBar == null) return null
        val glaze = AppCompatResources.getDrawable(this, R.drawable.watube_miniplayer_background)
            ?.mutate() as? LayerDrawable ?: return null
        val night = isNightUiMode()
        glaze.setGlassFill(
            getColorStateList(
                if (night) R.color.watube_miniplayer_glass_night else R.color.watube_miniplayer_glass_fill
            )
        )
        return GlassBackground(
            source = binding.backdrop,
            host = card,
            glaze = glaze,
            tone = if (night) getColor(R.color.watube_nav_scrim_night)
            else ThemeHelper.getThemeColor(this, com.google.android.material.R.attr.colorSurfaceContainer),
            ground = ThemeHelper.getThemeColor(this, android.R.attr.colorBackground),
            night = night,
            blurRadius = resources.getDimension(R.dimen.watube_glass_blur_radius),
            cornerRadius = resources.getDimension(R.dimen.watube_card_radius),
            // under the full player the card is covered by the video
            isShown = { playerContainerProgress > 0f },
        )
    }

    /** Gives the fill layer of a glass glaze ([R.id.watube_glass_fill]) the [fill] tone. */
    private fun LayerDrawable.setGlassFill(fill: ColorStateList) {
        (findDrawableByLayerId(R.id.watube_glass_fill) as? GradientDrawable)?.color = fill
    }

    /** Gives the mini player on show the card matching the glass setting. */
    private fun refreshMiniPlayerGlass() {
        listOfNotNull(
            binding.container.findViewById<View>(R.id.main_container),
            // the audio player's card is a plain surface while it is expanded
            binding.container.findViewById<View>(R.id.audio_player_container)
                ?.takeIf { it.background !is ColorDrawable },
        ).forEach { card ->
            card.background = miniPlayerBackground(card)
                ?: AppCompatResources.getDrawable(this, R.drawable.watube_miniplayer_background)
        }
    }

    /**
     * The screens are recorded for the glass only while a glass shows them: the bottom bar on
     * screen, or the docked mini player (beside the rail, the bar is hidden).
     */
    private fun updateBackdropRecording() {
        val miniPlayerDocked = playerContainerProgress > 0f && binding.container.childCount > 0
        binding.backdrop.isRecording =
            glassBar != null && (bottomBarOverlay.height > 0 || miniPlayerDocked)
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
    /** The new sun / moon turns into place, a beat after the content starts coming out. */
    private fun applyBrandHeader() {
        binding.themeToggle.setImageResource(
            if (isNightUiMode()) R.drawable.watube_top_light else R.drawable.watube_top_dark
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
    // endregion

    // region Navigation
    /** Resolved bottom gap for the floating nav bar (max of system inset and the design inset). */
    private var navBarBottomInset = 0

    private lateinit var binding: ActivityMainBinding

    lateinit var navController: NavController

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

        // before the navigation host creates its first screen
        bottomBarOverlay.attach(this)
        topBarOverlay.attach(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inflatedNightUiMode = isNightUiMode()

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
                        navBarBottomInset = systemBarInsets.bottom.coerceAtLeast(
                            bar.resources.getDimensionPixelSize(R.dimen.watube_nav_bottom_inset)
                        )
                        val params = bar.layoutParams as ViewGroup.MarginLayoutParams
                        if (params.bottomMargin != navBarBottomInset) {
                            params.bottomMargin = navBarBottomInset
                            bar.layoutParams = params
                        }
                        applyPlayerContainerProgress()
                    }
                    binding.navRail.let { rail ->
                        // the rail starts below the app bar, which already clears the status
                        // bar, and floats at the same height above the bottom edge as the
                        // bar and the mini player
                        val params = rail.layoutParams as ViewGroup.MarginLayoutParams
                        if (params.bottomMargin != navBarBottomInset) {
                            params.bottomMargin = navBarBottomInset
                            rail.layoutParams = params
                        }
                    }
                    binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        }
        // the mini player docks above the bar: follow a real change of its height only
        binding.bottomNav.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) applyPlayerContainerProgress()
        }
        // the screens run on under the bar: their lists follow every change of the room it
        // takes (its height, the system bars, every tab hidden, the rail taking over); a
        // layout pass that changed none of it costs one comparison
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            updateBottomBarOverlay()
            topBarOverlay.update(this, binding.appBarLayout.height)
        }

        applyBottomBarNightStyle()

        // Header brand: icon + fallback colour first, then the gradient, which needs a
        // laid out text box and is therefore refreshed from the layout listener.
        applyBrandHeader()
        // rebuilt by a theme switch: the sun / moon turns in as the new theme comes out
        binding.brandWord.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyBrandWordGradient()
        }
        // Mockup #themeBtn flips light/dark in place, with no restart: every screen is
        // rebuilt in the new theme and this one dissolves into it (ThemeTransition).
        // The value is the one Settings > Appearance writes.
        binding.themeToggle.setOnClickListener {
            val switchingToLight = isNightUiMode()
            ThemeTransition.switchTheme(this) {
                PreferenceHelper.putString(
                    PreferenceKeys.THEME_MODE,
                    if (switchingToLight) "L" else "D"
                )
                // OLED / pure black only applies in dark mode: disable it when going light
                if (switchingToLight) {
                    PreferenceHelper.putBoolean(PreferenceKeys.PURE_THEME, false)
                }
            }
        }
        // The bell opens the notification settings (which channels notify on new streams),
        // the only place the app actually manages notifications.
        binding.bellBtn.setOnClickListener {
            startActivity(
                Intent(this, SettingsActivity::class.java).putExtra(
                    SettingsActivity.REDIRECT_KEY,
                    SettingsActivity.REDIRECT_TO_NOTIFICATION_SETTINGS
                )
            )
        }
        // The overflow button replaces the removed toolbar: it hosts Settings/Help/About.
        binding.overflowBtn.setOnClickListener { anchor ->
            androidx.appcompat.widget.PopupMenu(this, anchor).apply {
                menuInflater.inflate(R.menu.action_bar, menu)
                setOnMenuItemClickListener(::onOptionsItemSelected)
            }.show()
        }

        // Check update automatically (on by default; the first launch always checks because
        // no previous check time is stored yet, then it throttles to the configured interval)
        if (PreferenceHelper.getBoolean(PreferenceKeys.AUTOMATIC_UPDATE_CHECKS, true)) {
            lifecycleScope.launch(Dispatchers.IO) {
                UpdateChecker(this@MainActivity).checkUpdate(false)
            }
        }

        val navHostFragment = binding.fragment.getFragment<NavHostFragment>()
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // save start tab fragment id and apply navbar style
        startFragmentId = try {
            NavBarHelper.applyNavBarStyle(binding.bottomNav)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            R.id.homeFragment
        }

        // set default tab as start fragment
        navController.graph = navController.navInflater.inflate(R.navigation.nav).also {
            it.setStartDestination(startFragmentId)
        }

        // Prevent duplicate entries into backstack, if selected item and current
        // visible fragment is different, then navigate to selected item.
        binding.bottomNav.setOnItemReselectedListener(::onBottomItemReselected)

        binding.bottomNav.setOnItemSelectedListener {
            navigateToBottomSelectedItem(it)
        }

        if (binding.bottomNav.menu.children.none { it.itemId == startFragmentId }) deselectBottomBarItems()

        // the bottom bar menu was just built above: building it a second time cost about a
        // fifth of a second at each launch and at each theme switch
        setupExpandedLayout(bottomMenuBuilt = true)

        // setupWithNavController only checks the item of a new destination, it never
        // repairs a bar desynced by a menu rebuild or a state restore: converge both
        // bars on the destination ourselves. The listener fires immediately for the
        // current destination, so onCreate() ends in a consistent state.
        navController.addOnDestinationChangedListener(
            NavController.OnDestinationChangedListener { _, destination, _ ->
                if (binding.bottomNav.menu.findItem(destination.id) != null) {
                    syncBottomBarSelection(destination)
                } else {
                    // a channel, a playlist...: its tab is found in the back stack, which is
                    // only published once the listeners ran, so wait for it
                    binding.root.post { syncBottomBarSelection() }
                }
            }
        )

        setupSubscriptionsBadge()

        // a recreation (rotation with the mini player, theme change...) keeps the launch intent:
        // handling it again reopened its video maximized or re-navigated to its channel
        if (savedInstanceState == null) loadIntentData()

        showUserInfoDialogIfNeeded()
    }

    /**
     * Deselect all bottom bar items
     */
    private fun deselectBottomBarItems() {
        listOf(binding.bottomNav, binding.navRail).forEach { forceExclusiveCheck(it, null) }
    }

    /**
     * Highlight [checked] and only it, then restore the exclusive checkable group so
     * any later check can never leave a second tab lit.
     */
    private fun forceExclusiveCheck(bar: NavigationBarView, checked: MenuItem?) {
        bar.menu.setGroupCheckable(0, true, false)
        for (child in bar.menu.children) {
            child.isChecked = child == checked
        }
        bar.menu.setGroupCheckable(0, true, true)
    }

    /**
     * Adaptive navigation: every window at least 600dp wide (Material's medium width: a
     * phone turned sideways, a tablet, an unfolded foldable) swaps the bottom bar for the
     * floating navigation rail at the start edge. A phone held upright keeps the bottom bar.
     *
     * Idempotent, because it is called again on every configuration change: MainActivity
     * declares `orientation` in its configChanges, so rotating never recreates it.
     * [bottomMenuBuilt] skips rebuilding the bottom bar menu when the caller just built it.
     */
    private fun setupExpandedLayout(bottomMenuBuilt: Boolean = false) {
        // the width of the window, not of the device: a phone turned sideways is as wide as a
        // small tablet, and the bottom bar took a quarter of its short height from the videos
        val expanded = resources.configuration.screenWidthDp >= GridColumns.MEDIUM_WIDTH_DP

        binding.navRail.isVisible = false
        setRailSpace(0)

        if (!expanded) {
            // compact layout: bring the bottom bar back, the nav preference still
            // decides whether it is visible at all (e.g. "hide every tab").
            // applyNavBarStyle() only ever hides the bar: it must be made visible first,
            // otherwise a pass through the expanded layout (fullscreen player forcing landscape,
            // or a launch while the device was turned) left it gone for good once back in
            // portrait - no bar, no way to navigate.
            binding.bottomNav.isVisible = true
            if (!bottomMenuBuilt) {
                try {
                    NavBarHelper.applyNavBarStyle(binding.bottomNav)
                } catch (_: Exception) {
                    // a corrupted nav preference must never hide the bar
                }
            }
            // the bar was gone while the player docked: re-place both for its real height
            binding.bottomNav.post { applyPlayerContainerProgress() }
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
        // the rail menu was just built: it carries the new videos badge too
        showNewVideosBadge()

        // "hide every tab" makes applyNavBarStyle drop the bar: keep it that way
        if (!binding.navRail.isVisible) return

        if (!railNavWired) {
            railNavWired = true
            binding.navRail.setupWithNavController(navController)
            binding.navRail.setOnItemReselectedListener(::onBottomItemReselected)
            binding.navRail.setOnItemSelectedListener {
                navigateToBottomSelectedItem(it)
            }
        }

        // keep the content and the docked mini player clear of the rail and its margin
        setRailSpace(
            resources.getDimensionPixelSize(R.dimen.watube_nav_inset) +
                resources.getDimensionPixelSize(R.dimen.watube_nav_rail_width)
        )
    }

    /**
     * Keeps the fragment container and the docked mini player clear of the navigation rail,
     * which takes [space] pixels at the start of the window (0 with the bottom bar).
     */
    private fun setRailSpace(space: Int) {
        (binding.fragment.layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
            if (layoutParams.marginStart != space) {
                layoutParams.marginStart = space
                binding.fragment.layoutParams = layoutParams
            }
        }
        miniPlayerStartInset = space
        binding.container.findViewById<SingleViewTouchableMotionLayout>(R.id.playerMotionLayout)
            ?.setMiniPlayerStartInset(space)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::binding.isInitialized) return
        // uiMode is handled here (no automatic recreation), so with the "System" theme a
        // dark/light switch of Android only reached the bar and the header: every inflated
        // view kept the previous colours. Recreate to repaint everything; the player
        // fragment restores its own state. A PiP window waits for its config change on exit.
        if (isNightUiMode() != inflatedNightUiMode &&
            !PictureInPictureCompat.isInPictureInPictureMode(this)
        ) {
            recreate()
            return
        }
        setupExpandedLayout()
        // setupExpandedLayout() rebuilt the visible bar's menu, which can re-check the
        // wrong item: no destination change follows on a config change, so re-sync now
        syncBottomBarSelection()
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
     * Reselecting the lit tab: on its root, scroll to the top; deeper in its stack
     * (channel, playlist... opened from it), go back to its root like YouTube does.
     */
    private fun onBottomItemReselected(item: MenuItem) {
        if (item.itemId == navController.currentDestination?.id) {
            tryScrollToTop(binding.fragment.getFragment<NavHostFragment>()
                .childFragmentManager.fragments.firstOrNull()?.view)
        } else if (!navController.popBackStack(item.itemId, false)) {
            navigateToBottomSelectedItem(item)
        }
    }

    private fun navigateToBottomSelectedItem(item: MenuItem): Boolean {
        if (item.itemId == R.id.subscriptionsFragment) {
            newVideosCount = 0
            showNewVideosBadge()
        }

        // settings are hosted by their own activity, not by this nav graph: the tab must never
        // become the selected one (false), otherwise the bar saved "Settings" as its selection
        // and a recreation after changing a setting restored a wrong tab (Library while on Home)
        if (item.itemId == R.id.settingsFragment) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return false
        }

        // NavigationUI reports false when the restored tab stack ends on a destination
        // outside the menu (home -> channel), which made the bar keep the previous tab
        return item.onNavDestinationSelected(navController) ||
            currentTab(binding.bottomNav.menu)?.itemId == item.itemId
    }

    /**
     * The tab owning [destination]: the destination itself when it is a tab, otherwise the
     * closest tab below it in the back stack, and the start tab while the back stack is still
     * empty (state restore).
     */
    private fun currentTab(
        menu: Menu,
        destination: NavDestination? = navController.currentDestination
    ): MenuItem? {
        destination?.let { menu.findItem(it.id) }?.let { return it }
        return navController.currentBackStack.value.asReversed()
            .firstNotNullOfOrNull { menu.findItem(it.destination.id) }
            ?: menu.findItem(startFragmentId)
    }

    /**
     * The settings tab opens a separate activity, so the checked item no longer matches
     * the current destination when coming back. Re-sync both bars, without triggering
     * [setOnItemSelectedListener] (a plain [MenuItem.isChecked] never does).
     *
     * Destinations missing from the menu (search, channel, playlist...) light the tab
     * owning their back stack, i.e. the closest tab below them: restoring a saved tab
     * stack (home -> channel) otherwise kept the previously selected tab lit.
     * Hidden destinations (trends, downloads) leave no visible tab lit.
     *
     * @param shownDestination the destination being shown. Inside an
     * [NavController.OnDestinationChangedListener] it must be the one the listener received:
     * [NavController.currentBackStack] is only published after the listeners ran, so reading it
     * there still returned the previous destination and re-lit the previous tab (home ->
     * "Subscriptions" header kept "Home" lit).
     */
    private fun syncBottomBarSelection(shownDestination: NavDestination? = null) {
        if (!::binding.isInitialized || !this::navController.isInitialized) return
        val destination = shownDestination ?: navController.currentDestination
        listOf(binding.bottomNav, binding.navRail).forEach { bar ->
            val item = currentTab(bar.menu, destination) ?: return@forEach
            val destinationId = item.itemId
            if (!item.isVisible) {
                if (bar.menu.children.any { it.isChecked }) forceExclusiveCheck(bar, null)
                return@forEach
            }
            // already the unique checked item with Material's internal id in sync
            if (item.isChecked && bar.selectedItemId == destinationId) return@forEach
            forceExclusiveCheck(bar, item)
            // if the menu already matched, no flag changed, nothing was dispatched and
            // NavigationBarView kept its stale selectedItemId: toggle once to force the
            // dispatch that re-reads it (no frame is drawn between the two calls)
            if (bar.selectedItemId != destinationId) {
                bar.menu.setGroupCheckable(0, true, false)
                item.isChecked = false
                item.isChecked = true
                bar.menu.setGroupCheckable(0, true, true)
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // the view hierarchy restore re-selects NavigationBarView's saved item, which
        // can disagree with the destination the NavController restored in onCreate()
        syncBottomBarSelection()
    }

    override fun onResume() {
        super.onResume()
        pipRelaunchPending = false
        syncBottomBarSelection()
        // the glass may have been switched in the settings
        if (::binding.isInitialized &&
            glassBarApplied != !PreferenceHelper.getBoolean(PreferenceKeys.EFECTO_OFF, false)
        ) {
            applyBottomBarGlass()
        }
    }
    // endregion

    // region Mini Player
    /**
     * Progress of the player container: 0 = player maximized, 1 = mini player docked above
     * the bottom bar. It drives plain translations (see [applyPlayerContainerProgress]) and
     * not a root MotionLayout anymore: a MotionLayout re-evaluates its scene on every layout
     * pass, so navigating to a heavy tab made it drift for ~2 frames and the bar jumped behind
     * the mini player. A translation is never touched by a layout pass, so it cannot drift.
     */
    private var playerContainerProgress = 1f

    /**
     * Places the bottom bar and the player container for [progress] (0 = player maximized,
     * 1 = mini player). As soon as the player leaves the maximized state the bar is pushed off
     * screen, then slides back in while the mini player is lifted by the same amount to dock
     * right above it. Only translations change, so this never triggers a layout pass.
     */
    private fun applyPlayerContainerProgress(progress: Float = playerContainerProgress) {
        playerContainerProgress = progress
        if (!::binding.isInitialized) return
        val bar = binding.bottomNav
        // the bar floats above the system inset, so the shift has to cover both
        val shift = (if (bar.isVisible) bar.height else 0) + navBarBottomInset
        bar.translationY = shift * when {
            progress <= 0f -> 0f
            progress < BAR_HIDDEN_PROGRESS -> progress / BAR_HIDDEN_PROGRESS
            else -> (1f - progress) / (1f - BAR_HIDDEN_PROGRESS)
        }
        binding.container.translationY = -shift *
            ((progress - CONTAINER_LIFT_START) / (1f - CONTAINER_LIFT_START)).coerceIn(0f, 1f)
        updateBackdropRecording()
    }

    /** The room the floating bottom bar and the gap under it take over the screens. */
    private fun updateBottomBarOverlay() {
        val bar = binding.bottomNav
        val room = if (bar.isVisible && bar.height > 0) bar.height + navBarBottomInset else 0
        bottomBarOverlay.update(this, room)
        updateBackdropRecording()
    }

    override fun minimizePlayerContainerLayout() {
        applyPlayerContainerProgress(1f)
        // predictive-back / channel navigation closes the player without a resume and
        // without a destination change: re-check the highlight the player covered
        syncBottomBarSelection()
    }

    override fun maximizePlayerContainerLayout() = applyPlayerContainerProgress(0f)

    override fun setPlayerContainerProgress(progress: Float) {
        applyPlayerContainerProgress(progress.coerceIn(0f, 1f))
        // 1f is only reported once the player container finished closing, which is the
        // one signal fired on that path (PlayerFragment reports it on transition end)
        if (progress >= 1f) syncBottomBarSelection()
    }
    // endregion

    // region Feed
    private val subscriptionsViewModel: SubscriptionsViewModel by viewModels()

    /**
     * Initialize the notification badge showing the amount of new videos
     */
    private fun setupSubscriptionsBadge() {
        // on by default (same default as the switch in general_settings.xml)
        if (!PreferenceHelper.getBoolean(PreferenceKeys.NEW_VIDEOS_BADGE, true)) return

        // the badge counts new videos: the feed is what has to be loaded, not the channels
        subscriptionsViewModel.fetchFeed(this, forceRefresh = false)

        subscriptionsViewModel.videoFeed.observe(this) { feed ->
            val lastCheckedFeedTime = PreferenceHelper.getLastCheckedFeedTime(seenByUser = true)
            val lastSeenVideoIndex = feed.orEmpty()
                .filter { !it.isUpcoming }
                .indexOfFirst { it.uploaded <= lastCheckedFeedTime }
            // nothing new (index below 1) drops a badge left over from a previous feed
            newVideosCount = lastSeenVideoIndex.coerceAtLeast(0)
            showNewVideosBadge()
        }
    }

    /**
     * Shows [newVideosCount] on the subscriptions tab, or no badge at 0. The badge is mirrored
     * on both bars, only one of them being visible at a time; a bar without that tab (the rail
     * of a phone, never built, or a tab the user removed) gets none, Material refusing a badge
     * for an item that is not in the menu.
     */
    private fun showNewVideosBadge() {
        listOf(binding.bottomNav, binding.navRail).forEach { bar ->
            if (bar.menu.findItem(R.id.subscriptionsFragment) == null) return@forEach
            if (newVideosCount < 1) {
                bar.removeBadge(R.id.subscriptionsFragment)
                return@forEach
            }
            bar.getOrCreateBadge(R.id.subscriptionsFragment).apply {
                number = newVideosCount
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
    // endregion

    // region Playlists
    // registering for activity results is only possible, this here should have been part of
    // PlaylistOptionsBottomSheet instead if Android allowed us to
    private var playlistExportFormat: ImportFormat = ImportFormat.NEWPIPE
    private var exportPlaylistId: String? = null
    // endregion

    // region Picture-in-Picture
    /** garde one-shot : loadIntentData() peut être ré-entré par onNewIntent() suite à la
     *  relance auto PiP, et une boucle d'intents fait pin/unpin de la fenêtre PiP. */
    private var pipRelaunchPending = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        runOnPlayerFragment {
            onUserLeaveHint()
            true
        }
    }
    // endregion

    // region Playlists
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
    // endregion

    // region Search
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

    /**
     * Runs a search query the way the toolbar SearchView submit used to: pasted
     * URLs open their video, channel or playlist right away, everything else lands
     * on the results screen and is recorded in the search history.
     *
     * @param query raw user input, trimmed and ignored when blank
     */
    fun openSearchResults(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        // handle inserted YouTube-like URLs and directly open the referenced
        // channel, playlist or video instead of showing search results
        if (trimmed.toHttpUrlOrNull() != null) {
            val queryIntent = IntentHelper.resolveType(trimmed.toUri())

            val didNavigate = navigateToMediaByIntent(queryIntent) {
                navController.popBackStack(R.id.searchFragment, true)
            }
            if (didNavigate) return
        }

        navController.navigate(NavDirections.showSearchResults(trimmed))

        addSearchQueryToHistory(trimmed)
    }

    /**
     * Clears the focus of the in-content search input when one currently holds it
     *
     * @return whether an input was focused and has been cleared
     */
    override fun clearSearchViewFocus(): Boolean {
        val focused = currentFocus
        if (focused !is EditText) return false

        focused.clearFocus()
        // an EditText does not dismiss the IME on clearFocus() by itself
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(focused.windowToken, 0)
        return true
    }
    // endregion

    // region Deep Links
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

        // Run a search query passed by the intent (search_query links). It used to be
        // parked until the next menu rebuild, which could drop it on onNewIntent().
        intent?.getStringExtra(IntentData.query)?.let(MediaIds::query)?.let {
            openSearchResults(it)
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
        // MainActivity is exported: the IDs may come from any app, only well-formed ones are used
        intent.getStringExtra(IntentData.channelId)?.let(MediaIds::channelId)?.let {
            actionBefore()
            navController.navigate(NavDirections.openChannel(channelId = it))
            return true
        }
        intent.getStringExtra(IntentData.channelName)?.let(MediaIds::channelName)?.let {
            actionBefore()
            navController.navigate(NavDirections.openChannel(channelName = it))
            return true
        }
        intent.getStringExtra(IntentData.playlistId)?.let(MediaIds::playlistId)?.let {
            actionBefore()
            navController.navigate(NavDirections.openPlaylist(playlistId = it))
            return true
        }
        intent.getStringArrayExtra(IntentData.videoIds)?.let(MediaIds::videoIds)?.let {
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

        intent.getStringExtra(IntentData.videoId)?.let(MediaIds::videoId)?.let {
            if (binding.bottomNav.menu.isNotEmpty()) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (!::binding.isInitialized) return
        loadIntentData()
    }
    // endregion

    // region Video Player
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // onCreate() peut avoir quitté avant d'initialiser binding (pas de réseau)
        if (!::binding.isInitialized) return super.onKeyUp(keyCode, event)
        // don't forward key events to the player while the search text input is used
        if (this::navController.isInitialized &&
            navController.currentDestination?.id == R.id.searchFragment
        ) return false

        if (runOnPlayerFragment { this@runOnPlayerFragment.onKeyUp(keyCode, event) }) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }
    // endregion

    // region Updates
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

        SecureAlertDialogBuilder(this)
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
    // endregion
}
