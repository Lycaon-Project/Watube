package com.watube.yard.ui.preferences

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BasePreferenceFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Watube privacy hub: exposes every anti fingerprinting measure that is active in the
 * app, so the user can understand and control them without reading the source code.
 */
class PrivacySettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.privacy_settings, rootKey)

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.PRIVACY_HARDENING)?.apply {
            isChecked = PrivacyHelper.isHardeningEnabled()
        }

        setupRotationFrequency()
        setupNeutralRegion()
        refreshIdentifierSummary()

        findPreference<Preference>("reset_identifiers")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_identifiers)
                .setMessage(R.string.reset_identifiers_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.dialog_reset) { _, _ -> resetIdentifiers() }
                .show()
            true
        }
    }

    /**
     * Single frequency shared by every rotating identifier (SponsorBlock id and neutral
     * region), so both always rotate at the exact same moment.
     */
    private fun setupRotationFrequency() {
        findPreference<ListPreference>(PreferenceKeys.PRIVACY_ROTATION_FREQUENCY)?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                val entry = preference.entry ?: return@SummaryProvider null
                getString(R.string.rotation_frequency_summary, entry)
            }
        }
    }

    /**
     * Neutral region switch: the summary keeps showing the explanation and appends the
     * country currently in use, so the rotation is visible instead of surprising.
     *
     * The text is assigned explicitly (instead of a SummaryProvider) because the row has
     * to be re-rendered after a rotation, and [Preference.notifyChanged] is protected.
     */
    private fun setupNeutralRegion() {
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.PRIVACY_NEUTRAL_REGION)?.apply {
            isChecked = PrivacyHelper.isNeutralRegionEnabled()
            summary = neutralRegionSummary(isChecked)
            setOnPreferenceChangeListener { preference, newValue ->
                val enabled = newValue == true
                // starting a new neutral region session always picks a fresh country
                if (enabled) PrivacyHelper.resetNeutralRegion()
                // written for the incoming state: the row is re-rendered right after the
                // value is persisted, so it shows the country that is now active.
                // hardening off means the region is not applied at all, so no country there
                preference.summary =
                    neutralRegionSummary(enabled && PrivacyHelper.isHardeningEnabled())
                true
            }
        }
    }

    private fun neutralRegionSummary(enabled: Boolean): CharSequence {
        val base = getString(R.string.privacy_neutral_region_summary)
        if (!enabled) return base
        return base + "\n" + getString(
            R.string.privacy_neutral_region_current,
            PrivacyHelper.getNeutralRegion()
        )
    }

    /**
     * Shows the SponsorBlock id currently in use (truncated) or the explanation when no
     * id exists yet (a fresh random one is only generated on the first submission).
     */
    private fun refreshIdentifierSummary(notRotatedHint: String? = null) {
        val idInfo = findPreference<Preference>("sb_user_id_info") ?: return
        val info = getString(R.string.sb_user_id_info)
        val currentId = PrivacyHelper.getValidSponsorBlockUserId()?.take(6)?.plus("…")

        idInfo.summary = when {
            currentId != null -> info + "\n" + currentId
            notRotatedHint != null -> info + "\n" + notRotatedHint
            else -> info
        }
    }

    /**
     * Purges every client side identifier that could be used to correlate requests:
     * SponsorBlock user id and neutral region (a fresh random one of each will be
     * generated on next use), WebView cookies/local storage. Downloads, history and
     * settings are preserved. This is also the manual rotation action for users who
     * selected the "manual only" frequency.
     */
    private fun resetIdentifiers() {
        PrivacyHelper.rotateAllIdentifiers()
        PrivacyHelper.clearWebViewData()

        refreshIdentifierSummary(getString(R.string.sponsorblock_id_rotated))

        // the country changed, so the new text differs and re-assigning it re-renders
        // the row (Preference.notifyChanged() is protected and not usable from here)
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.PRIVACY_NEUTRAL_REGION)?.let {
            it.summary = neutralRegionSummary(it.isChecked)
        }
    }
}
