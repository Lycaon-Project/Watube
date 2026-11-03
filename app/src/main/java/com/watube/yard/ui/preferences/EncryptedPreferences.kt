package com.watube.yard.ui.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import com.watube.yard.helpers.PreferenceHelper

// region Settings
/**
 * Rows whose value is kept encrypted (see PreferenceHelper.storedForm): the value goes
 * through PreferenceHelper both ways, instead of the default binding that reads and writes
 * the preference file directly, which would show the ciphertext and store the clear value.
 *
 * Showing a row never writes: when the stored value cannot be decrypted for now, the row
 * shows the default, and writing that default back would destroy the value for good.
 */
class EncryptedListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {
    private var settingInitialValue = false

    override fun onSetInitialValue(defaultValue: Any?) {
        settingInitialValue = true
        try {
            super.onSetInitialValue(defaultValue)
        } finally {
            settingInitialValue = false
        }
    }

    override fun getPersistedString(defaultReturnValue: String?): String? =
        persistedValue(key, shouldPersist(), defaultReturnValue)

    override fun persistString(value: String?): Boolean =
        persistValue(key, shouldPersist() && !settingInitialValue, value)
}

class EncryptedEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditTextPreference(context, attrs) {
    private var settingInitialValue = false

    override fun onSetInitialValue(defaultValue: Any?) {
        settingInitialValue = true
        try {
            super.onSetInitialValue(defaultValue)
        } finally {
            settingInitialValue = false
        }
    }

    override fun getPersistedString(defaultReturnValue: String?): String? =
        persistedValue(key, shouldPersist(), defaultReturnValue)

    override fun persistString(value: String?): Boolean =
        persistValue(key, shouldPersist() && !settingInitialValue, value)
}

private fun persistedValue(key: String, persist: Boolean, default: String?): String? {
    if (!persist || !PreferenceHelper.settings.contains(key)) return default
    return PreferenceHelper.getString(key, default.orEmpty()).ifEmpty { default }
}

private fun persistValue(key: String, persist: Boolean, value: String?): Boolean {
    if (!persist) return false
    // rows set their value again on every showing: the same value is not encrypted anew,
    // which would rewrite the file for nothing
    if (value == persistedValue(key, persist = true, default = null)) return true
    if (value == null) PreferenceHelper.remove(key) else PreferenceHelper.putString(key, value)
    return true
}
// endregion
