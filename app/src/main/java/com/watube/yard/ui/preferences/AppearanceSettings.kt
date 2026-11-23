package com.watube.yard.ui.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.ui.adapters.IconsSheetAdapter
import com.watube.yard.ui.base.BaseActivity
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.base.ThemeTransition
import com.watube.yard.ui.dialogs.NavBarOptionsDialog
import com.watube.yard.ui.dialogs.RequireRestartDialog
import com.watube.yard.ui.sheets.IconsBottomSheet
import java.util.Locale

// region Appearance
class AppearanceSettings : BasePreferenceFragment() {
    /**
     * Applies a theme change at once, with no restart: [apply] writes it, then every screen
     * is rebuilt in the new theme, this one dissolving into it.
     */
    private fun switchTheme(apply: () -> Unit) {
        val screen = activity as? BaseActivity ?: return apply()
        ThemeTransition.switchTheme(screen, apply)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appearance_settings, rootKey)

        val themeToggle = findPreference<ListPreference>(PreferenceKeys.THEME_MODE)
        themeToggle?.setOnPreferenceChangeListener { _, newValue ->
            val mode = newValue as? String ?: return@setOnPreferenceChangeListener false
            // OLED / pure black only makes sense in dark mode: switching to light turns it off
            val goingLight = mode == "L"
            if (goingLight) {
                findPreference<SwitchPreferenceCompat>(PreferenceKeys.PURE_THEME)?.isChecked = false
            }
            switchTheme {
                PreferenceHelper.putString(PreferenceKeys.THEME_MODE, mode)
                if (goingLight) PreferenceHelper.putBoolean(PreferenceKeys.PURE_THEME, false)
            }
            true
        }

        val pureTheme = findPreference<SwitchPreferenceCompat>(PreferenceKeys.PURE_THEME)
        pureTheme?.setOnPreferenceChangeListener { _, newValue ->
            val pure = newValue as? Boolean ?: return@setOnPreferenceChangeListener false
            switchTheme { PreferenceHelper.putBoolean(PreferenceKeys.PURE_THEME, pure) }
            true
        }

        // Watube: text size 88–116 % (applied in BaseActivity.attachBaseContext)
        val fontScale = findPreference<SeekBarPreference>(PreferenceKeys.FONT_SCALE)
        fontScale?.min = 88
        fontScale?.summaryProvider = Preference.SummaryProvider<SeekBarPreference> { "${it.value} %" }
        fontScale?.setOnPreferenceChangeListener { _, _ ->
            RequireRestartDialog().show(childFragmentManager, RequireRestartDialog::class.java.name)
            true
        }

        // Feed density (cozy/compact) is read when feeds are rebuilt: no restart needed
        findPreference<ListPreference>(PreferenceKeys.FEED_DENSITY)

        val appFont = findPreference<ListPreference>(PreferenceKeys.APP_FONT)
        appFont?.setOnPreferenceChangeListener { _, _ ->
            RequireRestartDialog().show(childFragmentManager, RequireRestartDialog::class.java.name)
            true
        }

        val changeIcon = findPreference<Preference>(PreferenceKeys.APP_ICON)
        val iconPref = PreferenceHelper.getString(
            PreferenceKeys.APP_ICON,
            IconsSheetAdapter.Companion.AppIcon.Default.activityAlias
        )
        IconsSheetAdapter.availableIcons.firstOrNull { it.activityAlias == iconPref }?.let {
            changeIcon?.summary = getString(it.nameResource)
        }
        changeIcon?.setOnPreferenceClickListener {
            IconsBottomSheet().show(childFragmentManager)
            true
        }

        val navBarOptions = findPreference<Preference>(PreferenceKeys.NAVBAR_ITEMS)
        navBarOptions?.setOnPreferenceClickListener {
            NavBarOptionsDialog().show(childFragmentManager, null)
            true
        }

        setupLanguagePref()
    }

    /**
     * App language (moved here from the former "General" screen: it is a personalisation
     * choice, so it belongs to the "Apparence" category of the settings hub).
     */
    private fun setupLanguagePref() {
        val language = findPreference<ListPreference>("language") ?: return
        // the language is set through Android's per-app language settings (Android 13+)

        // set displayed current settings value (i.e. current app language)
        val currentLocale = Locale.getDefault()
        // Préférence de LANGUE : utiliser le code de langue (jamais le code pays isO3Country,
        // sémantiquement faux et susceptible de lever MissingResourceException). `language`
        // (ISO 639) ne lève jamais d'exception.
        language.entries = arrayOf(currentLocale.displayLanguage)
        language.entryValues = arrayOf(currentLocale.language)
        language.value = currentLocale.language

        // open Android settings for per-app language preference for the app
        language.setOnPreferenceClickListener { _ ->
            try {
                startActivity(
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        .setData(Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
                )
            } catch (e: Exception) {
                // some systems have no per-app language screen
                Log.e(TAG(), e.toString())
                context?.toastFromMainThread(R.string.error_language_settings)
            }
            true
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "language") return

        super.onDisplayPreferenceDialog(preference)
    }
}
// endregion
