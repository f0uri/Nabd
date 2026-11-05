package app.nabd.nativeapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/** Per-backend session only. Never stores passwords, clears app data, or writes
 * the old React Native database. Read-only, one-time session import for upgrades. */
class SessionStore(private val context: Context, private val namespace: String) : Sessions {
    private val preferences = context.getSharedPreferences("nabd.native.session.$namespace", Context.MODE_PRIVATE)
    private val crypto = KeystoreAes("nabd.native.session.$namespace")
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
            return crypto.decrypt(encoded)?.takeIf(::valid)
        }
        set(value) {
            require(value == null || valid(value)) { "TELEGRAM_SESSION_REQUIRED" }
            if (value == null) { preferences.edit().remove("encryptedToken").putBoolean("legacyChecked", true).commit(); return }
            val encoded = crypto.encrypt(value)
            check(preferences.edit().putString("encryptedToken", encoded).putBoolean("legacyChecked", true).commit()) { "SESSION_STORAGE_ERROR" }
        }
}
