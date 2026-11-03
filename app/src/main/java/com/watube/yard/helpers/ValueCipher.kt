package com.watube.yard.helpers

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.util.LruCache
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// region Privacy
/**
 * Watube: encrypts the stored values that tell about the user (account token and name,
 * instances, identifiers, channels) with AES-256-GCM, using a key that never leaves the
 * Android Keystore, so they stay unreadable if the preference files are extracted.
 *
 * Stored format: `enc:v1:<base64(iv)>:<base64(ciphertext)>`.
 * A crypto failure never falls back to the clear text: [encrypt] refuses to store the value
 * (empty string, so the caller keeps it out of the preferences) and [decrypt] returns an
 * empty string rather than handing out an unusable ciphertext. The caller treats both as
 * "no value", which is the only correct outcome for an unreadable secret.
 */
object ValueCipher {
    private val TAG = ValueCipher::class.simpleName

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /** named after the token, the first value encrypted: renaming it would orphan the token */
    private const val KEY_ALIAS = "com.watube.yard.auth_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_SIZE_BITS = 256

    /**
     * Clear values of the latest ciphertexts: some are read on every request (the instance),
     * and each decryption is a call to the Keystore. Keyed by the ciphertext itself, so a
     * changed value is never answered from the cache.
     */
    private val decrypted = LruCache<String, String>(32)

    private val keyLock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(plainValue: String): String {
        // a value that merely looks encrypted (typed by the user) is encrypted like any other
        if (plainValue.isEmpty()) return plainValue

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(plainValue.toByteArray(Charsets.UTF_8))
            (PREFIX + encode(cipher.iv) + ":" + encode(ciphertext))
                .also { decrypted.put(it, plainValue) }
        }.getOrElse {
            // never a clear text value in the preferences: refuse to write anything
            Log.w(TAG, "Keystore encryption unavailable, refusing to store the value")
            ""
        }
    }

    fun decrypt(storedValue: String): String {
        if (!isEncrypted(storedValue)) return storedValue
        decrypted.get(storedValue)?.let { return it }

        return runCatching {
            val (iv, ciphertext) = storedValue.removePrefix(PREFIX).split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_LENGTH_BITS, decode(iv))
            )
            String(cipher.doFinal(decode(ciphertext)), Charsets.UTF_8)
                .also { decrypted.put(storedValue, it) }
        }.getOrElse {
            // the unreadable ciphertext is left untouched in the preferences (no data loss),
            // but it must never be used as a value: the caller sees "no value"
            Log.w(TAG, "Could not decrypt the stored value, treating it as missing")
            ""
        }
    }

    /**
     * True only when the Keystore answers and holds no key: values encrypted elsewhere
     * (copied from another phone) can never be read here. A Keystore failing now is not that.
     */
    fun isKeyMissing(): Boolean = cachedKey == null && runCatching {
        !KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.containsAlias(KEY_ALIAS)
    }.getOrDefault(false)

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
// endregion
