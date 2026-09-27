package com.watube.yard.ui.preferences

import android.os.Bundle
import com.watube.yard.R
import com.watube.yard.ui.base.BasePreferenceFragment

class SponsorBlockSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sponsorblock_settings, rootKey)
    }
}
