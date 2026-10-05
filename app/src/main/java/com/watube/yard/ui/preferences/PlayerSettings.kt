package com.watube.yard.ui.preferences

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.LocaleHelper
import com.watube.yard.helpers.PlayerHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.RequireRestartDialog
import com.watube.yard.ui.sheets.SleepTimerSheet
import com.watube.yard.ui.tools.RestMode
import com.watube.yard.ui.tools.SleepTimer
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
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
        setupRestModeSchedule()

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

    /** Days of the week (Monday first) and start / end hours of the scheduled rest mode. */
    private fun setupRestModeSchedule() {
        val locale = resources.configuration.locales[0]
        findPreference<MultiSelectListPreference>(PreferenceKeys.REST_MODE_DAYS)?.apply {
            entries = DayOfWeek.entries.map { day ->
                day.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
            }.toTypedArray()
            entryValues = DayOfWeek.entries.map { it.value.toString() }.toTypedArray()
        }
        // the listener runs before the value is stored: reschedule right after it
        val onChange = Preference.OnPreferenceChangeListener { _, _ ->
            listView.post { onRestModeChanged() }
            true
        }
        findPreference<Preference>(PreferenceKeys.REST_MODE_SCHEDULE)?.onPreferenceChangeListener = onChange
        findPreference<Preference>(PreferenceKeys.REST_MODE_DAYS)?.onPreferenceChangeListener = onChange
        setupRestModeTime(PreferenceKeys.REST_MODE_START, { RestMode.start }) { RestMode.start = it }
        setupRestModeTime(PreferenceKeys.REST_MODE_END, { RestMode.end }) { RestMode.end = it }
        updateRestModeSummaries()
    }

    private fun setupRestModeTime(key: String, read: () -> LocalTime, write: (LocalTime) -> Unit) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            val time = read()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                write(LocalTime.of(hour, minute))
                onRestModeChanged()
            }, time.hour, time.minute, DateFormat.is24HourFormat(requireContext())).show()
            true
        }
    }

    private fun onRestModeChanged() {
        RestMode.schedule()
        updateRestModeSummaries()
    }

    private fun updateRestModeSummaries() {
        val locale = resources.configuration.locales[0]
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        findPreference<Preference>(PreferenceKeys.REST_MODE_START)?.summary = timeFormat.format(RestMode.start)
        findPreference<Preference>(PreferenceKeys.REST_MODE_END)?.summary = timeFormat.format(RestMode.end)
        findPreference<Preference>(PreferenceKeys.REST_MODE_DAYS)?.summary = RestMode.days.sorted()
            .joinToString { it.getDisplayName(TextStyle.SHORT, locale) }
            .ifEmpty { getString(R.string.rest_mode_no_days) }
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