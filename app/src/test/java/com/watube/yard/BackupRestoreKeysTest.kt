package com.watube.yard

import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.BackupHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreKeysTest {

    private val deviceSecurity = listOf(
        PreferenceKeys.APP_LOCK,
        PreferenceKeys.APP_LOCK_DELAY,
        PreferenceKeys.PRIVATE_DNS,
        PreferenceKeys.PRIVATE_DNS_RESOLVER,
        PreferenceKeys.BLOCK_SCREENSHOTS,
        PreferenceKeys.HIDE_LOCKSCREEN_CONTENT,
        PreferenceKeys.HIDE_RECENTS_PREVIEW,
    )

    @Test
    fun `a backup can not change the security settings of the phone`() {
        deviceSecurity.forEach { assertFalse(it, BackupHelper.isImportable(it)) }
    }

    @Test
    fun `network destinations are still never imported and normal settings are`() {
        assertFalse(BackupHelper.isImportable(PreferenceKeys.AUTH_INSTANCE))
        assertTrue(BackupHelper.isImportable(PreferenceKeys.AUDIO_ONLY_MODE))
    }

    @Test
    fun `the security settings of the phone survive the restore as they are`() {
        val current = mapOf(
            PreferenceKeys.APP_LOCK to true,
            PreferenceKeys.PRIVATE_DNS_RESOLVER to "quad9",
            PreferenceKeys.BLOCK_SCREENSHOTS to true,
            PreferenceKeys.AUDIO_ONLY_MODE to true,
        )
        assertEquals(
            mapOf(
                PreferenceKeys.APP_LOCK to true,
                PreferenceKeys.PRIVATE_DNS_RESOLVER to "quad9",
                PreferenceKeys.BLOCK_SCREENSHOTS to true,
            ),
            BackupHelper.deviceSecurityToKeep(current)
        )
    }

    @Test
    fun `the security settings of the phone are never exported`() {
        deviceSecurity.forEach { assertFalse(it, BackupHelper.isExportable(it)) }
        assertTrue(BackupHelper.isExportable(PreferenceKeys.AUDIO_ONLY_MODE))
    }
}
