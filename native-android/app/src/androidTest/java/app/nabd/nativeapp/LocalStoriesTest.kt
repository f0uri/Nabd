package app.nabd.nativeapp

import android.database.sqlite.SQLiteDatabase
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import app.nabd.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalStoriesTest {
    @Test fun importsOnlyRealOwnersStoriesReadOnlyAndPersistsNativeChangesSeparately() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val self = "1".repeat(32); val other = "2".repeat(32); val ns = "1".repeat(24)
        fun legacy(id: String, owner: String) = JSONObject().put("id", id).put("author", JSONObject().put("id", owner)).put("text", "قصة محفوظة").put("imageUri", JSONObject.NULL).put("createdAt", "الآن")
        val original = JSONArray().put(legacy("s_mine_old", self)).put(legacy("s_mine_other", other)).toString()
        val dbPath = context.getDatabasePath("RKStorage"); dbPath.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS catalystLocalStorage (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT OR REPLACE INTO catalystLocalStorage (key,value) VALUES (?,?)", arrayOf("nabd.stories.v1", original))
        }
        val directory = File(context.filesDir, "native-stories")
        directory.listFiles()?.filter { it.name.startsWith(ns) }?.forEach { it.delete() }
        val store = LocalStories(context, ns)
        assertEquals(listOf("s_mine_old"), store.load(self).map { it.id })
        store.add(Person(self, "Real User", "real_user", "", null, false), "قصة Kotlin", null)
        assertEquals(2, LocalStories(context, ns).load(self).size)
        assertEquals(listOf("s_mine_other"), store.load(other).map { it.id })
        store.remove(self, "s_mine_old")
        assertEquals("قصة Kotlin", store.load(self).single().text)
        SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM catalystLocalStorage WHERE key=?", arrayOf("nabd.stories.v1")).use { c -> assertTrue(c.moveToFirst()); assertEquals(original, c.getString(0)) }
        }
    }
    @Test fun sharesUseReadOnlyScopedProviderUrisAndNeverPrivateFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val person = Person("a".repeat(32), "Alice", "alice", "", null, false)
        val post = FeedPost("b".repeat(32), person, "shared text", "", "data:image/jpeg;base64,YQ==", 1f, null, 0, emptyList())
        val chooser = shareIntent(context, post)
        @Suppress("DEPRECATION") val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, target.action)
        assertEquals("image/jpeg", target.type)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, target.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val uri = target.clipData!!.getItemAt(0).uri
        assertEquals("${context.packageName}.native.updates", uri.authority)
        context.contentResolver.openInputStream(uri).use { assertEquals(97, it!!.read()) }
        assertTrue(runCatching { androidx.core.content.FileProvider.getUriForFile(context, uri.authority!!, File(context.filesDir, "secret")) }.isFailure)
    }
}
