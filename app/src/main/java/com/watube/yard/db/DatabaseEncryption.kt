package com.watube.yard.db

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// region Database
/**
 * The Room database is stored encrypted with SQLCipher (AES-256 and an HMAC-SHA512 per page).
 *
 * Its key is 256 random bits, kept wrapped (AES-256-GCM) by a key that never leaves the Android
 * Keystore, in StrongBox when the phone has one. SQLCipher receives it as a raw key, so opening
 * the database costs no key derivation.
 */
object DatabaseEncryption {
    private val TAG = DatabaseEncryption::class.simpleName

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "com.watube.yard.database"
    private const val PREFS = "database_key"
    private const val WRAPPED_KEY = "wrapped"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_BYTES = 32
    private const val WRAP_KEY_BITS = 256
    private const val KEYSTORE_ATTEMPTS = 3
    private const val KEYSTORE_RETRY_MS = 200L
    private const val ENCRYPTING = ".encrypting"
    private const val PLAIN = ".plain"
    private const val LOST = ".lost"
    private val SIDE_FILES = listOf("-wal", "-shm", "-journal")
    private val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    init {
        System.loadLibrary("sqlcipher")
    }

    /** What reading the stored key gave: the key, gone for good, or not readable right now. */
    private sealed interface StoredKey {
        class Usable(val rawKey: ByteArray) : StoredKey
        data object Lost : StoredKey
        data object Unavailable : StoredKey
    }

    /**
     * The SQLCipher passphrase of [database], after converting a database still in clear text.
     *
     * Null when the database is (or will be) in clear text for now: the Keystore cannot be
     * used, or converting failed. Converting is tried again on the next launch. An encrypted
     * database is never opened without its key, nor deleted because the Keystore failed once.
     */
    fun prepare(context: Context, database: File): ByteArray? {
        recoverInterruptedSwap(database)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encryptedOnDisk = database.exists() && !isPlainText(database)

        val rawKey = when (val stored = storedKey(prefs)) {
            is StoredKey.Usable -> stored.rawKey
            StoredKey.Unavailable -> {
                // a Keystore failing now says nothing about the key: the files are left alone,
                // and an encrypted library stops the app until a later launch can read it
                check(!encryptedOnDisk) { "Keystore unavailable, the library cannot be opened" }
                return null
            }
            StoredKey.Lost -> {
                // encrypted with a key that is gone for good (app data restored elsewhere,
                // Keystore reset): it can never be read again, but it is set aside, not deleted
                if (encryptedOnDisk) setAside(database)
                newKey(prefs) ?: return null
            }
        }

        val passphrase = passphraseOf(rawKey)
        if (database.exists() && isPlainText(database)) {
            runCatching { encrypt(database, passphrase) }.onFailure {
                // the clear text file is untouched: keep using it and try again next launch,
                // rather than leaving the app unable to start
                Log.e(TAG, "Could not encrypt the database, it stays as it is for now", it)
                File(database.path + ENCRYPTING).delete()
                return null
            }
        }
        return passphrase
    }

    /**
     * Ends a file swap that was cut short (the app killed between the renames of [swapIn]).
     * Nothing is deleted unless the encrypted copy is known to be in place.
     */
    private fun recoverInterruptedSwap(database: File) {
        val plain = File(database.path + PLAIN)
        if (!plain.exists()) return
        when {
            !database.exists() -> {
                File(database.path + ENCRYPTING).delete()
                if (!plain.renameTo(database)) Log.e(TAG, "Could not restore the clear text library")
            }
            !isPlainText(database) && database.length() > 0 -> {
                deleteDatabaseFiles(plain)
                // what is left of the clear text database's emptied log, not the encrypted one's
                val log = File(database.path + "-wal")
                if (log.length() == 0L) SIDE_FILES.forEach { File(database.path + it).delete() }
            }
            // two clear text files: which one holds the library cannot be told, keep both
            else -> setAside(plain, from = PLAIN)
        }
    }

    /** Moves a database that cannot be used any more out of the way, keeping a single copy. */
    private fun setAside(database: File, from: String = "") {
        val base = database.path.removeSuffix(from)
        deleteDatabaseFiles(File(base + LOST))
        (listOf("") + SIDE_FILES).forEach { File(database.path + it).renameTo(File(base + LOST + it)) }
        Log.w(TAG, "Unusable database set aside")
    }

    /** SQLCipher raw key syntax: the 256 bits as hex, used as is (no PBKDF2). */
    private fun passphraseOf(rawKey: ByteArray): ByteArray =
        ("x'" + rawKey.joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)

    private fun isPlainText(database: File): Boolean {
        val header = ByteArray(PLAIN_HEADER.size)
        val read = database.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(PLAIN_HEADER)
    }

