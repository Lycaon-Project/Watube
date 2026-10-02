package com.watube.yard.ui.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.helpers.LocaleHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.ui.adapters.IconsSheetAdapter
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.NavBarOptionsDialog
import com.watube.yard.ui.dialogs.RequireRestartDialog
import com.watube.yard.ui.sheets.IconsBottomSheet
import java.util.Locale

class AppearanceSettings : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.appearance_settings, rootKey)

        val themeToggle = findPreference<ListPreference>(PreferenceKeys.THEME_MODE)
        themeToggle?.setOnPreferenceChangeListener { _, newValue ->
            // OLED / pure black only makes sense in dark mode: switching to light turns it off
            if (newValue == "L") {
                findPreference<SwitchPreferenceCompat>(PreferenceKeys.PURE_THEME)?.isChecked = false
            }
            RequireRestartDialog().show(childFragmentManager, RequireRestartDialog::class.java.name)
            true
        }

        val pureTheme = findPreference<SwitchPreferenceCompat>(PreferenceKeys.PURE_THEME)
        pureTheme?.setOnPreferenceChangeListener { _, _ ->
            RequireRestartDialog().show(childFragmentManager, RequireRestartDialog::class.java.name)
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
        val language = findPreference<ListPreference>("language")
        if (!LocaleHelper.isPerAppLocaleSettingSupported()) {
            language?.setOnPreferenceChangeListener { _, _ ->
                RequireRestartDialog().show(
                    childFragmentManager,
                    RequireRestartDialog::class.java.name
                )
                true
            }
            val languages = requireContext().resources.getStringArray(R.array.languageCodes)
                .map { code ->
                    val locale = LocaleHelper.getLocaleFromAndroidCode(code)

                    // each language's name is displayed in its own language,
                    // e.g. 'de': 'Deutsch', FR: 'Francais', ...
                    locale.toString() to locale.getDisplayName(locale)
                }.sortedBy { it.second.lowercase() }
            language?.entries =
                arrayOf(requireContext().getString(R.string.systemLanguage)) + languages.map { it.second }
            language?.entryValues = arrayOf("sys") + languages.map { it.first }
        } else {
            // on newer Android versions, the language is set through Android settings

            // set displayed current settings value (i.e. current app language)
            val currentLocale = Locale.getDefault()
            language?.entries = arrayOf(currentLocale.displayLanguage)
            language?.entryValues = arrayOf(currentLocale.isO3Country)
            language?.value = currentLocale.isO3Country

            // open Android settings for per-app language preference for the app
            language?.setOnPreferenceClickListener { _ ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                            .setData(Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
                    )
                } catch (e: Exception) {
                    context?.toastFromMainThread("Failed to open per-app language settings: ${e.message}")
                }
                true
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key == "language" && LocaleHelper.isPerAppLocaleSettingSupported()) return

        super.onDisplayPreferenceDialog(preference)
    }
}
