package com.watube.yard.ui.preferences

import android.content.Context
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.watube.yard.R
import com.watube.yard.constants.IntentData
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.databinding.DialogImportExportFormatChooserBinding
import com.watube.yard.enums.ImportFormat
import com.watube.yard.extensions.toastFromMainDispatcher
import com.watube.yard.extensions.toastFromMainThread
import com.watube.yard.helpers.BackupCrypto
import com.watube.yard.helpers.BackupHelper
import com.watube.yard.helpers.ImportHelper
import com.watube.yard.helpers.PreferenceHelper
import com.watube.yard.obj.BackupFile
import com.watube.yard.ui.base.BasePreferenceFragment
import com.watube.yard.ui.dialogs.BackupDialog
import com.watube.yard.ui.dialogs.BackupProtectionDialogs
import com.watube.yard.ui.dialogs.BackupDialog.Companion.BACKUP_DIALOG_REQUEST_KEY
import com.watube.yard.ui.dialogs.RequireRestartDialog
import com.watube.yard.util.TextUtils
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// region Backup & Restore
class BackupRestoreSettings : BasePreferenceFragment() {
    private var backupFile = BackupFile()
    private var importFormat: ImportFormat = ImportFormat.NEWPIPE

    /** What will open the backup being exported, from the protection dialog to the file. */
    private var exportSecret: Pair<CharArray, BackupCrypto.SecretKind>? = null