    /**
     * Copies the clear text database into an encrypted one (schema, data and the version Room
     * reads), checks the copy, then swaps the files. The clear text file is only deleted once
     * the checked encrypted copy is in place, so an interruption never loses the library.
     */
    private fun encrypt(database: File, passphrase: ByteArray) {
        val encrypted = File(database.path + ENCRYPTING)
        encrypted.delete()

        // ATTACH creates the new file with the flags of this connection: it needs CREATE
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        val (version, rows) = SQLiteDatabase.openDatabase(database.path, "", null, flags, null)
            .use { db ->
                // the write-ahead log holds the latest changes: fold it into the file first
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                val version = db.version
                val rows = rowCounts(db)
                db.execSQL(
                    "ATTACH DATABASE ? AS encrypted KEY ?",
                    arrayOf(encrypted.path, String(passphrase, Charsets.US_ASCII))
                )
                db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA encrypted.user_version = $version")
                db.execSQL("DETACH DATABASE encrypted")
                version to rows
            }

        checkCopy(encrypted, passphrase, version, rows)
        swapIn(database, encrypted)
        Log.i(TAG, "Database encrypted")
    }

    /** Rows of every table, the schema tables of SQLite aside. */
    private fun rowCounts(db: SQLiteDatabase): Map<String, Long> {
        val tables = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            null
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return tables.associateWith { table ->
            val quoted = "\"" + table.replace("\"", "\"\"") + "\""
            db.rawQuery("SELECT COUNT(*) FROM $quoted", null).use { it.moveToFirst(); it.getLong(0) }
        }
    }

    /** The encrypted copy opens with the key and holds the same version and rows. */
    private fun checkCopy(copy: File, passphrase: ByteArray, version: Int, rows: Map<String, Long>) {
        SQLiteDatabase.openDatabase(
            copy.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null
        ).use { db ->
            // lists the damaged pages, if any
            db.rawQuery("PRAGMA cipher_integrity_check", null).use {
                check(it.count == 0) { "encrypted copy damaged" }
            }
            check(db.version == version) { "encrypted copy has another version" }
            check(rowCounts(db) == rows) { "encrypted copy misses rows" }
        }
    }

    private fun swapIn(database: File, encrypted: File) {
        val plain = File(database.path + PLAIN)
        check(database.renameTo(plain)) { "could not move the clear text database aside" }
        if (!encrypted.renameTo(database)) {
            plain.renameTo(database)
            error("could not put the encrypted database in place")
        }
        deleteDatabaseFiles(plain)
        SIDE_FILES.forEach { File(database.path + it).delete() }
    }

    private fun deleteDatabaseFiles(database: File) {
        (listOf("") + SIDE_FILES).forEach { File(database.path + it).delete() }
    }

    private fun newKey(prefs: SharedPreferences): ByteArray? = runCatching {
        val rawKey = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val wrapped = encode(cipher.iv) + ":" + encode(cipher.doFinal(rawKey))
        // written synchronously: a key lost on a crash would orphan the database
        prefs.edit(commit = true) { putString(WRAPPED_KEY, wrapped) }
        rawKey
    }.onFailure { Log.e(TAG, "Keystore unavailable, the database stays unencrypted", it) }
        .getOrNull()

    /** A few attempts: the Keystore can be busy or not ready yet right after the phone starts. */
    private fun storedKey(prefs: SharedPreferences): StoredKey {
        val wrapped = prefs.getString(WRAPPED_KEY, null) ?: return StoredKey.Lost
        repeat(KEYSTORE_ATTEMPTS - 1) {
            val result = unwrap(wrapped)
            if (result != StoredKey.Unavailable) return result
            SystemClock.sleep(KEYSTORE_RETRY_MS)
        }
        return unwrap(wrapped)
    }

    private fun unwrap(wrapped: String): StoredKey = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val wrappingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (wrappingKey == null) {
            StoredKey.Lost
        } else {
            val (iv, ciphertext) = wrapped.split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_LENGTH_BITS, decode(iv)))
            StoredKey.Usable(cipher.doFinal(decode(ciphertext)))
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        Log.e(TAG, "Database key invalidated", e)
        StoredKey.Lost
    } catch (e: AEADBadTagException) {
        // the Keystore answered, the wrapped key does not match it: it cannot be recovered
        Log.e(TAG, "Database key does not match its wrapping key", e)
        StoredKey.Lost
    } catch (e: IllegalArgumentException) {
        // malformed stored value (split or base64)
        Log.e(TAG, "Database key unreadable", e)
        StoredKey.Lost
    } catch (e: IndexOutOfBoundsException) {
        Log.e(TAG, "Database key unreadable", e)
        StoredKey.Lost
    } catch (e: Exception) {
        Log.e(TAG, "Keystore unavailable for now", e)
        StoredKey.Unavailable
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return try {
            generateWrappingKey(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            // no secure element on this phone: the key stays in the TEE backed Keystore
            generateWrappingKey(strongBox = false)
        }
    }

    private fun generateWrappingKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(WRAP_KEY_BITS)
                .setIsStrongBoxBacked(strongBox)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    private fun decode(data: String): ByteArray = Base64.decode(data, Base64.NO_WRAP)
}
// endregion
