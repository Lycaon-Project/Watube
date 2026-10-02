package com.watube.yard.helpers

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Watube: encrypts the authentication token with AES-256-GCM using a key that never leaves
 * the Android Keystore, so the token stays unreadable if the preferences file is extracted.
 *
 * Stored format: `enc:v1:<base64(iv)>:<base64(ciphertext)>`.
 * A crypto failure never falls back to the clear text: [encrypt] refuses to store the value
 * (empty string, so the caller keeps the token out of the preferences) and [decrypt] returns
 * an empty string rather than handing out an unusable ciphertext. The caller treats both as
 * "not logged in", which is the only correct outcome for an unreadable token.
 */
object TokenCipher {
    private val TAG = TokenCipher::class.simpleName

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "com.watube.yard.auth_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_SIZE_BITS = 256

    private val keyLock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(plainValue: String): String {
        if (plainValue.isEmpty() || isEncrypted(plainValue)) return plainValue

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(plainValue.toByteArray(Charsets.UTF_8))
            PREFIX + encode(cipher.iv) + ":" + encode(ciphertext)
        }.getOrElse {
            // never a clear text value in the preferences: refuse to write anything
            Log.w(TAG, "Keystore encryption unavailable, refusing to store the value")
            ""
        }
    }

    fun decrypt(storedValue: String): String {
        if (!isEncrypted(storedValue)) return storedValue

        return runCatching {
            val (iv, ciphertext) = storedValue.removePrefix(PREFIX).split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_LENGTH_BITS, decode(iv))
            )
            String(cipher.doFinal(decode(ciphertext)), Charsets.UTF_8)
        }.getOrElse {
            // the unreadable ciphertext is left untouched in the preferences (no data loss),
            // but it must never be used as a bearer token: the caller sees "no token"
            Log.w(TAG, "Could not decrypt the stored value, treating it as missing")
            ""
        }
    }

    private fun encode(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    private fun decode(data: String): ByteArray = Base64.decode(data, Base64.NO_WRAP)

    /**
     * Keystore access is serialized: two racing key generations would overwrite the alias
     * and permanently orphan the ciphertexts written with the losing key.
     */
    private fun key(): SecretKey {
        cachedKey?.let { return it }

        synchronized(keyLock) {
            cachedKey?.let { return it }

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
            cachedKey = key
            return key
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }
}
