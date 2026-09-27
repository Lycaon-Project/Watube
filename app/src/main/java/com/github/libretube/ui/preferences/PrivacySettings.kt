package com.github.libretube.ui.preferences

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.github.libretube.R
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.helpers.PrivacyHelper
import com.github.libretube.ui.base.BasePreferenceFragment
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
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.RESET_SB_UUID_DAILY)?.apply {
            isChecked = PreferenceHelper.getBoolean(PreferenceKeys.RESET_SB_UUID_DAILY, true)
        }

        val idInfo = findPreference<Preference>("sb_user_id_info")
        idInfo?.summary = getString(R.string.sb_user_id_info) + "\n" +
            PrivacyHelper.getValidSponsorBlockUserId()?.take(6)?.plus("…")
            ?: getString(R.string.sponsorblock_id_rotated)

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
     * Purges every client side identifier that could be used to correlate requests:
     * SponsorBlock user id (a fresh random one will be generated on next submission),
     * WebView cookies/local storage. Downloads, history and settings are preserved.
     */
    private fun resetIdentifiers() {
        PrivacyHelper.resetSponsorBlockUserId()
        PrivacyHelper.clearWebViewData()
        findPreference<Preference>("sb_user_id_info")?.summary =
            getString(R.string.sb_user_id_info) + "\n" + getString(R.string.sponsorblock_id_rotated)
    }
}
