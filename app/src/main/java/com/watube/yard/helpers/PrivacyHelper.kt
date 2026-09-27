package com.watube.yard.helpers

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.edit
import com.watube.yard.constants.PreferenceKeys
import java.security.SecureRandom

private const val HOUR_MILLIS = 60L * 60 * 1000

/**
 * Central place for all Watube anti fingerprinting decisions.
 *
 * The master switch ([isHardeningEnabled]) is enabled by default and controls every
 * identifier that could be used to link requests coming from this device together:
 *  - SIM / carrier based country detection (see [LocaleHelper.getDetectedCountry])
 *  - the SponsorBlock user id, rotated on the shared [RotationFrequency] schedule
 *  - the neutral region used for trending/home requests (see [getNeutralRegion])
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

    /**
     * How often the rotating client identifiers (SponsorBlock user id **and** neutral
     * region) are regenerated. A single shared frequency keeps every identifier in sync
     * so an observer can never correlate "the id changed" with "the region changed" at
     * two different points in time.
     *
     * [MANUAL] never rotates on its own: only an explicit "reset identifiers" action
     * (or a change of this setting) produces a new value.
     */
    enum class RotationFrequency(val value: String, val periodMillis: Long?) {
        EVERY_12_HOURS("12h", 12 * HOUR_MILLIS),
        EVERY_24_HOURS("24h", 24 * HOUR_MILLIS),
        MANUAL("manual", null);

        companion object {
            fun fromValue(value: String?): RotationFrequency =
                entries.firstOrNull { it.value == value } ?: EVERY_24_HOURS
        }
    }

    /**
     * Countries the neutral region rotates through. Chosen to be large, well supported
     * trending regions so the visible content stays usable while never pointing back at
     * the real location of the user (Tor/Mullvad "blend into the crowd" strategy).
     */
    private val NEUTRAL_REGIONS = arrayOf(
        "US", "CA", "GB", "DE", "FR", "NL", "SE", "JP", "AU", "BR", "IN", "IT"
    )

    private const val USER_ID_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val MANUAL_CYCLE_STAMP = "manual"
    private val secureRandom = SecureRandom()
    private val rotationLock = Any()

    /**
     * Hardening is on by default: privacy should be opt-out, not opt-in.
     */
    fun isHardeningEnabled(): Boolean {
        return PreferenceHelper.getBoolean(PreferenceKeys.PRIVACY_HARDENING, true)
    }

    /**
     * Convenience overload for call sites that already hold a context.
     * The parameter is ignored; kept for API compatibility with callers like LocaleHelper.
     */
    fun isHardeningEnabledWith(context: Context): Boolean = isHardeningEnabled()

    /**
     * The frequency chosen by the user for every rotating identifier.
     *
     * Falls back on the legacy `reset_sb_uuid_daily` boolean so installations that never
     * saw the frequency preference keep behaving exactly as before (daily on, off otherwise).
     */
    fun rotationFrequency(): RotationFrequency {
        val stored = PreferenceHelper.getString(PreferenceKeys.PRIVACY_ROTATION_FREQUENCY, "")
        if (stored.isNotEmpty()) return RotationFrequency.fromValue(stored)

        val legacyDaily = PreferenceHelper.getBoolean(PreferenceKeys.RESET_SB_UUID_DAILY, true)
        return if (legacyDaily) RotationFrequency.EVERY_24_HOURS else RotationFrequency.MANUAL
    }

    /**
     * Identifier rotation is active unless the user explicitly selected "manual".
     */
    fun isRotationAutomatic(): Boolean = rotationFrequency() != RotationFrequency.MANUAL

    /**
     * Shared cycle stamp of the current rotation window. Every rotating identifier stores
     * the stamp it was generated for; when the stored stamp no longer matches, the value
     * is stale and gets replaced. Using one stamp for all identifiers guarantees they all
     * rotate at the very same moment.
     */
    fun currentCycleStamp(): String {
        val period = rotationFrequency().periodMillis ?: return MANUAL_CYCLE_STAMP
        return (System.currentTimeMillis() / period).toString()
    }

    /**
     * Returns the stored SponsorBlock user id, or null when it has to be regenerated
     * (missing id, or a new rotation cycle while automatic rotation is active).
     */
    fun getValidSponsorBlockUserId(): String? {
        val stored = PreferenceHelper.getString(PreferenceKeys.SB_USER_ID, "")
        if (stored.isEmpty()) return null

        if (!isRotationAutomatic()) return stored

        val cycle = PreferenceHelper.getString(PreferenceKeys.SB_USER_ID_CYCLE, "")
        return stored.takeIf { cycle == currentCycleStamp() }
    }

    /**
     * Stores a freshly generated SponsorBlock user id together with the rotation cycle
     * it is valid for.
     */
    fun storeSponsorBlockUserId(userId: String) {
        PreferenceHelper.settings.edit {
            putString(PreferenceKeys.SB_USER_ID, userId)
            putString(PreferenceKeys.SB_USER_ID_CYCLE, currentCycleStamp())
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
     *
     * Watube 27A1: the [context] parameter is kept for call-site compatibility but is
     * intentionally unused - even the legacy per-package identifier was removed from the
     * non-hardened path since it leaked the application id (a classic fingerprinting
     * vector) and provided no functional benefit to any endpoint.
     */
    @Suppress("UNUSED_PARAMETER")
    fun getUserAgent(context: Context): String = GENERIC_USER_AGENT

    /**
     * Canonical generic user agent used across the whole app. Every component that talks
     * to a third party service (SponsorBlock, DeArrow, Return YouTube Dislike proxy,
     * NewPipe extractor downloader, Piped instances) must use this single value so that
     * all Watube users are indistinguishable from one another on the wire.
     */
    val userAgent: String get() = GENERIC_USER_AGENT

    /**
     * Optional "neutral region" mode (Tor/Mullvad style): when enabled, the trending/home
     * region sent to the backend is picked from [NEUTRAL_REGIONS] instead of the device
     * locale country, so requests don't reveal where the user actually lives. The region
     * is re-picked on every rotation cycle, synchronized with the SponsorBlock id.
     * Off by default because it changes visible content recommendations; it can be
     * toggled in the privacy settings.
     */
    fun isNeutralRegionEnabled(): Boolean {
        return isHardeningEnabled() &&
            PreferenceHelper.getBoolean(PreferenceKeys.PRIVACY_NEUTRAL_REGION, false)
    }

    /**
     * The neutral region currently in use, rotating it when the current cycle expired.
     * Never returns a different value within the same cycle so every screen (home,
     * trending, cached feeds) agrees on a single region during the whole window.
     */
    fun getNeutralRegion(): String {
        synchronized(rotationLock) {
            val cycle = currentCycleStamp()
            val active = PreferenceHelper.getString(PreferenceKeys.NEUTRAL_REGION_ACTIVE, "")
            if (active.isNotEmpty() &&
                PreferenceHelper.getString(PreferenceKeys.NEUTRAL_REGION_CYCLE, "") == cycle
            ) {
                return active
            }

            val next = pickNeutralRegion(active)
            PreferenceHelper.settings.edit {
                putString(PreferenceKeys.NEUTRAL_REGION_ACTIVE, next)
                putString(PreferenceKeys.NEUTRAL_REGION_CYCLE, cycle)
            }
            return next
        }
    }

    /**
     * Drops the neutral region so that the next read picks a brand new one. Used by the
     * manual "reset identifiers" action.
     */
    fun resetNeutralRegion() {
        PreferenceHelper.settings.edit {
            remove(PreferenceKeys.NEUTRAL_REGION_ACTIVE)
            remove(PreferenceKeys.NEUTRAL_REGION_CYCLE)
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
            remove(PreferenceKeys.SB_USER_ID_CYCLE)
        }
    }

    /**
     * Rotates every client side identifier at once (SponsorBlock id + neutral region).
     * This is the manual rotation action: it is also what drives the "manual only"
     * frequency, where nothing rotates until the user asks for it.
     */
    fun rotateAllIdentifiers() {
        resetSponsorBlockUserId()
        resetNeutralRegion()
    }

    /**
     * Clears everything a WebView may have persisted about the user (cookies and web
     * storage). Called from the manual cache cleanup.
     */
    fun clearWebViewData() {
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }

    private fun pickNeutralRegion(previous: String): String {
        // both branches must be lists: NEUTRAL_REGIONS is an Array
        val pool = NEUTRAL_REGIONS.filter { it != previous }
            .ifEmpty { NEUTRAL_REGIONS.toList() }
        return pool[secureRandom.nextInt(pool.size)]
    }
}
