package com.github.libretube.helpers

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.edit
import com.github.libretube.BuildConfig
import com.github.libretube.constants.PreferenceKeys
import java.security.SecureRandom
import java.util.Calendar

/**
 * Central place for all Watube anti fingerprinting decisions.
 *
 * The master switch ([isHardeningEnabled]) is enabled by default and controls every
 * identifier that could be used to link requests coming from this device together:
 *  - SIM / carrier based country detection (see [LocaleHelper.getDetectedCountry])
 *  - the SponsorBlock user id, optionally rotated daily (see [rotateSponsorBlockUserIdDaily])
 *  - the reported User-Agent of third party services (SponsorBlock, DeArrow), which is
 *    normalized to a single generic value instead of leaking package name and version
 *  - WebView level information (see [applyWebViewAntiFingerprinting])
 *
 * All measures are purely client side and don't require any additional permission,
 * so they can never break existing functionality.
 */
object PrivacyHelper {
    /**
     * Generic browser-like user agent used for all third party API calls when hardening
     * is enabled. It intentionally contains no device, app or version information, so all
     * hardened Watube users look the same to external observers (Tor/Mullvad style
     * "blend into the crowd" strategy).
     */
    const val GENERIC_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)" +
            " Chrome/131.0.0.0 Safari/537.36"

    private const val KEY_SB_UUID_DAY = "sb_user_id_day"
    private const val USER_ID_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom = SecureRandom()

    /**
     * Hardening is on by default: privacy should be opt-out, not opt-in.
     */
    fun isHardeningEnabled(): Boolean {
        return PreferenceHelper.getBoolean(PreferenceKeys.PRIVACY_HARDENING, true)
    }

    /**
     * Convenience overload for call sites that already hold a context.
     */
    @JvmOverloads
    fun isHardeningEnabled(context: Context? = null): Boolean = isHardeningEnabled()

    /**
     * Whether the SponsorBlock user id should be regenerated every day.
     */
    fun rotateSponsorBlockUserIdDaily(): Boolean {
        return isHardeningEnabled() &&
            PreferenceHelper.getBoolean(PreferenceKeys.RESET_SB_UUID_DAILY, true)
    }

    /**
     * Returns the stored SponsorBlock user id, or null when it has to be regenerated
     * (missing id, or new day while daily rotation is active).
     */
    fun getValidSponsorBlockUserId(): String? {
        val stored = PreferenceHelper.getString(PreferenceKeys.SB_USER_ID, "")
        if (stored.isEmpty()) return null

        if (!rotateSponsorBlockUserIdDaily()) return stored

        val day = PreferenceHelper.getString(KEY_SB_UUID_DAY, "")
        return stored.takeIf { day == currentDayStamp() }
    }

    /**
     * Stores a freshly generated SponsorBlock user id together with its validity day.
     */
    fun storeSponsorBlockUserId(userId: String) {
        PreferenceHelper.settings.edit {
            putString(PreferenceKeys.SB_USER_ID, userId)
            putString(KEY_SB_UUID_DAY, currentDayStamp())
        }
    }

    /**
     * Generate a random, uncorrelated SponsorBlock user id using [SecureRandom],
     * so that submissions can never be linked back to the device.
     */
    fun generateSponsorBlockUserId(): String = buildString(30) {
        repeat(30) { append(USER_ID_CHARS[secureRandom.nextInt(USER_ID_CHARS.length)]) }
    }

    /**
     * The user agent to use for third party services (SponsorBlock, DeArrow, ...).
     * When hardening is enabled, a stable generic value is returned that doesn't leak
     * the package name, app version or device model.
     */
    fun getUserAgent(context: Context): String {
        return if (isHardeningEnabled()) {
            GENERIC_USER_AGENT
        } else {
            "${context.packageName}/${BuildConfig.VERSION_NAME}"
        }
    }

    /**
     * Applies Mullvad-browser/Tor inspired WebView hardening where the public Android
     * APIs allow it without breaking the PoToken challenge:
     *  - disables the DOM cache, a classic persistent fingerprinting vector
     *  - blocks third party cookies for the embedded view
     * Web storage (localStorage) stays untouched on purpose: the BotGuard/PoToken
     * challenge relies on it, disabling it would break high quality playback.
     */
    fun applyWebViewAntiFingerprinting(webView: WebView) {
        if (!isHardeningEnabled()) return

        runCatching {
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webView.settings.javaScriptCanOpenWindowsAutomatically = false
        }

        runCatching {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        }
    }

    /**
     * Drops the current SponsorBlock user id; a fresh random one is generated on the
     * next submission. Used by the "reset identifiers" action in privacy settings.
     */
    fun resetSponsorBlockUserId() {
        PreferenceHelper.settings.edit {
            remove(PreferenceKeys.SB_USER_ID)
            remove(KEY_SB_UUID_DAY)
        }
    }

    /**
     * Clears everything a WebView may have persisted about the user (cookies and web
     * storage). Called from the manual cache cleanup.
     */
    fun clearWebViewData() {
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }

    private fun currentDayStamp(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }
}
