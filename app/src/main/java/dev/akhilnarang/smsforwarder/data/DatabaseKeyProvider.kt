package dev.akhilnarang.smsforwarder.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates and securely stores the SQLCipher database passphrase.
 * The passphrase is a 32-byte random key, base64-encoded and stored in
 * EncryptedSharedPreferences so it is itself encrypted at rest.
 */
class DatabaseKeyProvider(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        prefs =
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }

    fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val committed = prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            throw IllegalStateException(
                "Failed to persist database passphrase; refusing to proceed to avoid losing the encrypted DB."
            )
        }
        return passphrase
    }

    private companion object {
        const val PREFS_NAME = "db_key_store"
        const val KEY_PASSPHRASE = "db_passphrase"
    }
}
