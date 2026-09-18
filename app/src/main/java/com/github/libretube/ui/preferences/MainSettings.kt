package com.github.libretube.ui.preferences

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.github.libretube.BuildConfig
import com.github.libretube.R
import com.github.libretube.extensions.formatAsFileSize
import com.github.libretube.extensions.toastFromMainDispatcher
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.base.BasePreferenceFragment
import com.github.libretube.ui.dialogs.ErrorDialog
import com.github.libretube.util.UpdateChecker
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
