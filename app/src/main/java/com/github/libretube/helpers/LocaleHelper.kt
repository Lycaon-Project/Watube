package com.github.libretube.helpers

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.obj.Country
import java.util.Locale

object LocaleHelper {
    @Deprecated("Only used for SDKs below 33 for compatibility")
    fun getAppLocale(): Locale {
        val languageName = PreferenceHelper.getString(PreferenceKeys.LANGUAGE, "sys")
        return when {
            languageName == "sys" -> Locale.getDefault()
            else -> getLocaleFromAndroidCode(languageName)
        }
    }

    fun isPerAppLocaleSettingSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun getLocaleFromAndroidCode(code: String): Locale {
        val normalizedCode = code.replace("-r", "-")
        return if (normalizedCode.contains("-")) {
            val parts = normalizedCode.split("-", limit = 2)
            Locale(parts[0], parts[1].uppercase())
        } else {
            Locale(normalizedCode)
        }
    }

    /**
     * Watube anti-fingerprinting (inspired by Mullvad Browser / Tor Browser policies):
     * when privacy hardening is enabled, the SIM and cellular-network country are never
     * read. Those values leak the carrier and the real roaming location and act as a
     * stable fingerprinting signal across apps/sessions. Only the user-visible locale
     * country (already exposed to every website via Accept-Language) is used instead.
     */
    fun getDetectedCountry(context: Context): String {
        if (!PrivacyHelper.isHardeningEnabled(context)) {
            detectSIMCountry(context)?.let { return it }
            detectNetworkCountry(context)?.let { return it }
        }
        return detectLocaleCountry(context) ?: "US"
    }

    private fun detectSIMCountry(context: Context): String? {
        return context.getSystemService<TelephonyManager>()?.simCountryIso?.ifEmpty { null }
    }

    private fun detectNetworkCountry(context: Context): String? {
        return context.getSystemService<TelephonyManager>()?.networkCountryIso?.ifEmpty { null }
    }

    private fun detectLocaleCountry(context: Context): String? {
        return ConfigurationCompat.getLocales(context.resources.configuration)[0]!!.country
            .ifEmpty { null }
    }

    fun getAvailableCountries(): List<Country> {
        return Locale.getISOCountries()
            .map { Country(Locale("", it).displayCountry, it) }
            .sortedBy { it.name }
    }

    fun getAvailableLocales(): List<Country> {
        return Locale.getAvailableLocales()
            .distinctBy { it.language }
            .map { Country(it.displayLanguage, it.language) }
            .sortedBy { it.name }
    }
}
