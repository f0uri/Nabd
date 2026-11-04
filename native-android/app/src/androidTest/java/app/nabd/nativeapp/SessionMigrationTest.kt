package app.nabd.nativeapp

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.nabd.nativeapp.data.SessionStore
import org.junit.Assert.*
import org.junit.Test

class SessionMigrationTest {
    @Test fun legacySyntheticSessionImportsEncryptedWithoutDeletingOrEditingOldRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "e".repeat(24)
        val token = "nabd-tg:$namespace:${"a".repeat(32)}:${"c".repeat(64)}"
        val path = context.getDatabasePath("RKStorage"); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS catalystLocalStorage (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT OR REPLACE INTO catalystLocalStorage (key,value) VALUES (?,?)", arrayOf("nabd.telegram.$namespace.token", token))
        }
        context.getSharedPreferences("nabd.native.session.$namespace", 0).edit().clear().commit()
        val store = SessionStore(context, namespace)
        assertEquals(token, store.token)
        assertFalse(context.getSharedPreferences("nabd.native.session.$namespace", 0).all.values.any { it == token })
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM catalystLocalStorage WHERE key=?", arrayOf("nabd.telegram.$namespace.token")).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(token, cursor.getString(0)) }
        }
        store.token = null
        assertNull(store.token)
        // Logout must not automatically re-import a legacy token on next startup.
        assertNull(SessionStore(context, namespace).token)
    }
}
