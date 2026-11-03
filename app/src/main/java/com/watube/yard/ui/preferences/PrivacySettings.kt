package com.watube.yard.ui.preferences

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.watube.yard.R
import com.watube.yard.api.RetrofitInstance
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.db.DatabaseHolder.Database
import com.watube.yard.db.DatabaseHelper
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.AppLock
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.LocaleHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.helpers.ScreenPrivacy
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import com.watube.yard.util.SharedHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Watube privacy hub: exposes every anti fingerprinting measure that is active in the
 * app, so the user can understand and control them without reading the source code.
 *
 * It is also the "Confidentialité" category of the settings hub: the instance/Piped
 * operation mode (inherited from [InstanceSettings]) and the local history controls are
 * merged into the same screen.
 */
class PrivacySettings : InstanceSettings() {
    // region Privacy
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.privacy_settings, rootKey)

        addPreferencesFromResource(R.xml.instance_settings)
        addPreferencesFromResource(R.xml.history_settings)

        bindInstancePreferences()
        bindHistoryPreferences()

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.PRIVACY_HARDENING)?.apply {
            isChecked = PrivacyHelper.isHardeningEnabled()
        }

        setupRotationFrequency()
        setupTouristMode()

        // takes effect at once on this screen; the others pick it up when they come back
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.BLOCK_SCREENSHOTS)
            ?.setOnPreferenceChangeListener { _, _ ->
                view?.post { activity?.window?.let(ScreenPrivacy::applyTo) }
                true
            }
        refreshIdentifierSummary()

        // the lock needs something to ask for: a fingerprint, a face or a screen lock code
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.APP_LOCK)
            ?.setOnPreferenceChangeListener { _, enable ->
                if (enable != true) return@setOnPreferenceChangeListener true
                if (!AppLock.canLock(requireContext())) {
                    Toast.makeText(context, R.string.app_lock_unavailable, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                AppLock.onEnabled()
                true
            }

        findPreference<Preference>(CLEAR_EVERYTHING)?.setOnPreferenceClickListener {
            SecureAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_everything)
                .setMessage(R.string.clear_everything_dialog)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_everything_confirm) { _, _ -> clearEverything() }
                .show()
            true
        }

        findPreference<Preference>("reset_identifiers")?.setOnPreferenceClickListener {
            SecureAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_identifiers)
                .setMessage(R.string.reset_identifiers_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.dialog_reset) { _, _ -> resetIdentifiers() }
                .show()
            true
        }
    }

    /**
     * "Clear everything now": the watch and search history, the playback positions, the caches,
     * the account session and the identifiers sent to third parties, in one go. What the user
     * built on purpose (subscriptions, playlists, downloads, settings) stays.
     */
    private fun clearEverything() {
        val context = requireContext().applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            DatabaseHelper.clearBrowsingData()
            ImageHelper.clearCache(context)
            PreferenceHelper.setToken("")
            PrivacyHelper.resetSponsorBlockUserId()
            PrivacyHelper.clearWebViewData()
            SharedHttpClient.dropIdleConnections()
            context.toastFromMainDispatcher(R.string.clear_everything_done)
        }
    }

    /**
     * Single frequency shared by every rotating identifier (SponsorBlock id and neutral
     * region), so both always rotate at the exact same moment.
     */
    private fun setupRotationFrequency() {
        // the neutral country is picked by hand only while nothing rotates on its own
        val country = findPreference<ListPreference>(PreferenceKeys.NEUTRAL_REGION_COUNTRY)
        country?.apply {
            val countries = LocaleHelper.getAvailableCountries()
            entries = countries.map { it.name }.toTypedArray()
            entryValues = countries.map { it.code }.toTypedArray()
            isVisible = PrivacyHelper.rotationFrequency() == PrivacyHelper.RotationFrequency.MANUAL
            summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                getString(R.string.privacy_neutral_region_country_summary, preference.entry)
            }
        }
        findPreference<ListPreference>(PreferenceKeys.PRIVACY_ROTATION_FREQUENCY)?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                val entry = preference.entry ?: return@SummaryProvider null
                getString(R.string.rotation_frequency_summary, entry)
            }
            setOnPreferenceChangeListener { _, newValue ->
                country?.isVisible = newValue == PrivacyHelper.RotationFrequency.MANUAL.value
                true
            }
        }
    }

    /**
     * Tourist mode keeps no browsing trace on the device, so turning it on also offers to
     * wipe what is already stored. The preference is only persisted once the deletion has
     * been accepted, otherwise the switch would promise an empty device while keeping data.
     */
    private fun setupTouristMode() {
        val touristMode = findPreference<SwitchPreferenceCompat>(PreferenceKeys.TOURIST_MODE)
            ?: return

        touristMode.setOnPreferenceChangeListener { _, newValue ->
            if (newValue != true) return@setOnPreferenceChangeListener true

            SecureAlertDialogBuilder(requireContext())
                .setTitle(R.string.tourist_mode)
                .setMessage(R.string.tourist_mode_clear_dialog)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay) { _, _ ->
                    // setChecked() only persists and re-renders the row, it does not call
                    // the change listener again, so the dialog cannot pop up twice
                    touristMode.isChecked = true
                    CoroutineScope(Dispatchers.IO).launch {
                        DatabaseHelper.clearBrowsingData()
                    }
                }
                .show()
            false
        }
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
     * SponsorBlock user id (a fresh random one is generated on next use), WebView
     * cookies/local storage. Downloads, history and
     * settings are preserved. This is also the manual rotation action for users who
     * selected the "manual only" frequency.
     */
    private fun resetIdentifiers() {
        PrivacyHelper.resetSponsorBlockUserId()
        PrivacyHelper.clearWebViewData()
        // a clean break, like Tor Browser's "new identity": no open connection and no cached
        // response (ETag / Last-Modified revalidation) carries over to the new identifiers
        lifecycleScope.launch(Dispatchers.IO) {
            SharedHttpClient.dropIdleConnections()
            RetrofitInstance.clearHttpCache()
        }

        refreshIdentifierSummary(getString(R.string.sponsorblock_id_rotated))
    }
    // endregion

    // region Watch History
    /**
     * Local history controls (search history, watch history, positions, bookmarks) of
     * R.xml.history_settings, merged into this hub.
     */
    private fun bindHistoryPreferences() {
        // search history is not recorded anymore once the toggle is off, so the entries
        // already stored have no reason to stay on the device
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.SEARCH_HISTORY_TOGGLE)
            ?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == false) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        Database.searchHistoryDao().deleteAll()
                    }
                }
                true
            }

        findPreference<Preference>(PreferenceKeys.CLEAR_SEARCH_HISTORY)
            ?.setOnPreferenceClickListener {
                showClearDialog(R.string.clear_history) {
                    Database.searchHistoryDao().deleteAll()
                }
                true
            }

        findPreference<Preference>(PreferenceKeys.CLEAR_WATCH_HISTORY)
            ?.setOnPreferenceClickListener {
                showClearDialog(R.string.clear_history) {
                    Database.watchHistoryDao().deleteAll()
                }
                true
            }

        findPreference<Preference>(PreferenceKeys.CLEAR_WATCH_POSITIONS)
            ?.setOnPreferenceClickListener {
                showClearDialog(R.string.reset_watch_positions) {
                    DatabaseHelper.clearWatchPositions()
                }
                true
            }

        findPreference<Preference>(PreferenceKeys.CLEAR_BOOKMARKS)
            ?.setOnPreferenceClickListener {
                showClearDialog(R.string.clear_bookmarks) {
                    Database.playlistBookmarkDao().deleteAll()
                }
                true
            }
    }

    private fun showClearDialog(title: Int, actionOnConfirm: suspend () -> Unit) {
        SecureAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(R.string.irreversible)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.okay) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    actionOnConfirm.invoke()
                }
            }
            .show()
    }
    // endregion

    private companion object {
        const val CLEAR_EVERYTHING = "clear_everything"
    }
}
