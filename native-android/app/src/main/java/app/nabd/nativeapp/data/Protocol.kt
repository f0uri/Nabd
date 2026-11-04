package app.nabd.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val MAX_VAULT_BYTES = 2 * 1024 * 1024
val REACTIONS = listOf("like", "love", "haha", "wow", "sad", "angry")
fun newId(): String = UUID.randomUUID().toString().replace("-", "")
fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
fun digest(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))
fun passwordHash(password: String, salt: String): String {
    require(password.length <= 128 && salt.matches(Regex("[a-f0-9]{32}"))) { "INVALID_CREDENTIALS" }
    val spec = PBEKeySpec(password.toCharArray(), salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), 210000, 256)
    return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.joinToString("") { "%02x".format(it.toInt() and 255) } } finally { spec.clearPassword() }
}
fun JSONObject.objects(): List<JSONObject> = keys().asSequence().map { getJSONObject(it) }.toList()
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun JSONArray.list(): List<Any> = (0 until length()).map { get(it) }
fun JSONObject.copyJson() = JSONObject(toString())
fun checkProtocol(condition: Boolean) { require(condition) { "TELEGRAM_INVALID_VAULT" } }
fun uid(value: String) = value.matches(Regex("[a-f0-9]{32}"))
fun validDate(value: String): Boolean = runCatching { java.time.Instant.parse(value) }.isSuccess
fun validImage(value: String): Boolean = value.length <= ((128 * 1024 + 2) / 3) * 4 + 32 && value.matches(Regex("data:image/(jpeg|png|webp);base64,[a-zA-Z0-9+/=]+"))

/** Raw JSON is preserved, including optional per-account extensions. Never rewrite a
 * foreign pin, initialize on a read failure, or silently repair malformed snapshots. */
