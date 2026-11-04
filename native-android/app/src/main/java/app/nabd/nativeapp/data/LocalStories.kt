package app.nabd.nativeapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class LocalStory(val id: String, val text: String, val image: String?, val date: String)

/** Stories were LOCAL in the old app too. No fictional server publishing.
 * Per-namespace/account files; original AsyncStorage row is strictly read-only.
 */
class LocalStories(private val context: Context, private val namespace: String) {
    private val mutex = Mutex()
    private val limit = 16 * 1024 * 1024
    init { require(namespace.matches(Regex("[a-f0-9]{24}"))) }
    private fun file(self: String): AtomicFile {
        require(uid(self))
        val folder = File(context.filesDir, "native-stories").apply { mkdirs() }
        return AtomicFile(File(folder, "$namespace.$self.json"))
    }
    private fun read(target: AtomicFile): JSONArray = target.openRead().use { JSONArray(readBounded(it, limit).toString(Charsets.UTF_8)) }
    private fun save(target: AtomicFile, value: JSONArray) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= limit && value.length() <= 80) { "LOCAL_STORIES_FULL" }
        val stream = target.startWrite()
        try { stream.write(bytes); target.finishWrite(stream) } catch (e: Exception) { target.failWrite(stream); throw e }
    }
    private suspend fun loadRaw(self: String): JSONArray {
        val target = file(self)
        if (target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()) return read(target)
        val path = context.getDatabasePath("RKStorage")
        val raw = if (path.isFile) SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.query("catalystLocalStorage", arrayOf("value"), "key = ?", arrayOf("nabd.stories.v1"), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } else null
        require(raw == null || raw.toByteArray().size <= limit) { "LOCAL_STORIES_FULL" }
        val original = raw?.let(::JSONArray) ?: JSONArray()
        val imported = JSONArray()
        original.objects().filter { it.optJSONObject("author")?.optString("id") == self && it.optString("id").startsWith("s_mine") }.forEach { old ->
            val copy = old.copyJson()
            val image = if (old.isNull("imageUri")) null else old.optString("imageUri").takeIf { it.isNotBlank() }
            if (image != null && Uri.parse(image).scheme == "file") {
                val source = File(Uri.parse(image).path ?: error("LOCAL_STORY_IMAGE"))
                // Only copy the app's own old files, never arbitrary filesystem paths.
                require(source.canonicalPath.startsWith(context.applicationInfo.dataDir.let { File(it).canonicalPath } + "/")) { "LOCAL_STORY_IMAGE" }
                if (source.isFile) copy.put("imageUri", inlineImage(context, Uri.fromFile(source)).first)
                // Keep a missing old URI so its text/identity is not silently discarded.
            }
            imported.put(copy)
        }
        save(target, imported)
        return imported
    }
    private fun present(value: JSONArray): List<LocalStory> = value.objects().map {
        LocalStory(it.getString("id"), it.optString("text", ""), if (it.isNull("imageUri")) null else it.optString("imageUri").takeIf(String::isNotBlank), it.optString("createdAt", ""))
    }
    suspend fun load(self: String): List<LocalStory> = withContext(Dispatchers.IO) { mutex.withLock { present(loadRaw(self)) } }
    suspend fun add(self: Person, text: String, image: String?) = withContext(Dispatchers.IO) { mutex.withLock {
        require(text.length <= 2000 && (text.isNotBlank() || image != null) && (image == null || validImage(image))) { "VALIDATION" }
        val previous = loadRaw(self.id)
        val story = JSONObject().put("id", "s_mine_${newId()}").put("text", text.trim()).put("imageUri", image ?: JSONObject.NULL)
            .put("author", JSONObject().put("id", self.id).put("name", self.name).put("username", self.username).put("isMe", true))
            .put("createdAt", Instant.now().toString()).put("seen", true).put("colors", JSONArray(listOf("#E8264A", "#22151C")))
        save(file(self.id), JSONArray(listOf(story) + previous.objects()))
    } }
    suspend fun remove(self: String, id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        save(file(self), JSONArray(loadRaw(self).objects().filterNot { it.getString("id") == id }))
    } }
}
