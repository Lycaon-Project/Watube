package com.watube.yard.ui.preferences

import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingPeriodicWorkPolicy
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.extensions.formatAsFileSize
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NotificationHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.ErrorDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Données & maintenance" category of the settings hub: data saver / downloads /
 * notifications together with the maintenance actions (backup, cache, update check,
 * crash log) and the settings reset.
 */
class MaintenanceSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_settings, rootKey)
        addPreferencesFromResource(R.xml.notification_settings)
        addPreferencesFromResource(R.xml.maintenance_settings)

        setupReset()
        setupNotifications()
        setupBackup()
        setupClearCache()
        setupCrashLog()
    }

    private fun setupReset() {
        findPreference<Preference>(PreferenceKeys.RESET_SETTINGS)?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset)
                .setMessage(R.string.reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset) { _, _ ->
                    PreferenceHelper.clearPreferences()
                    PreferenceHelper.setToken("")
                    ActivityCompat.recreate(requireActivity())
                }
                .show()
            true
        }
    }

    /**
     * Notification scheduling: re-enqueues the background worker whenever the throttle
     * or the network requirement changes.
     */
    private fun setupNotifications() {
        val notificationsEnabled =
            findPreference<SwitchPreferenceCompat>(PreferenceKeys.NOTIFICATION_ENABLED)
        val checkingFrequency = findPreference<ListPreference>(PreferenceKeys.CHECKING_FREQUENCY)
        val requiredNetwork = findPreference<ListPreference>(PreferenceKeys.REQUIRED_NETWORK)

        val listener = Preference.OnPreferenceChangeListener { _, _ ->
            NotificationHelper.enqueueWork(
                context = requireContext(),
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
            )
            true
        }

        notificationsEnabled?.setOnPreferenceChangeListener(listener)
        checkingFrequency?.setOnPreferenceChangeListener(listener)
        requiredNetwork?.setOnPreferenceChangeListener(listener)
    }

    private fun setupBackup() {
        findPreference<Preference>("backup_restore")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_backupRestoreSettings)
            true
        }
    }

    /**
     * Lets the user manually free the storage used by images, http responses and temp files.
     */
    private fun setupClearCache() {
        val clearCache = findPreference<Preference>("clear_cache") ?: return
        refreshCacheSummary(clearCache)

        clearCache.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_cache_dialog_title)
                .setMessage(R.string.clear_cache_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_cache) { _, _ ->
                    val appContext = requireContext().applicationContext
                    lifecycleScope.launch {
                        ImageHelper.clearCache(appContext)
                        // Watube: also drop cookies / web storage a WebView may have persisted
                        PrivacyHelper.clearWebViewData()
                        refreshCacheSummary(clearCache)
                        appContext.toastFromMainDispatcher(R.string.cache_cleared)
                    }
                }
                .show()

            true
        }
    }

    private fun refreshCacheSummary(preference: Preference) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            // walking the cache directory is I/O: keep it off the main thread
            val size = withContext(Dispatchers.IO) {
                ImageHelper.getCacheSize(appContext)
            }
            preference.summary = getString(R.string.clear_cache_summary, size.formatAsFileSize())
        }
    }

    private fun setupCrashLog() {
        val crashlog = findPreference<Preference>("crashlog")
        crashlog?.isVisible = PreferenceHelper.getErrorLog().isNotEmpty() && BuildConfig.DEBUG
        crashlog?.setOnPreferenceClickListener {
            ErrorDialog().show(childFragmentManager, null)
            crashlog.isVisible = false
            true
        }
    }
}