object VaultProtocol {
    fun validate(v: JSONObject, namespace: String): JSONObject {
        fun safe(value: Any?) {
            if (value is JSONObject) value.keys().forEach { key -> checkProtocol(key !in setOf("__proto__", "constructor", "prototype")); safe(value.get(key)) }
            if (value is JSONArray) value.list().forEach(::safe)
        }
        safe(v)
        checkProtocol(v.toString().toByteArray().size <= MAX_VAULT_BYTES)
        checkProtocol(v.getString("app") == "nabd-direct-telegram" && v.getInt("version") == 1 && v.getString("namespace") == namespace)
        checkProtocol(v.keys().asSequence().toSet() == setOf("app", "version", "namespace", "revision", "writeId", "parent", "users", "posts", "friendships"))
        checkProtocol(v.getLong("revision") >= 0 && uid(v.getString("writeId")) && (v.isNull("parent") || v.getLong("parent") > 0))
        val users = v.getJSONObject("users")
        val emails = mutableSetOf<String>(); val handles = mutableSetOf<String>()
        users.keys().forEach { id ->
            val a = users.getJSONObject(id); val p = a.getJSONObject("profile")
            checkProtocol(uid(id) && p.getString("id") == id && p.getString("name").length in 2..40)
            val email = p.getString("email"); val handle = p.getString("username")
            checkProtocol(email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) && email == email.lowercase(java.util.Locale.ROOT))
            checkProtocol(handle.matches(Regex("[a-z][a-z0-9_]{2,39}")) && handles.add(handle) && emails.add(email))
            checkProtocol(p.getString("color").matches(Regex("#[a-fA-F0-9]{6}")) && p.get("initial") is String)
            checkProtocol(p.optString("bio", "").length <= 160 && (p.isNull("avatarUrl") || validImage(p.getString("avatarUrl"))))
            checkProtocol(uid(a.getString("salt")) && a.getString("passwordHash").matches(Regex("[a-f0-9]{64}")))
            a.getJSONObject("sessions").let { sessions -> sessions.keys().forEach { key -> checkProtocol(key.matches(Regex("[a-f0-9]{64}")) && sessions.getLong(key) >= 0) } }
        }
        fun exists(id: String) = uid(id) && users.has(id)
        v.getJSONArray("friendships").list().forEach { raw ->
            val pair = raw as JSONArray
            checkProtocol(pair.length() == 2 && pair.getString(0) != pair.getString(1) && pair.strings().all(::exists))
        }
        v.getJSONObject("posts").keys().forEach { id ->
            val stored = v.getJSONObject("posts").getJSONObject(id); val p = stored.getJSONObject("post")
            val votes = stored.getJSONObject("votes"); val likes = stored.getJSONObject("commentLikes")
            checkProtocol(uid(id) && p.getString("id") == id && exists(p.getJSONObject("author").getString("id")))
            checkProtocol(p.getString("privacy") == "public" && p.getString("text").length <= 5000 && validDate(p.getString("createdAt")) && p.getLong("shares") >= 0)
            checkProtocol(p.getInt("commentsCount") == p.getJSONArray("comments").length())
            votes.keys().forEach { voter -> checkProtocol(exists(voter) && votes.getString(voter) in REACTIONS) }
            REACTIONS.forEach { kind -> checkProtocol(p.getJSONObject("reactions").getInt(kind) == votes.keys().asSequence().count { votes.getString(it) == kind }) }
            if (!p.isNull("media")) media(p.getJSONObject("media"))
            val comments = mutableSetOf<String>()
            p.getJSONArray("comments").objects().forEach { c ->
                val cid = c.getString("id"); val cl = likes.optJSONArray(cid) ?: JSONArray()
                checkProtocol(uid(cid) && comments.add(cid) && exists(c.getJSONObject("author").getString("id")) && c.getString("text").length <= 2000 && validDate(c.getString("createdAt")))
                checkProtocol(cl.strings().distinct().size == cl.length() && cl.strings().all(::exists) && cl.length() == c.getInt("likes"))
            }
            checkProtocol(likes.keys().asSequence().all { it in comments })
        }
        val messages = mutableSetOf<String>()
        users.keys().forEach { owner ->
            val chats = users.getJSONObject(owner).optJSONObject("chatV1") ?: return@forEach
            chats.keys().forEach { id ->
                val chat = chats.getJSONObject(id); val members = chat.getJSONArray("members").strings()
                checkProtocol(uid(id) && chat.getString("id") == id && members.size == 2 && members.all(::exists) && members[0] < members[1] && members[0] == owner)
                checkProtocol(digest("nabd-chat-v1:${members.joinToString(":")}").take(32) == id && validDate(chat.getString("createdAt")))
                val prior = mutableSetOf<String>()
                chat.getJSONArray("messages").objects().forEach { m ->
                    val mid = m.getString("id")
                    checkProtocol(uid(mid) && messages.add(mid) && m.getString("senderId") in members && m.getString("text").length <= 5000 && validDate(m.getString("createdAt")))
                    checkProtocol(m.get("read") is Boolean && m.get("edited") is Boolean && m.get("deleted") is Boolean)
                    checkProtocol(m.isNull("replyToId") || m.getString("replyToId") in prior)
                    val reactions = m.getJSONObject("reactions")
                    checkProtocol(reactions.keys().asSequence().all { it in members && reactions.getString(it) in REACTIONS })
                    if (m.getBoolean("deleted")) checkProtocol(m.getString("text").isEmpty() && m.isNull("media") && reactions.length() == 0)
                    else checkProtocol(m.getString("text").isNotBlank() || !m.isNull("media"))
                    if (!m.isNull("media")) media(m.getJSONObject("media"))
                    prior.add(mid)
                }
            }
        }
        return v
    }
    private fun media(m: JSONObject) {
        checkProtocol(uid(m.getString("id")) && m.getString("kind") == "image" && validImage(m.getString("uri")))
        checkProtocol(m.getDouble("aspectRatio").isFinite() && m.getDouble("aspectRatio") > 0 && m.getInt("width") >= 0 && m.getInt("height") >= 0)
    }
}
