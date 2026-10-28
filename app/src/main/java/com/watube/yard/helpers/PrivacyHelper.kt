package com.watube.yard.helpers

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.edit
import com.watube.yard.constants.PreferenceKeys
import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// region Privacy
private const val HOUR_MILLIS = 60L * 60 * 1000

/**
 * Central place for all Watube anti fingerprinting decisions.
 *
 * The master switch ([isHardeningEnabled]) is enabled by default and controls every
 * identifier that could be used to link requests coming from this device together:
 *  - SIM / carrier based country detection (see [LocaleHelper.getDetectedCountry])
 *  - the SponsorBlock user id, rotated on the shared [RotationFrequency] schedule
 *  - the neutral region declared for trending (see [NEUTRAL_REGION])
 *  - the reported User-Agent of third party services (SponsorBlock, DeArrow), which is
 *    normalized to a single generic value instead of leaking package name and version
 *  - WebView level information (see [applyWebViewAntiFingerprinting])
 *
 * All measures are purely client side and don't require any additional permission,
 * so they can never break existing functionality.
 */
object PrivacyHelper {
    /**
     * Chrome 152 reached the stable channel on that day, and since then a major version ships
     * every 2 weeks (chromiumdash.appspot.com milestone schedule). Refresh with app updates: the
     * formula only bridges the time between two releases of Watube.
     */
    private val CHROME_ANCHOR_DATE = LocalDate.of(2026, 8, 25)
    private const val CHROME_ANCHOR_MAJOR = 152
    private const val CHROME_RELEASE_DAYS = 14L

    /** A wrong device clock must not produce an absurd version. */
    private const val CHROME_MAX_DRIFT_DAYS = 10L * 365

    /**
     * Generic browser-like user agent used for all third party API calls when hardening
     * is enabled. It intentionally contains no device, app or version information, so all
     * hardened Watube users look the same to external observers (Tor/Mullvad style
     * "blend into the crowd" strategy).
     *
     * The Chrome version follows the stable channel, one release behind (staged rollouts keep
     * most users there): a version frozen in the code (131) became, within months, a rare value
     * that singled Watube users out instead of hiding them in the crowd.
     */
    val GENERIC_USER_AGENT: String by lazy {
        val days = ChronoUnit.DAYS.between(CHROME_ANCHOR_DATE, LocalDate.now())
            .coerceIn(0, CHROME_MAX_DRIFT_DAYS)
        val major = CHROME_ANCHOR_MAJOR + days / CHROME_RELEASE_DAYS - 1
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)" +
            " Chrome/$major.0.0.0 Safari/537.36"
    }

    /**
     * How often the SponsorBlock user id is regenerated.
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
     * Country declared for trending while the region is automatic. It is the one NewPipe
     * Extractor declares in every other request (with en-GB and UTC+0), so trending says no
     * more than the rest of the traffic and every user sends the same value. Countries drawn
     * at random per user used to single each user out among the others instead.
     */
    const val NEUTRAL_REGION = "GB"

    private const val USER_ID_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val MANUAL_CYCLE_STAMP = "manual"
    private val secureRandom = SecureRandom()

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
        // kept encrypted (see PreferenceHelper.storedForm); when it cannot be, no id is kept
        // and a fresh one is generated for the next submission
        val stored = PreferenceHelper.storedForm(PreferenceKeys.SB_USER_ID, userId)
        PreferenceHelper.settings.edit {
            if (stored == null) {
                remove(PreferenceKeys.SB_USER_ID)
            } else {
                putString(PreferenceKeys.SB_USER_ID, stored)
            }
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
     * Neutral region (on by default, as hardening is opt-out): trending is requested for
     * [NEUTRAL_REGION] instead of the device country. It can be turned off in the privacy
     * settings, as it changes the trending videos shown.
     */
    fun isNeutralRegionEnabled(): Boolean {
        return isHardeningEnabled() &&
            PreferenceHelper.getBoolean(PreferenceKeys.PRIVACY_NEUTRAL_REGION, true)
    }

    /**
     * The neutral country: [NEUTRAL_REGION], or the one the user picked while the rotation is
     * "manual only" (any other country sets them apart from the other users).
     */
    fun neutralRegion(): String =
        if (rotationFrequency() == RotationFrequency.MANUAL) {
            PreferenceHelper.getString(PreferenceKeys.NEUTRAL_REGION_COUNTRY, NEUTRAL_REGION)
        } else {
            NEUTRAL_REGION
        }

    /**
     * Applies Mullvad-browser/Tor inspired WebView hardening where the public Android
     * APIs allow it:
     *  - disables the DOM cache, a classic persistent fingerprinting vector
     *  - disables web storage (localStorage): nothing persists from one visit to the next, the
     *    local pages fall back to the system theme and language. It used to stay on for the
     *    BotGuard / PoToken challenge, which is gone (see SabrAttestationException).
     *  - blocks third party cookies for the embedded view
     */
    fun applyWebViewAntiFingerprinting(webView: WebView) {
        if (!isHardeningEnabled()) return

        runCatching {
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webView.settings.javaScriptCanOpenWindowsAutomatically = false
            webView.settings.domStorageEnabled = false
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
     * Clears everything a WebView may have persisted about the user (cookies and web
     * storage). Called from the manual cache cleanup.
     */
    fun clearWebViewData() {
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }
}
// endregion
