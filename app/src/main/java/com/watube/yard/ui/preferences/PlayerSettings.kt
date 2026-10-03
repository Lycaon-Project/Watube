package com.watube.yard.ui.preferences

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.LocaleHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.RequireRestartDialog
import com.watube.yard.ui.sheets.SleepTimerSheet
import com.watube.yard.ui.tools.SleepTimer
import kotlin.math.ceil

class PlayerSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.player_settings, rootKey)

        // "Lecteur" category of the settings hub: default quality/network plus the
        // SponsorBlock options live on the very same screen.
        addPreferencesFromResource(R.xml.audio_video_settings)
        addPreferencesFromResource(R.xml.sponsorblock_settings)

        val defaultSubtitle = findPreference<ListPreference>(PreferenceKeys.DEFAULT_SUBTITLE)
        defaultSubtitle?.let { setupSubtitlePref(it) }

        val captionSettings = findPreference<Preference>(PreferenceKeys.CAPTION_SETTINGS)
        captionSettings?.setOnPreferenceClickListener {
            try {
                val captionSettingsIntent = Intent(Settings.ACTION_CAPTIONING_SETTINGS)
                startActivity(captionSettingsIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show()
            }
            true
        }

        // the sleep timer lives here instead of the player options: started now from the sheet,
        // or for every video with the last chosen duration
        findPreference<Preference>(PreferenceKeys.SLEEP_TIMER)?.setOnPreferenceClickListener {
            SleepTimerSheet().show(childFragmentManager)
            true
        }
        childFragmentManager.setFragmentResultListener(SleepTimerSheet.SLEEP_TIMER_REQUEST_KEY, this) { _, _ ->
            updateSleepTimerSummaries()
        }

        findPreference<ListPreference>(PreferenceKeys.ORIENTATION)
            ?.setOnPreferenceChangeListener { _, _ ->
                RequireRestartDialog().show(childFragmentManager, RequireRestartDialog::class.java.name)
                true
            }

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

    override fun onResume() {
        super.onResume()
        updateSleepTimerSummaries()
    }

    private fun updateSleepTimerSummaries() {
        val minutesLeft = ceil(SleepTimer.timeLeftMillis.toDouble() / DateUtils.MINUTE_IN_MILLIS).toInt()
        findPreference<Preference>(PreferenceKeys.SLEEP_TIMER)?.summary =
            if (minutesLeft > 0) resources.getQuantityString(R.plurals.minutes_left, minutesLeft, minutesLeft)
            else getString(R.string.disabled)

        val minutes = PlayerHelper.sleepTimerMinutes.toInt()
        findPreference<Preference>(PreferenceKeys.SLEEP_TIMER_ALL_VIDEOS)?.summary = getString(
            R.string.sleep_timer_all_videos_summary,
            resources.getQuantityString(R.plurals.sleep_timer_chip_minutes, minutes, minutes)
        )
    }

    private fun setupSubtitlePref(preference: ListPreference) {
        val locales = LocaleHelper.getAvailableLocales()
        val localeNames = locales.map { it.name }
            .toMutableList()
        localeNames.add(0, requireContext().getString(R.string.none))

        val localeCodes = locales.map { it.code }
            .toMutableList()
        localeCodes.add(0, "")

        preference.entries = localeNames.toTypedArray()
        preference.entryValues = localeCodes.toTypedArray()
        preference.summaryProvider =
            Preference.SummaryProvider<ListPreference> {
                it.entry
            }
    }
}