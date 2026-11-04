package app.nabd.nativeapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per-backend session only. Never stores passwords, clears app data, or writes
 * the old React Native database. Read-only, one-time session import for upgrades. */
class SessionStore(private val context: Context, private val namespace: String) : Sessions {
    private val preferences = context.getSharedPreferences("nabd.native.session.$namespace", Context.MODE_PRIVATE)
    private val alias = "nabd.native.session.$namespace"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun valid(value: String) = value.matches(Regex("nabd-tg:$namespace:[a-f0-9]{32}:[a-f0-9]{64}"))
    @get:Synchronized @set:Synchronized override var token: String?
        get() {
            if (!preferences.getBoolean("legacyChecked", false)) {
                val file = context.getDatabasePath("RKStorage")
                val legacy = if (file.isFile) runCatching {
                    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.query("catalystLocalStorage", arrayOf("value"), "key = ?", arrayOf("nabd.telegram.$namespace.token"), null, null, null).use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0).takeIf(::valid) else null
                        }
                    }
                }.getOrNull() else null
                if (legacy != null) token = legacy
                preferences.edit().putBoolean("legacyChecked", true).commit()
            }
            val encoded = preferences.getString("encryptedToken", null) ?: return null
            return runCatching {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8).takeIf(::valid)
            }.getOrNull()
        }
        set(value) {
            require(value == null || valid(value)) { "TELEGRAM_SESSION_REQUIRED" }
            if (value == null) { preferences.edit().remove("encryptedToken").putBoolean("legacyChecked", true).commit(); return }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
            val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
            check(preferences.edit().putString("encryptedToken", encoded).putBoolean("legacyChecked", true).commit()) { "SESSION_STORAGE_ERROR" }
        }
}
