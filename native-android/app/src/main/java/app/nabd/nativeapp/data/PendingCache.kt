package app.nabd.nativeapp.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pending presses — posts, comments, messages, stories, reactions,
 * follows and shares the vault has not confirmed yet — kept on the
 * device so they survive a restart or an app update. Without this a
 * post that failed to publish vanished the moment the app was
 * reinstalled, because the pending rows lived only in memory.
 *
 * Only what the user typed (or tapped) is stored — never a session, a
 * password hash or a salt — and the rows are tagged with the account
 * that made them, so another account's leftovers are never restored.
 */
object PendingCache {
    private const val FILE = "nabd-pending.json"

    fun save(context: Context, pending: PendingState, account: String) {
        try {
            val file = AtomicFile(File(context.filesDir, FILE))
            val stream = file.startWrite()
            stream.write(write(pending, account).toString().toByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            try { File(context.filesDir, FILE).delete() } catch (_: Exception) { }
        }
    }

    /** The account that made the rows, with the rows — null when nothing was ever saved. */
    fun load(context: Context): Pair<String, PendingState>? = try {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) null else read(JSONObject(file.readText()))
    } catch (_: Exception) { null }

    fun clear(context: Context) { try { File(context.filesDir, FILE).delete() } catch (_: Exception) { } }

    private fun write(pending: PendingState, account: String) = JSONObject()
        .put("account", account)
        .put("comments", JSONArray(pending.comments.map { c -> JSONObject().put("key", c.key).put("post", c.post).put("text", c.text)
            .put("image", c.image ?: JSONObject.NULL).put("replyTo", c.replyTo ?: JSONObject.NULL).put("date", c.date).put("failed", c.failed) }))
        .put("messages", JSONArray(pending.messages.map { m -> JSONObject().put("key", m.key).put("thread", m.thread).put("text", m.text)
            .put("image", m.image ?: JSONObject.NULL).put("replyTo", m.replyTo ?: JSONObject.NULL).put("date", m.date).put("failed", m.failed) }))
        .put("posts", JSONArray(pending.posts.map { p -> JSONObject().put("key", p.key).put("text", p.text)
            .put("image", p.image ?: JSONObject.NULL).put("date", p.date).put("failed", p.failed) }))
        .put("stories", JSONArray(pending.stories.map { s -> JSONObject().put("key", s.key).put("text", s.text)
            .put("image", s.image ?: JSONObject.NULL).put("date", s.date).put("failed", s.failed) }))
        .put("reactions", JSONObject().apply { pending.reactions.forEach { (id, kind) -> put(id, kind ?: JSONObject.NULL) } })
        .put("follows", JSONObject().apply { pending.follows.forEach { (id, follow) -> put(id, follow) } })
        .put("reelComments", JSONArray(pending.reelComments.map { r -> JSONObject().put("key", r.key).put("reel", r.reel).put("text", r.text)
            .put("date", r.date).put("failed", r.failed) }))
        .put("shares", JSONObject().apply { pending.shares.forEach { (id, n) -> put(id, n) } })

    private fun read(json: JSONObject): Pair<String, PendingState> {
        val account = json.optString("account")
        fun text(obj: JSONObject, key: String): String? = if (obj.isNull(key)) null else obj.optString(key)
        val comments = json.optJSONArray("comments")?.objects()?.map { o -> PendingComment(o.getString("key"), o.getString("post"), o.getString("text"),
            text(o, "image"), text(o, "replyTo"), o.getString("date"), o.optBoolean("failed")) } ?: emptyList()
        val messages = json.optJSONArray("messages")?.objects()?.map { o -> PendingMessage(o.getString("key"), o.getString("thread"), o.getString("text"),
            text(o, "image"), text(o, "replyTo"), o.getString("date"), o.optBoolean("failed")) } ?: emptyList()
        val posts = json.optJSONArray("posts")?.objects()?.map { o -> PendingPost(o.getString("key"), o.getString("text"),
            text(o, "image"), o.getString("date"), o.optBoolean("failed")) } ?: emptyList()
        val stories = json.optJSONArray("stories")?.objects()?.map { o -> PendingStory(o.getString("key"), o.getString("text"),
            text(o, "image"), o.getString("date"), o.optBoolean("failed")) } ?: emptyList()
        val reactions = json.optJSONObject("reactions")?.let { obj -> obj.keys().asSequence().associateWith { k -> text(obj, k) } } ?: emptyMap()
        val follows = json.optJSONObject("follows")?.let { obj -> obj.keys().asSequence().associateWith { k -> obj.optBoolean(k) } } ?: emptyMap()
        val reelComments = json.optJSONArray("reelComments")?.objects()?.map { o -> PendingReelComment(o.getString("key"), o.getString("reel"),
            o.getString("text"), o.getString("date"), o.optBoolean("failed")) } ?: emptyList()
        val shares = json.optJSONObject("shares")?.let { obj -> obj.keys().asSequence().associateWith { k -> obj.optInt(k) } } ?: emptyMap()
        return account to PendingState(comments, messages, posts, stories, reactions, follows, reelComments, shares)
    }
}
