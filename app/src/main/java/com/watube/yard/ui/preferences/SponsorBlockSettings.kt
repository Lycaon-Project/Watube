package com.watube.yard.ui.preferences

import android.os.Bundle
import androidx.preference.EditTextPreference
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BasePreferenceFragment

class SponsorBlockSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sponsorblock_settings, rootKey)

        // The user may enter their own SponsorBlock id (for instance to share submissions
        // across devices). It is stored under the same key PrivacyHelper reads, but the
        // change has to go through PrivacyHelper as well: it stamps the id with the
        // current rotation cycle, otherwise the hand written value would be considered
        // stale and silently replaced by a random one on the very next submission.
        findPreference<EditTextPreference>(PreferenceKeys.SB_USER_ID)
            ?.setOnPreferenceChangeListener { _, newValue ->
                PrivacyHelper.storeSponsorBlockUserId(newValue?.toString().orEmpty())
                true
            }
    }
}
