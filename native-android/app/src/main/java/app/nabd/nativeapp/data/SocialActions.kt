package app.nabd.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject

/** Mutates a freshly validated document only. Repository validates and commits it.
 * Membership/ownership checks deliberately match the existing Telegram adapter.
 */
internal object SocialActions {
    private const val PULSE = "like"
    fun chat(v: JSONObject, self: String, id: String): JSONObject = v.getJSONObject("users").objects()
        .flatMap { it.optJSONObject("chatV1")?.objects().orEmpty() }
        .find { it.getString("id") == id && self in it.getJSONArray("members").strings() } ?: error("FORBIDDEN")
    private fun message(v: JSONObject, self: String, thread: String, id: String): JSONObject =
        chat(v, self, thread).getJSONArray("messages").objects().find { it.getString("id") == id } ?: error("FORBIDDEN")
    fun send(v: JSONObject, self: String, thread: String, text: String, reply: String?, image: String?, ratio: Double, date: String) {
        require(text.trim().length <= 5000 && (text.isNotBlank() || image != null)) { "VALIDATION" }
        require(image == null || (validImage(image) && ratio.isFinite() && ratio > 0)) { "VALIDATION" }
        val messages = chat(v, self, thread).getJSONArray("messages")
        require(reply == null || messages.objects().any { it.getString("id") == reply }) { "VALIDATION" }
        val media = image?.let { JSONObject().put("id", newId()).put("kind", "image").put("uri", it).put("mime", "image/jpeg").put("width", 0).put("height", 0).put("aspectRatio", ratio) }
        messages.put(JSONObject().put("id", newId()).put("senderId", self).put("text", text.trim()).put("createdAt", date)
            .put("read", false).put("edited", false).put("deleted", false).put("replyToId", reply ?: JSONObject.NULL)
            .put("reactions", JSONObject()).put("media", media ?: JSONObject.NULL))
    }
    fun edit(v: JSONObject, self: String, thread: String, id: String, text: String) {
        require(text.trim().length in 1..5000) { "VALIDATION" }
        val m = message(v, self, thread, id)
        require(m.getString("senderId") == self && !m.getBoolean("deleted")) { "FORBIDDEN" }
        m.put("text", text.trim()).put("edited", true)
    }
    fun delete(v: JSONObject, self: String, thread: String, id: String) {
        val m = message(v, self, thread, id)
        require(m.getString("senderId") == self) { "FORBIDDEN" }
        m.put("text", "").put("media", JSONObject.NULL).put("reactions", JSONObject()).put("deleted", true)
    }
    fun react(v: JSONObject, self: String, thread: String, id: String, kind: String?) {
        require(kind == null || kind in REACTIONS) { "VALIDATION" }
        val m = message(v, self, thread, id)
        require(!m.getBoolean("deleted")) { "FORBIDDEN" }
        if (kind == null) m.getJSONObject("reactions").remove(self) else m.getJSONObject("reactions").put(self, kind)
    }
    fun read(v: JSONObject, self: String, thread: String): Boolean {
        var changed = false
        chat(v, self, thread).getJSONArray("messages").objects().forEach {
            if (it.getString("senderId") != self && !it.getBoolean("read")) { it.put("read", true); changed = true }
        }
        return changed
    }
    fun deleteComment(v: JSONObject, self: String, post: String, id: String) {
        val stored = v.getJSONObject("posts").getJSONObject(post); val p = stored.getJSONObject("post")
        val c = p.getJSONArray("comments").objects().find { it.getString("id") == id } ?: error("FORBIDDEN")
        require(c.getJSONObject("author").getString("id") == self || p.getJSONObject("author").getString("id") == self) { "FORBIDDEN" }
        val remaining = p.getJSONArray("comments").objects().filterNot { it.getString("id") == id }
        p.put("comments", JSONArray(remaining)).put("commentsCount", remaining.size)
        stored.getJSONObject("commentLikes").remove(id)
    }
    /** Comments carry the same six reactions as posts; نبضة keeps the legacy array so old clients still count it. */
    fun reactComment(v: JSONObject, self: String, post: String, id: String, kind: String) {
        require(kind in REACTIONS) { "VALIDATION" }
        val stored = v.getJSONObject("posts").getJSONObject(post)
        val c = stored.getJSONObject("post").getJSONArray("comments").objects().find { it.getString("id") == id } ?: error("FORBIDDEN")
        val likes = stored.getJSONObject("commentLikes")
        val pulses = likes.optJSONArray(id)?.strings().orEmpty().toMutableList()
        val votes = c.optJSONObject("votes") ?: JSONObject()
        val counts = c.optJSONObject("reactions") ?: JSONObject()
        val previous = votes.optString(self).ifEmpty { null }
        if (kind == PULSE) {
            if (self in pulses) pulses.remove(self) else pulses.add(self)
            if (previous != null) { votes.remove(self); counts.put(previous, (counts.optInt(previous) - 1).coerceAtLeast(0)) }
        } else {
            if (previous != null) counts.put(previous, (counts.optInt(previous) - 1).coerceAtLeast(0))
            if (previous == kind) votes.remove(self) else { votes.put(self, kind); counts.put(kind, counts.optInt(kind) + 1) }
            pulses.remove(self)
        }
        likes.put(id, JSONArray(pulses))
        c.put("votes", votes).put("reactions", counts)
        c.put("likes", pulses.size + counts.keys().asSequence().sumOf { counts.optInt(it) })
    }
    fun likeComment(v: JSONObject, self: String, post: String, id: String) {
        val stored = v.getJSONObject("posts").getJSONObject(post)
        val c = stored.getJSONObject("post").getJSONArray("comments").objects().find { it.getString("id") == id } ?: error("FORBIDDEN")
        val likes = stored.getJSONObject("commentLikes")
        val old = likes.optJSONArray(id)?.strings().orEmpty()
        val next = if (self in old) old - self else old + self
        likes.put(id, JSONArray(next)); c.put("likes", next.size)
    }
}
