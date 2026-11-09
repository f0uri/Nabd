package app.nabd.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * A picture is a Telegram document of its own.
 *
 * The vault is one 2 MB document and a photo used to be written inside it as
 * text: ten pictures were enough to fill it, and a full vault refuses every
 * write, so nothing could be published any more. From here the vault keeps a
 * small preview of each picture plus the id of the document holding the real
 * one, and the picture is fetched from Telegram when it is shown.
 */
/** What the vault keeps of a voice note once the recording became a document of its own. */
const val AUDIO_PLACEHOLDER = "data:audio/3gp;base64,A"

object MediaFiles {
    private var transport: VaultTransport? = null
    private var directory: File? = null
    private val lock = Mutex()

    /** Called once, when the repository is built. */
    fun attach(transport: VaultTransport, cacheDir: File) {
        this.transport = transport
        this.directory = File(cacheDir, "nabd-media").apply { mkdirs() }
    }

    /** What the app carries instead of the picture itself. */
    fun reference(fileId: String) = "tg:$fileId"
    fun isReference(value: String?) = value != null && value.startsWith("tg:")

    /**
     * The picture behind a reference, downloaded once and kept on the device so
     * scrolling back to an old post does not ask Telegram again.
     */
    suspend fun file(value: String?): File? {
        if (!isReference(value)) return null
        val id = value!!.removePrefix("tg:")
        val dir = directory ?: return null
        val target = File(dir, id.filter { it.isLetterOrDigit() }.take(48) + ".img")
        return withContext(Dispatchers.IO) {
            lock.withLock {
                if (target.length() > 0) return@withLock target
                val bytes = transport?.fetchMedia(id) ?: return@withLock null
                val part = File(dir, target.name + ".part")
                part.writeBytes(bytes)
                if (part.renameTo(target)) target else { part.delete(); null }
            }
        }
    }

    /**
     * Sends one picture to Telegram and answers with what the vault stores: the
     * small preview and the id of the document that now holds the picture.
     * A picture too small to matter, or one Telegram refuses, stays inline.
     */
    suspend fun publish(data: String, preview: (String) -> String?): Pair<String, String>? {
        val kind = kindOf(data) ?: return null
        if (data.length <= MEDIA_INLINE_BYTES) return null
        return withContext(Dispatchers.IO) {
            val bytes = android.util.Base64.decode(data.substringAfter(','), android.util.Base64.NO_WRAP)
            if (bytes.isEmpty() || bytes.size > MAX_MEDIA_BYTES) return@withContext null
            val id = runCatching { transport?.uploadMedia(bytes, "nabd-media.${kind.second}", kind.first) }.getOrNull() ?: return@withContext null
            val small = preview(data) ?: return@withContext null
            small to id
        }
    }

    /**
     * Makes room in the vault. The biggest pictures leave first and each one
     * becomes a document of its own, until the vault is back under [needed]
     * bytes. The preview stays in its place, so nothing vanishes from the feed.
     * Returns how many pictures moved out.
     */
    suspend fun spill(v: JSONObject, needed: Int = VAULT_SOFT_BYTES, move: suspend (String) -> Pair<String, String>?): Int {
        if (v.toString().toByteArray().size <= needed) return 0
        val pictures = mutableListOf<Triple<JSONObject, String, Int>>()
        fun add(holder: JSONObject?, key: String) {
            val value = holder?.optString(key).orEmpty()
            if (value.isBlank() || !value.startsWith("data:")) return
            pictures += Triple(holder!!, key, value.length)
        }
        v.optJSONObject("posts")?.keys()?.forEach { id ->
            val post = v.optJSONObject("posts").optJSONObject(id)?.optJSONObject("post") ?: return@forEach
            add(post.optJSONObject("media"), "uri")
            post.optJSONArray("comments")?.objects()?.forEach { comment -> add(comment, "image") }
        }
        v.optJSONObject("users")?.keys()?.forEach { owner ->
            val account = v.optJSONObject("users").optJSONObject(owner) ?: return@forEach
            account.optJSONArray("stories")?.objects()?.forEach { story -> add(story, "image") }
            account.optJSONArray("highlightsV1")?.objects()?.forEach { group ->
                group.optJSONArray("stories")?.objects()?.forEach { story -> add(story, "image") }
            }
            add(account.optJSONObject("profile"), "avatarUrl")
            account.optJSONObject("chatV1")?.objects()?.forEach { chat ->
                chat.optJSONObject("audioV1")?.objects()?.forEach { note -> add(note, "uri") }
            }
        }
        var moved = 0
        pictures.sortedByDescending { it.third }.forEach { (holder, key, _) ->
            if (v.toString().toByteArray().size <= needed) return moved
            val data = holder.optString(key)
            if (data.length <= MEDIA_INLINE_BYTES || holder.optString("fileId").isNotBlank()) return@forEach
            val pair = move(data) ?: return@forEach
            holder.put(key, pair.first).put("fileId", pair.second)
            moved++
        }
        return moved
    }

    /** A picture or a recording, and the name and type its document is sent with. */
    private fun kindOf(data: String): Pair<String, String>? = when {
        data.startsWith("data:image/png") -> "image/png" to "png"
        data.startsWith("data:image/webp") -> "image/webp" to "webp"
        data.startsWith("data:image/") -> "image/jpeg" to "jpg"
        // Telegram keeps a recording as a document, so the type is ours to choose.
        data.startsWith("data:audio/") -> "audio/3gp" to "3gp"
        else -> null
    }
}
