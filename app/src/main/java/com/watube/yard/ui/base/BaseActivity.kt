package com.watube.yard.ui.base

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.AppLock
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.ScreenPrivacy
import com.watube.yard.helpers.ThemeHelper
import com.watube.yard.helpers.ThemeHelper.getThemeMode
import com.watube.yard.helpers.WindowHelper
import com.watube.yard.ui.tools.RestMode

// region UI Components
/**
 * Activity that applies the Watube theme and the in-app language
 */
open class BaseActivity : AppCompatActivity() {

    open val isDialogActivity: Boolean = false

    // Cache de la préférence d'orientation pour éviter les recalculs
    // IMPORTANT : Cette propriété doit rester publique pour être accessible depuis PlayerFragment
    val screenOrientationPref: Int by lazy {
        when (PreferenceHelper.getString(
            PreferenceKeys.ORIENTATION,
            resources.getString(R.string.config_default_orientation_pref)
        )) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_USER
            else -> ActivityInfo.SCREEN_ORIENTATION_USER // Fallback sécurisé
        }
    }

    /**
     * Whether the phone of the user has a cutout like a notch or not
     */
    var hasCutout: Boolean = false
        private set

    // Référence au listener pour pouvoir le nettoyer
    private var insetsListener: View.OnApplyWindowInsetsListener? = null

    /** The theme switch (ThemeTransition.generation) this screen was built under. */
    var themeGeneration = 0
        private set

    /** Day or night, as this screen was built (Configuration.UI_MODE_NIGHT_*). */
    private var builtNight = Configuration.UI_MODE_NIGHT_UNDEFINED

    /** A rebuild is on its way: further requests (AppCompat, the theme switch) add nothing. */
    private var rebuilding = false
    override fun onCreate(savedInstanceState: Bundle?) {
        // Appliquer le thème uniquement si nécessaire
        applyThemeIfNeeded()

        // Configurer l'orientation avant super.onCreate pour éviter les changements de configuration
        requestedOrientation = screenOrientationPref

        // Détecter les cutouts de manière asynchrone pour ne pas bloquer le premier rendu
        setupCutoutDetection()

        super.onCreate(savedInstanceState)

        // Activer edge to edge uniquement si pas en mode dialog. After super.onCreate: a
        // screen that handles uiMode itself (MainActivity) only gets its day/night resources
        // there, and the bars took their style from the old theme when rebuilt by a theme
        // switch (dark status icons on the dark theme, a light scrim behind the buttons).
        if (!isDialogActivity) {
            enableEdgeToEdge()
        }

        themeGeneration = ThemeTransition.generation
        builtNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (savedInstanceState != null) refreshWindowBackground()
        ThemeTransition.onCreated(this)
        ScreenPrivacy.applyTo(window)
        ScreenPrivacy.applyRecentsPreviewTo(this)
        supportFragmentManager.registerFragmentLifecycleCallbacks(ScreenPrivacy.dialogWatcher, true)

        if (!isDialogActivity) keepContentClearOfSideInsets()
    }

    /**
     * Edge to edge: every screen handles the top and bottom system bars itself, but none
     * handled the sides. With 3-button navigation the bar sits on a side in landscape (and the
     * camera cutout on the other), so content and buttons went underneath them. The side
     * insets are applied once here, on the content root, and passed on untouched so each
     * screen keeps its own top / bottom handling.
     */
    private fun keepContentClearOfSideInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val sides = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = sides.left, right = sides.right)
            insets
        }
    }

    /**
     * Applique le thème uniquement si nécessaire
     */
    private fun applyThemeIfNeeded() {
        if (isDialogActivity) {
            ThemeHelper.applyDialogActivityTheme(this)
        } else {
            ThemeHelper.updateTheme(this)
        }
    }

    /**
     * Configure la détection des cutouts de manière optimisée
     */
    private fun setupCutoutDetection() {
        if (insetsListener != null) return

        insetsListener = View.OnApplyWindowInsetsListener { view, insets ->
            hasCutout = WindowHelper.hasCutout(view)
            view.onApplyWindowInsets(insets)
        }
        window.decorView.setOnApplyWindowInsetsListener(insetsListener)
    }

    override fun attachBaseContext(newBase: Context?) {
        // the night mode has to be known before AppCompat builds this activity's configuration
        // in super.attachBaseContext(): set later, the first activity of the process started with
        // the system's day/night instead of the app theme, and enableEdgeToEdge() picked the
        // status bar icon colour from it (white icons on the light theme when Android is dark)
        val uiPref = PreferenceHelper.getString(PreferenceKeys.THEME_MODE, "D")
        AppCompatDelegate.setDefaultNightMode(getThemeMode(uiPref))

        val baseContext = newBase ?: return super.attachBaseContext(null)
        super.attachBaseContext(baseContext)

        val configuration = Configuration()
        var needsConfig = false

        // Watube: text size from Settings > Appearance (88–116 %)
        val fontScalePercent = PreferenceHelper.getInt(PreferenceKeys.FONT_SCALE, 100)
        if (fontScalePercent != 100) {
            configuration.fontScale = fontScalePercent / 100f
            needsConfig = true
        }

        if (needsConfig) {
            applyOverrideConfiguration(configuration)
        }
    }

    /**
     * Android keeps the window of a screen rebuilt in front, and with it the background of
     * the theme it was built with: after a theme switch the gaps between the cards of a
     * screen stayed in the old colour. The rebuilt screen takes the one of its new theme.
     */
    private fun refreshWindowBackground() {
        theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground)).use {
            it.getDrawable(0)?.let(window::setBackgroundDrawable)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // the content is in place: lay the old screen over it and open the new theme
        ThemeTransition.onPostCreated(this)
    }

    override fun onStart() {
        super.onStart()
        // the theme changed while this screen was away (a screen opened mid-switch)
        if (themeGeneration != ThemeTransition.generation) recreate()
        AppLock.onStart(this)
    }

    override fun recreate() {
        if (rebuilding) return
        rebuilding = true
        super.recreate()
    }

    /**
     * The screens that keep their views across a day/night change (uiMode in the manifest)
     * would stay in the old colours when the system switches in the "follow the system"
     * mode: they rebuild, fading from the old look.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ThemeTransition.nightChanged(this, newConfig, builtNight)) {
            ThemeTransition.followSystem(this)
        }
    }

    override fun onStop() {
        AppLock.onStop(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // the settings may have changed on another screen
        ScreenPrivacy.applyTo(window)
        ScreenPrivacy.applyRecentsPreviewTo(this)
        // the scheduled rest mode covers whichever screen is in front
        RestMode.onResume(this)
    }

    override fun onPause() {
        RestMode.onPause(this)
        super.onPause()
    }

    /**
     * Nettoie les ressources pour éviter les fuites mémoire
     */
    override fun onDestroy() {
        super.onDestroy()
        ThemeTransition.onDestroyed(this)
        if (insetsListener != null) {
            window.decorView.setOnApplyWindowInsetsListener(null)
            insetsListener = null
        }
    }

    /**
     * Méthode conservée pour compatibilité, mais utilise le cache
     */
    open fun requestOrientationChange() {
        requestedOrientation = screenOrientationPref
    }
}
// endregion
