package com.watube.yard.ui.dialogs

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.PersistableBundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.watube.yard.R
import com.watube.yard.databinding.DialogBackupProtectionBinding
import com.watube.yard.databinding.DialogBackupUnlockBinding
import com.watube.yard.databinding.DialogRecoveryCodeBinding
import com.watube.yard.helpers.BackupCrypto
import com.watube.yard.helpers.BackupCrypto.SecretKind
import com.watube.yard.ui.base.SecureAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// region Backup & Restore
/**
 * The dialogs of the encrypted backups (see [BackupCrypto]): choosing what will open a backup,
 * a password or a recovery code shown once, and opening one.
 *
 * They show or take a secret, so they are kept out of screenshots and screen recordings
 * whatever the setting. Secrets travel as char arrays, cleared once used, never as strings
 * (but for the recovery code on screen and in the clipboard, where it has to be text).
 */
object BackupProtectionDialogs {
    /** Asks what will open the backup, then hands it over (the receiver clears it once used). */
    fun askExportSecret(context: Context, onReady: (CharArray, SecretKind) -> Unit) {
        val binding = DialogBackupProtectionBinding.inflate(LayoutInflater.from(context))
        val minLength = BackupCrypto.MIN_PASSWORD_LENGTH
        binding.backupPasswordLayout.helperText =
            context.getString(R.string.backup_password_helper, minLength)
        binding.backupProtectionChoice.setOnCheckedChangeListener { _, checkedId ->
            val password = checkedId == R.id.backup_protect_password
            binding.backupPasswordPanel.isVisible = password
            binding.backupCodePanel.isVisible = !password
        }
        // an error goes away as soon as the user corrects the field
        binding.backupPassword.doAfterTextChanged { binding.backupPasswordLayout.error = null }
        binding.backupPasswordConfirm.doAfterTextChanged {
            binding.backupPasswordConfirmLayout.error = null
        }

        val dialog = SecureAlertDialogBuilder(context)
            .setTitle(R.string.backup_protect_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_continue, null)
            .createProtected()
        dialog.setOnDismissListener {
            binding.backupPassword.text?.clear()
            binding.backupPasswordConfirm.text?.clear()
        }
        dialog.show()

        // a second tap before the dialog is gone must not hand a second secret over
        var handedOver = false
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (handedOver) return@setOnClickListener
            if (binding.backupProtectCode.isChecked) {
                handedOver = true
                dialog.dismiss()
                showRecoveryCode(context, BackupCrypto.newRecoveryCode(), onReady)
                return@setOnClickListener
            }
            val password = binding.backupPassword.text.toChars()
            val confirm = binding.backupPasswordConfirm.text.toChars()
            val matches = password.contentEquals(confirm)
            confirm.fill(Char.MIN_VALUE)
            when {
                password.size < minLength -> {
                    password.fill(Char.MIN_VALUE)
                    binding.backupPasswordLayout.error =
                        context.getString(R.string.backup_password_too_short, minLength)
                }
                !matches -> {
                    password.fill(Char.MIN_VALUE)
                    binding.backupPasswordConfirmLayout.error =
                        context.getString(R.string.backup_password_mismatch)
                }
                else -> {
                    handedOver = true
                    dialog.dismiss()
                    onReady(password, SecretKind.PASSWORD)
                }
            }
        }
    }

    /** The code is only handed over once the user confirms having written it down. */
    private fun showRecoveryCode(
        context: Context,
        code: CharArray,
        onReady: (CharArray, SecretKind) -> Unit
    ) {
        val binding = DialogRecoveryCodeBinding.inflate(LayoutInflater.from(context))
        binding.recoveryCode.text = String(code)
        binding.recoveryCodeCopy.setOnClickListener { copySensitive(context, code) }

        val dialog = SecureAlertDialogBuilder(context)
            .setTitle(R.string.recovery_code_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_create, null)
            .createProtected()
        var handedOver = false
        dialog.setOnDismissListener {
            if (!handedOver) code.fill(Char.MIN_VALUE)
            clearCopiedCode(context)
        }
        dialog.show()

        val create = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        create.isEnabled = false
        binding.recoveryCodeNoted.setOnCheckedChangeListener { _, noted -> create.isEnabled = noted }
        create.setOnClickListener {
            if (handedOver) return@setOnClickListener
            handedOver = true
            dialog.dismiss()
            onReady(code, SecretKind.RECOVERY_CODE)
        }
    }

    /**
     * Asks for the secret of an encrypted backup. [open] runs off the main thread and returns
     * the backup, or null when the secret is wrong: the dialog then stays, with the error.
     */
    fun askUnlockSecret(
        context: Context,
        kind: SecretKind,
        scope: CoroutineScope,
        open: (CharArray) -> ByteArray?,
        onOpened: (ByteArray) -> Unit
    ) {
        val binding = DialogBackupUnlockBinding.inflate(LayoutInflater.from(context))
        bindSecretField(context, binding, kind)

        val dialog = SecureAlertDialogBuilder(context)
            .setTitle(R.string.backup_unlock_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_unlock_open, null)
            .createProtected()
        var attempt: Job? = null
        dialog.setOnDismissListener {
            attempt?.cancel()
            binding.backupUnlockSecret.text?.clear()
        }
        dialog.show()

        val openButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        openButton.setOnClickListener {
            val secret = binding.backupUnlockSecret.text.toChars()
            if (secret.isEmpty()) return@setOnClickListener
            setBusy(binding, openButton, busy = true)
            attempt = scope.launch {
                val result = try {
                    Result.success(withContext(Dispatchers.Default) { open(secret) })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                } catch (e: OutOfMemoryError) {
                    // the key derivation did not fit in this phone's memory
                    Result.failure(e)
                } finally {
                    secret.fill(Char.MIN_VALUE)
                }
                val backup = result.getOrNull()
                if (backup != null) {
                    dialog.dismiss()
                    onOpened(backup)
                    return@launch
                }
                setBusy(binding, openButton, busy = false)
                binding.backupUnlockLayout.error = context.getString(
                    when {
                        result.isFailure -> R.string.backup_unreadable
                        kind == SecretKind.RECOVERY_CODE -> R.string.backup_unlock_wrong_code
                        else -> R.string.backup_unlock_wrong_password
                    }
                )
            }
        }
    }

    private fun bindSecretField(
        context: Context,
        binding: DialogBackupUnlockBinding,
        kind: SecretKind
    ) {
        val field = binding.backupUnlockSecret
        if (kind == SecretKind.RECOVERY_CODE) {
            binding.backupUnlockMessage.setText(R.string.backup_unlock_code_message)
            binding.backupUnlockLayout.hint = context.getString(R.string.backup_protect_code)
            // shown as typed (it is copied from paper), capitals, no suggestions kept
            field.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            // a password manager must not offer to save it as a password of the app
            field.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        } else {
            binding.backupUnlockMessage.setText(R.string.backup_unlock_password_message)
            binding.backupUnlockLayout.hint = context.getString(R.string.password)
            field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            field.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            binding.backupUnlockLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        field.doAfterTextChanged { binding.backupUnlockLayout.error = null }
    }

    /** The key derivation takes a moment: the field and the button wait with the user. */
    private fun setBusy(binding: DialogBackupUnlockBinding, openButton: View, busy: Boolean) {
        binding.backupUnlockProgress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        binding.backupUnlockLayout.isEnabled = !busy
        openButton.isEnabled = !busy
    }

    /** Marked sensitive: hidden from the clipboard preview and the keyboard suggestions. */
    private fun copySensitive(context: Context, code: CharArray) {
        val clip = ClipData.newPlainText(context.getString(R.string.recovery_code_label), String(code))
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
    }

    /** Once the code is noted, it leaves the clipboard; anything copied since then stays. */
    private fun clearCopiedCode(context: Context) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        val label = clipboard.primaryClipDescription?.label ?: return
        if (label == context.getString(R.string.recovery_code_label)) clipboard.clearPrimaryClip()
    }

    private fun Editable?.toChars(): CharArray {
        val text = this ?: return CharArray(0)
        return CharArray(text.length).also { TextUtils.getChars(text, 0, text.length, it, 0) }
    }

    private fun AlertDialog.Builder.createProtected(): AlertDialog = create().apply {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
// endregion
