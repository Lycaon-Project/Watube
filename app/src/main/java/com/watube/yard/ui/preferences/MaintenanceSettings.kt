package com.watube.yard.ui.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.core.app.ActivityCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.work.ExistingPeriodicWorkPolicy
import com.watube.yard.BuildConfig
import com.watube.yard.R
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.DialogDownloadFolderBinding
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.formatAsFileSize
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.helpers.DownloadDestination
import com.watube.yard.helpers.ImageHelper
import com.watube.yard.helpers.NotificationHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.helpers.PrivacyHelper
import com.watube.yard.helpers.ThemeHelper
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.ErrorDialog
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Données & maintenance" category of the settings hub: data saver / downloads /
 * notifications together with the maintenance actions (backup, cache, update check,
 * crash log) and the settings reset.
 */
/** Size of the detail line of a choice, against its name (destination folder dialog). */
private const val CHOICE_DETAIL_SCALE = 0.85f

class MaintenanceSettings : BasePreferenceFragment() {
    // region Downloads
    private val pickFolder = registerForActivityResult(OpenDocumentTree()) { onFolderPicked(it) }
    // endregion

    // region Settings
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_settings, rootKey)
        addPreferencesFromResource(R.xml.notification_settings)
        addPreferencesFromResource(R.xml.maintenance_settings)

        setupReset()
        setupDownloadFolder()
        setupNotifications()
        setupBackup()
        setupClearCache()
        setupCrashLog()
    }

    private fun setupReset() {
        findPreference<Preference>(PreferenceKeys.RESET_SETTINGS)?.setOnPreferenceClickListener {
            SecureAlertDialogBuilder(requireContext())
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
     * Lets the user manually free the storage used by images, http responses and temp files.
     */
    private fun setupClearCache() {
        val clearCache = findPreference<Preference>("clear_cache") ?: return
        refreshCacheSummary(clearCache)

        clearCache.setOnPreferenceClickListener {
            SecureAlertDialogBuilder(requireContext())
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
    // endregion

    // region Downloads
    /**
     * Destination folder: Watube's own (Download/Watube) or one the user picks, kept through
     * a lasting access; and Save readable, which drops the encryption of new downloads, so
     * it is only switched on once its consequence was read.
     */
    private fun setupDownloadFolder() {
        val folder = findPreference<Preference>(PreferenceKeys.DOWNLOAD_FOLDER) ?: return
        refreshFolderSummary(folder)
        folder.setOnPreferenceClickListener {
            showFolderChoice()
            true
        }

        val readable = findPreference<SwitchPreferenceCompat>(PreferenceKeys.DOWNLOAD_READABLE)
        readable?.setOnPreferenceChangeListener { _, enabled ->
            if (enabled != true) return@setOnPreferenceChangeListener true
            SecureAlertDialogBuilder(requireContext())
                .setTitle(R.string.download_readable)
                .setMessage(R.string.download_readable_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay) { _, _ -> readable.isChecked = true }
                .show()
            false
        }
    }

    /**
     * What the folder is for, where the downloads go now, and the folders to choose from:
     * buttons drawn like the dialog's Cancel, each with its name over where it is.
     */
    private fun showFolderChoice() {
        val context = requireContext()
        val binding = DialogDownloadFolderBinding.inflate(layoutInflater)
        binding.downloadFolderCurrent.text =
            getString(R.string.download_folder_current, DownloadDestination.describe(context))
        binding.downloadFolderDefault.text = choiceLabel(
            getString(R.string.download_folder_default),
            DownloadDestination.defaultLabel()
        )
        binding.downloadFolderPick.text = choiceLabel(
            getString(R.string.download_folder_pick),
            getString(R.string.download_folder_pick_hint)
        )
        val dialog = SecureAlertDialogBuilder(context)
            .setTitle(R.string.download_folder)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .show()
        binding.downloadFolderDefault.setOnClickListener {
            dialog.dismiss()
            setFolder(null)
        }
        binding.downloadFolderPick.setOnClickListener {
            dialog.dismiss()
            runCatching { pickFolder.launch(DownloadDestination.pickedFolder()) }
                .onFailure { Log.e(TAG(), "No folder picker", it) }
        }
    }

    /** A choice's name, and under it, smaller and quieter, where it is. */
    private fun choiceLabel(name: String, detail: String): CharSequence {
        val quiet = ThemeHelper.getThemeColor(
            requireContext(),
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        return buildSpannedString {
            append(name)
            append('\n')
            inSpans(RelativeSizeSpan(CHOICE_DETAIL_SCALE), ForegroundColorSpan(quiet)) { append(detail) }
        }
    }

    private fun onFolderPicked(uri: Uri?) {
        uri ?: return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val kept = runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure { Log.e(TAG(), "No lasting access to the picked folder", it) }.isSuccess
        if (kept) {
            setFolder(uri)
        } else {
            Toast.makeText(requireContext(), R.string.download_folder_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Remembers [uri] (null: Watube's own folder) and gives back the access to the former one. */
    private fun setFolder(uri: Uri?) {
        val former = DownloadDestination.pickedFolder()
        PreferenceHelper.putString(PreferenceKeys.DOWNLOAD_FOLDER, uri?.toString().orEmpty())
        if (former != null && former != uri) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { requireContext().contentResolver.releasePersistableUriPermission(former, flags) }
        }
        findPreference<Preference>(PreferenceKeys.DOWNLOAD_FOLDER)?.let(::refreshFolderSummary)
    }

    private fun refreshFolderSummary(folder: Preference) {
        val context = requireContext()
        folder.summary = if (DownloadDestination.isPickedFolderReachable(context)) {
            getString(R.string.download_folder_summary, DownloadDestination.describe(context))
        } else {
            getString(R.string.download_folder_unavailable)
        }
    }
    // endregion

    // region New Video Notifications
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
    // endregion

    // region Backup & Restore
    private fun setupBackup() {
        findPreference<Preference>("backup_restore")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_backupRestoreSettings)
            true
        }
    }
    // endregion

    // region Crash Reports
    private fun setupCrashLog() {
        val crashlog = findPreference<Preference>("crashlog") ?: return
        // crash traces only exist in debug builds: release drops this code (and the dialog)
        crashlog.isVisible = BuildConfig.DEBUG && PreferenceHelper.getErrorLog().isNotEmpty()
        if (!BuildConfig.DEBUG) return
        crashlog.setOnPreferenceClickListener {
            ErrorDialog().show(childFragmentManager, null)
            crashlog.isVisible = false
            true
        }
    }
    // endregion
}
