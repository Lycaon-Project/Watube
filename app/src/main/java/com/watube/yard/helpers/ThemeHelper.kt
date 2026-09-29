package com.watube.yard.helpers

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.text.Spanned
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.ui.adapters.IconsSheetAdapter
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

object ThemeHelper {
    /**
     * Set the theme, including accent color and night mode
     */
    fun updateTheme(activity: AppCompatActivity) {
        val accentColor = currentAccent()
        if (PreferenceHelper.getString(PreferenceKeys.ACCENT_COLOR, "") != accentColor) {
            PreferenceHelper.putString(PreferenceKeys.ACCENT_COLOR, accentColor)
        }

        activity.setTheme(getTheme(accentColor))
        if (accentColor == "my") DynamicColors.applyToActivityIfAvailable(activity)

        val pureThemeEnabled = PreferenceHelper.getBoolean(
            PreferenceKeys.PURE_THEME,
            false
        )
        if (pureThemeEnabled) activity.theme.applyStyle(R.style.Pure, true)

        applyFontStyle(activity)
    }

    /**
     * Apply the optional "Watube" typeface selected in Settings > Appearance.
     * The default value ("system") applies nothing, so the stock font is kept.
     */
    private fun applyFontStyle(activity: Activity) {
        if (PreferenceHelper.getString(PreferenceKeys.APP_FONT, "system") != "system") {
            activity.theme.applyStyle(R.style.WatubeFontFigtree, true)
        }
    }

    /**
     * Update the accent color of the app and apply dynamic colors if needed
     */
    private fun getTheme(accentColor: String): Int {
        return when (accentColor) {
            // set the accent color, use the pure black/white theme if enabled
            "my" -> R.style.BaseTheme
            "aqua" -> R.style.Theme_Watube
            "azur" -> R.style.Theme_Azur
            "corail" -> R.style.Theme_Corail
            "lavande" -> R.style.Theme_Lavande
            "ambre" -> R.style.Theme_Ambre
            else -> throw IllegalArgumentException()
        }
    }

    /** true if the given stored accent value is still supported by the current version */
    fun isValidAccent(accentColor: String): Boolean = accentColor in setOf(
        "my", "aqua", "azur", "corail", "lavande", "ambre"
    )

    fun applyDialogActivityTheme(activity: Activity) {
        activity.theme.applyStyle(R.style.DialogActivity, true)
        applyFontStyle(activity)
    }

    /**
     * set the theme mode (light, dark, auto)
     */
    fun getThemeMode(themeMode: String): Int {
        return when (themeMode) {
            "A" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            "L" -> AppCompatDelegate.MODE_NIGHT_NO
            "D" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    /**
     * change the app icon
     */
    fun changeIcon(context: Context, newLogoActivityAlias: String) {
        // Disable Old Icon(s)
        for (appIcon in IconsSheetAdapter.availableIcons) {
            val activityClass = context.packageName.removeSuffix(".debug") + "." + appIcon.activityAlias

            // remove old icons
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // set the class name for the activity alias
        val newLogoActivityClass = context.packageName.removeSuffix(".debug") + "." + newLogoActivityAlias
        // Enable New Icon
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, newLogoActivityClass),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Get a color by a color resource attr
     */
    fun getThemeColor(context: Context, colorCode: Int) =
        MaterialColors.getColor(context, colorCode, Color.TRANSPARENT)

    /**
     * Get the styled app name
     */
    fun getStyledAppName(context: Context): Spanned {
        val colorPrimary = getThemeColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
        val hexColor = "#%06X".format(0xFFFFFF and colorPrimary)
        return "Wa<span style='color:$hexColor'>tube</span>"
            .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    /**
     * True when the UI currently renders with dark surfaces.
     *
     * The system night flag alone is not enough: every Watube accent ships with dark-grey
     * neutrals (dark UI in light mode) while Material You and the pure theme still follow
     * the system and stay light.
     */
    fun isDarkMode(context: Context): Boolean {
        val darkModeFlag =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (darkModeFlag == Configuration.UI_MODE_NIGHT_YES) return true
        // flat white surfaces, whatever the accent
        if (PreferenceHelper.getBoolean(PreferenceKeys.PURE_THEME, false)) return false
        // Material You follows the wallpaper: light surfaces while the system is light
        return currentAccent() != "my"
    }

    /** The accent currently in use, falling back to the same default as [updateTheme] */
    private fun currentAccent(): String {
        val accentColor = PreferenceHelper.getString(PreferenceKeys.ACCENT_COLOR, "")
        return if (isValidAccent(accentColor)) accentColor
        else if (DynamicColors.isDynamicColorAvailable()) "my" else "aqua"
    }
}
