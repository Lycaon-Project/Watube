package com.watube.yard.ui.preferences

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.extensions.formatAsFileSize
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.ErrorDialog
import com.watube.yard.util.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainSettings : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        val update = findPreference<Preference>("update")
        update?.summary = "v${BuildConfig.VERSION_NAME}"

        // check app update manually
        update?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                update.summary = getString(R.string.checking_for_updates)
                withContext(Dispatchers.IO) {
                    UpdateChecker(requireContext()).checkUpdate(true)
                }
                update.summary = "v${BuildConfig.VERSION_NAME}"
            }

            true
        }

        setupClearCache()

        val crashlog = findPreference<Preference>("crashlog")
        crashlog?.isVisible = PreferenceHelper.getErrorLog().isNotEmpty() && BuildConfig.DEBUG
        crashlog?.setOnPreferenceClickListener {
            ErrorDialog().show(childFragmentManager, null)
            crashlog.isVisible = false
            true
        }
        
        listOf(
            "general" to R.id.action_global_generalSettings,
            "instance" to R.id.action_global_instanceSettings,
            "appearance" to R.id.action_global_appearanceSettings,
            "privacy" to R.id.action_global_privacySettings,
            "sponsorblock" to R.id.action_global_sponsorBlockSettings,
            "player" to R.id.action_global_playerSettings,
            "audio_video" to R.id.action_global_audioVideoSettings,
            "history" to R.id.action_global_historySettings,
            "notifications" to R.id.action_global_notificationSettings,
            "backup_restore" to R.id.action_global_backupRestoreSettings
        ).forEach { (preferenceKey, actionId) ->
            findPreference<Preference>(preferenceKey)?.setOnPreferenceClickListener { _ ->
                findNavController().navigate(actionId)
                true
            }
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
                        // Watube: also drop cookies/web storage persisted by the PoToken WebView
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
            val size = ImageHelper.getCacheSize(appContext)
            preference.summary = getString(R.string.clear_cache_summary, size.formatAsFileSize())
        }
    }
}