    // backup and restore database
    private val getBackupFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            // bound to the fragment lifecycle so nothing runs (and no dialog is shown)
            // once the screen is gone
            val appContext = context?.applicationContext ?: return@registerForActivityResult
            lifecycleScope.launch {
                val file = runCatching { BackupHelper.readBackupFile(appContext, uri) }
                    .getOrElse { return@launch appContext.toastFromMainDispatcher(R.string.backup_unreadable) }
                if (!BackupCrypto.isEncrypted(file)) return@launch confirmUnencrypted(appContext, file)

                val kind = runCatching { BackupCrypto.secretKindOf(file) }
                    .getOrElse { return@launch appContext.toastFromMainDispatcher(R.string.backup_unreadable) }
                BackupProtectionDialogs.askUnlockSecret(
                    requireContext(),
                    kind,
                    lifecycleScope,
                    open = { secret ->
                        try {
                            BackupCrypto.decrypt(file, secret)
                        } catch (e: BackupCrypto.WrongSecretException) {
                            null
                        }
                    },
                    onOpened = { json -> lifecycleScope.launch { restore(appContext, json) } }
                )
            }
        }
    private val createBackupFile = registerForActivityResult(CreateDocument(FILETYPE_ANY)) { uri ->
        val appContext = context?.applicationContext ?: return@registerForActivityResult
        val pending = exportSecret
        exportSecret = null
        if (pending == null) {
            // the secret did not survive (the app was stopped while choosing the file): the
            // file the picker created stays empty, it is removed and the user told
            if (uri != null) {
                runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
                appContext.toastFromMainThread(R.string.backup_creation_failed)
            }
            return@registerForActivityResult
        }
        val (secret, kind) = pending
        if (uri == null) {
            secret.fill(Char.MIN_VALUE)
            return@registerForActivityResult
        }
        // started even if the screen is closed meanwhile: the secret is always cleared
        lifecycleScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            BackupHelper.createAdvancedBackup(appContext, uri, backupFile, secret, kind)
        }
    }

    /** A file of an older version is not protected: anyone could have read or changed it. */
    private fun confirmUnencrypted(appContext: Context, file: ByteArray) {
        SecureAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_unencrypted_title)
            .setMessage(R.string.backup_unencrypted_message)
            .setNegativeButton(R.string.cancel) { _, _ -> file.fill(0) }
            .setPositiveButton(R.string.restore) { _, _ ->
                lifecycleScope.launch { restore(appContext, file) }
            }
            .show()
    }

    private suspend fun restore(appContext: Context, json: ByteArray) {
        val restored = runCatching { BackupHelper.restoreAdvancedBackup(appContext, json) }
        json.fill(0)
        if (restored.isFailure) return appContext.toastFromMainDispatcher(R.string.backup_unreadable)
        // could fail if fragment is already closed
        runCatching { RequireRestartDialog().show(childFragmentManager, this::class.java.name) }
    }

    override fun onDestroy() {
        exportSecret?.first?.fill(Char.MIN_VALUE)
        exportSecret = null
        super.onDestroy()
    }

    /**
     * result listeners for importing and exporting subscriptions
     */
    private val getSubscriptionsFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val appContext = context?.applicationContext ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            ImportHelper.importSubscriptions(appContext, uri, importFormat)
        }
    }

    private val createSubscriptionsFile =
        registerForActivityResult(CreateDocument(FILETYPE_ANY)) { uri ->
            if (uri == null) return@registerForActivityResult
            val appContext = context?.applicationContext ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                ImportHelper.exportSubscriptions(appContext, uri, importFormat)
            }
        }


    // result listeners for importing and exporting playlists
    private val getPlaylistsFile =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
            val appContext = context?.applicationContext ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                for (file in files) {
                    ImportHelper.importPlaylists(appContext, file, importFormat)
                }
            }
        }

    private val getWatchHistoryFile =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
            val appContext = context?.applicationContext ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                for (file in files) {
                    ImportHelper.importWatchHistory(appContext, file, importFormat)
                }
            }
        }

    private val createPlaylistsFile =
        registerForActivityResult(CreateDocument(FILETYPE_ANY)) { uri ->
            val appContext = context?.applicationContext ?: return@registerForActivityResult
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    ImportHelper.exportPlaylists(appContext, uri, importFormat)
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.import_export_settings, rootKey)

        val importSubscriptions = findPreference<Preference>("import_subscriptions")
        importSubscriptions?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.import_subscriptions_from,
                importSubscriptionFormatList
            ) { format, _ ->
                importFormat = format
                getSubscriptionsFile.launch("*/*")
            }
            true
        }

        val exportSubscriptions = findPreference<Preference>("export_subscriptions")
        exportSubscriptions?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.export_subscriptions_to,
                exportSubscriptionFormatList,
                isExport = true
            ) { format, includeTimestamp ->
                importFormat = format
                createSubscriptionsFile.launch(
                    getExportFileName(requireContext(), format, "subscriptions", includeTimestamp)
                )
            }
            true
        }

        val importPlaylists = findPreference<Preference>("import_playlists")
        importPlaylists?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.import_playlists_from,
                importPlaylistFormatList
            ) { format, _ ->
                importFormat = format
                getPlaylistsFile.launch(arrayOf("*/*"))
            }
            true
        }

        val exportPlaylists = findPreference<Preference>("export_playlists")
        exportPlaylists?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.export_playlists_to,
                exportPlaylistFormatList,
                isExport = true
            ) { format, includeTimestamp ->
                importFormat = format
                createPlaylistsFile.launch(
                    getExportFileName(requireContext(), format, "playlists", includeTimestamp)
                )
            }
            true
        }

        val importWatchHistory = findPreference<Preference>("import_watch_history")
        importWatchHistory?.setOnPreferenceClickListener {
            createImportFormatDialog(
                requireContext(),
                R.string.import_watch_history,
                importWatchHistoryFormatList
            ) { format, _ ->
                importFormat = format
                getWatchHistoryFile.launch(arrayOf("*/*"))
            }
            true
        }

        childFragmentManager.setFragmentResultListener(
            BACKUP_DIALOG_REQUEST_KEY,
            this
        ) { _, resultBundle ->
            val encodedBackupFile = resultBundle.getString(IntentData.backupFile)!!
            backupFile = Json.decodeFromString(encodedBackupFile)
            // always encrypted: what will open it is chosen first, then where it goes
            BackupProtectionDialogs.askExportSecret(requireContext()) { secret, kind ->
                exportSecret?.first?.fill(Char.MIN_VALUE)
                exportSecret = secret to kind
                val timestamp = TextUtils.getFileSafeTimeStampNow()
                createBackupFile.launch("watube-backup-${timestamp}.$BACKUP_EXTENSION")
            }
        }
        val advancedBackup = findPreference<Preference>("backup")
        advancedBackup?.setOnPreferenceClickListener {
            BackupDialog().show(childFragmentManager, null)
            true
        }

        val restoreAdvancedBackup = findPreference<Preference>("restore")
        restoreAdvancedBackup?.setOnPreferenceClickListener {
            // encrypted backups have no registered type, older ones are JSON
            getBackupFile.launch("*/*")
            true
        }
    }

    companion object {
        const val JSON = "application/json"
        private const val BACKUP_EXTENSION = "watube"

        /**
         * Mimetype to use to create new files when setting extension manually
         */
        const val FILETYPE_ANY = "application/octet-stream"

        val importSubscriptionFormatList = listOf(
            ImportFormat.NEWPIPE,
            ImportFormat.FREETUBE,
            ImportFormat.YOUTUBECSV
        )
        val exportSubscriptionFormatList = listOf(
            ImportFormat.NEWPIPE,
            ImportFormat.FREETUBE
        )
        val importPlaylistFormatList = listOf(
            ImportFormat.PIPED,
            ImportFormat.FREETUBE,
            ImportFormat.YOUTUBECSV,
            ImportFormat.URLSORIDS
        )
        val exportPlaylistFormatList = listOf(
            ImportFormat.PIPED,
            ImportFormat.FREETUBE
        )
        val importWatchHistoryFormatList = listOf(ImportFormat.YOUTUBEJSON)

        fun createImportFormatDialog(
            context: Context,
            @StringRes titleStringId: Int,
            formats: List<ImportFormat>,
            isExport: Boolean = false,
            onConfirm: (ImportFormat, Boolean) -> Unit
        ) {
            var selectedIndex = 0

            val dialog = SecureAlertDialogBuilder(context)
                .setTitle(context.getString(titleStringId))
                .setSingleChoiceItems(
                    formats.map { context.getString(it.value) }.toTypedArray(),
                    selectedIndex
                ) { _, i ->
                    selectedIndex = i
                }

            val layoutInflater = LayoutInflater.from(context)
            val binding = DialogImportExportFormatChooserBinding.inflate(layoutInflater)
            binding.includeTimestamp.isChecked = PreferenceHelper.getBoolean(
                PreferenceKeys.INCLUDE_TIMESTAMP_IN_BACKUP_FILENAME,
                false
            )
            if (isExport) {
                dialog.setView(binding.root)
            }

            dialog.setPositiveButton(R.string.okay) { _, _ ->
                if (isExport) PreferenceHelper.putBoolean(
                    PreferenceKeys.INCLUDE_TIMESTAMP_IN_BACKUP_FILENAME,
                    binding.includeTimestamp.isChecked
                )

                onConfirm(formats[selectedIndex], binding.includeTimestamp.isChecked)
            }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        fun getExportFileName(
            context: Context,
            format: ImportFormat,
            type: String,
            includeTimestamp: Boolean
        ): String {
            var baseString = context.getString(format.value).lowercase()
            baseString += "-${type}"

            if (includeTimestamp) {
                baseString += "-${TextUtils.getFileSafeTimeStampNow()}"
            }

            return "${baseString}.${format.fileExtension}"
        }
    }
}
// endregion
