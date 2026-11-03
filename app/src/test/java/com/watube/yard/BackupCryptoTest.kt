package com.watube.yard

import com.watube.yard.helpers.BackupCrypto
import com.watube.yard.helpers.BackupCrypto.SecretKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** Encrypted backups (BackupCrypto): what opens them, and what must not. */
class BackupCryptoTest {
    private val backup = """{"watchHistory":[{"videoId":"abc"}],"preferences":[]}""".toByteArray()
    private val password = "correct horse battery staple".toCharArray()

    @Test
    fun passwordOpensItsBackup() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)

        assertTrue(BackupCrypto.isEncrypted(file))
        assertEquals(SecretKind.PASSWORD, BackupCrypto.secretKindOf(file))
        assertArrayEquals(backup, BackupCrypto.decrypt(file, password.copyOf()))
        // nothing of the content shows in the file
        assertFalse(String(file, Charsets.ISO_8859_1).contains("watchHistory"))
    }

    @Test
    fun recoveryCodeIsAcceptedTypedLoosely() {
        val code = BackupCrypto.newRecoveryCode()
        val file = BackupCrypto.encrypt(backup, code, SecretKind.RECOVERY_CODE)
        // lower case, spaces instead of dashes, O typed for 0 and l for 1
        val typed = String(code).lowercase().replace('-', ' ')
            .replace('0', 'o').replace('1', 'l').toCharArray()

        assertEquals(SecretKind.RECOVERY_CODE, BackupCrypto.secretKindOf(file))
        assertArrayEquals(backup, BackupCrypto.decrypt(file, typed))
    }

    @Test
    fun recoveryCodeHasTheExpectedShape() {
        val code = String(BackupCrypto.newRecoveryCode())
        assertTrue(code, Regex("""^([0-9A-HJKMNP-TV-Z]{4}-){5}[0-9A-HJKMNP-TV-Z]{4}$""").matches(code))
        assertTrue(String(BackupCrypto.newRecoveryCode()) != code)
    }

    @Test(expected = BackupCrypto.WrongSecretException::class)
    fun wrongPasswordIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        BackupCrypto.decrypt(file, "correct horse battery stapl".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongSecretException::class)
    fun alteredContentIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        file[file.size - 20] = (file[file.size - 20].toInt() xor 1).toByte()
        BackupCrypto.decrypt(file, password)
    }

    @Test(expected = BackupCrypto.WrongSecretException::class)
    fun alteredHeaderIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        // fewer passes, still within bounds: authenticated with the content, so refused
        ByteBuffer.wrap(file).putInt(PASSES_OFFSET, 2)
        BackupCrypto.decrypt(file, password)
    }

    @Test
    fun writesTheRecommendedArgon2idCosts() {
        val header = ByteBuffer.wrap(BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD))
        assertEquals(64 * 1024, header.getInt(MEMORY_OFFSET))
        assertEquals(3, header.getInt(PASSES_OFFSET))
        assertEquals(4, header.get(PASSES_OFFSET + 4).toInt())
    }

    @Test(expected = BackupCrypto.InvalidBackupException::class)
    fun excessiveMemoryCostIsRefusedBeforeDerivingAnything() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        ByteBuffer.wrap(file).putInt(MEMORY_OFFSET, 4 * 1024 * 1024)
        BackupCrypto.decrypt(file, password)
    }

    @Test(expected = BackupCrypto.InvalidBackupException::class)
    fun truncatedFileIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        BackupCrypto.decrypt(file.copyOf(30), password)
    }

    @Test(expected = BackupCrypto.WrongSecretException::class)
    fun switchedSecretKindIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        file[KIND_OFFSET] = SecretKind.RECOVERY_CODE.id.toByte()
        BackupCrypto.decrypt(file, password)
    }

    @Test(expected = BackupCrypto.InvalidBackupException::class)
    fun unknownVersionIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        file[KIND_OFFSET - 1] = 9
        BackupCrypto.decrypt(file, password)
    }

    @Test(expected = BackupCrypto.InvalidBackupException::class)
    fun zeroLanesAreRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        file[PASSES_OFFSET + 4] = 0
        BackupCrypto.decrypt(file, password)
    }

    @Test(expected = BackupCrypto.InvalidBackupException::class)
    fun negativeMemoryCostIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        ByteBuffer.wrap(file).putInt(MEMORY_OFFSET, -1)
        BackupCrypto.decrypt(file, password)
    }

    @Test(expected = BackupCrypto.InvalidBackupException::class)
    fun tooShortSaltIsRefused() {
        val file = BackupCrypto.encrypt(backup, password, SecretKind.PASSWORD)
        file[PASSES_OFFSET + 5] = 8
        BackupCrypto.decrypt(file, password)
    }

    @Test
    fun accentedPasswordOpensWhateverTheKeyboardComposes() {
        // "é" as one character (NFC) when saving, as "e" plus an accent (NFD) when opening
        val composed = "mot de passe déjà vu".toCharArray()
        val decomposed = "mot de passe déjà vu".toCharArray()
        val file = BackupCrypto.encrypt(backup, composed, SecretKind.PASSWORD)
        assertArrayEquals(backup, BackupCrypto.decrypt(file, decomposed))
    }

    @Test
    fun legacyClearBackupsAreRecognised() {
        assertFalse(BackupCrypto.isEncrypted(backup))
        assertFalse(BackupCrypto.isEncrypted(ByteArray(0)))
    }

    private companion object {
        /** after the 8 byte magic and the version */
        const val KIND_OFFSET = 9

        /** after the 8 byte magic, the version and the secret kind */
        const val MEMORY_OFFSET = 10
        const val PASSES_OFFSET = 14
    }
}
