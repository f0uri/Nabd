package app.nabd.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val MAX_VAULT_BYTES = 2 * 1024 * 1024
val REACTIONS = listOf("like", "love", "haha", "wow", "sad", "angry")
/** Official account every new profile follows at registration, when it exists. */
const val OFFICIAL_HANDLE = "f0uri"
/** Real point economy. Points are only ever earned by a real, verified event. */
const val POINTS_DAILY_LOGIN = 10
const val POINTS_POST = 20
const val POINTS_INVITE = 50
const val BOOST_COST = 100
const val BOOST_MS = 24L * 60 * 60 * 1000
/** Stories live in the shared vault and expire 24h after they were written. */
const val STORY_TTL_MS = 24L * 60 * 60 * 1000
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
fun JSONArray.strings(): List<String> = (0 until length()).map { get(it) as? String ?: error("TELEGRAM_INVALID_VAULT") }
fun JSONArray.list(): List<Any> = (0 until length()).map { get(it) }
fun JSONObject.copyJson() = JSONObject(toString())
fun checkProtocol(condition: Boolean) { require(condition) { "TELEGRAM_INVALID_VAULT" } }
fun uid(value: String) = value.matches(Regex("[a-f0-9]{32}"))
private val legacyDateFormat = java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter()
// Match JS Date.toISOString(), including .000, so legacy lexical feed ordering
// stays correct when native and RN posts share the same second.
fun isoDate(milliseconds: Long): String = legacyDateFormat.format(java.time.Instant.ofEpochMilli(milliseconds))
fun validDate(value: String): Boolean = runCatching { java.time.Instant.parse(value) }.isSuccess
fun validImage(value: String): Boolean = value.length <= ((128 * 1024 + 2) / 3) * 4 + 32 && value.matches(Regex("data:image/(jpeg|png|webp);base64,[a-zA-Z0-9+/=]+"))

/** Raw JSON is preserved, including optional per-account extensions. Never rewrite a
 * foreign pin, initialize on a read failure, or silently repair malformed snapshots. */
private fun JSONObject.strictString(key: String): String = get(key) as? String ?: error("TELEGRAM_INVALID_VAULT")
private fun JSONArray.strictString(index: Int): String = get(index) as? String ?: error("TELEGRAM_INVALID_VAULT")
private fun JSONObject.strictLong(key: String): Long {
    val value = get(key) as? Number ?: error("TELEGRAM_INVALID_VAULT")
    checkProtocol(value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble() && value.toDouble() <= 9007199254740991.0 && value.toDouble() >= -9007199254740991.0)
    return value.toLong()
}
private fun JSONObject.strictInt(key: String): Int { val value = strictLong(key); checkProtocol(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); return value.toInt() }
private fun JSONObject.strictDouble(key: String): Double = (get(key) as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: error("TELEGRAM_INVALID_VAULT")

object VaultProtocol {
    fun validate(v: JSONObject, namespace: String): JSONObject {
        fun safe(value: Any?) {
            if (value is JSONObject) value.keys().forEach { key -> checkProtocol(key !in setOf("__proto__", "constructor", "prototype")); safe(value.get(key)) }
            if (value is JSONArray) value.list().forEach(::safe)
        }
        safe(v)
        checkProtocol(v.toString().toByteArray().size <= MAX_VAULT_BYTES)
        checkProtocol(v.strictString("app") == "nabd-direct-telegram" && v.strictInt("version") == 1 && v.strictString("namespace") == namespace)
        checkProtocol(v.keys().asSequence().toSet() == setOf("app", "version", "namespace", "revision", "writeId", "parent", "users", "posts", "friendships"))
        checkProtocol(v.strictLong("revision") >= 0 && uid(v.strictString("writeId")) && (v.isNull("parent") || v.strictLong("parent") > 0))
        val users = v.getJSONObject("users")
        fun exists(id: String) = uid(id) && users.has(id)
        val emails = mutableSetOf<String>(); val handles = mutableSetOf<String>()
        users.keys().forEach { id ->
            val a = users.getJSONObject(id); val p = a.getJSONObject("profile")
            checkProtocol(uid(id) && p.strictString("id") == id && p.strictString("name").length in 2..40)
            val email = p.strictString("email"); val handle = p.strictString("username")
            checkProtocol(email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) && email == email.lowercase(java.util.Locale.ROOT))
            checkProtocol(handle.matches(Regex("[a-z][a-z0-9_]{2,39}")) && handles.add(handle) && emails.add(email))
            checkProtocol(p.strictString("color").matches(Regex("#[a-fA-F0-9]{6}")) && p.get("initial") is String)
            checkProtocol((p.isNull("bio") || p.strictString("bio").length <= 160) && (p.isNull("avatarUrl") || validImage(p.strictString("avatarUrl"))))
            checkProtocol(uid(a.strictString("salt")) && a.strictString("passwordHash").matches(Regex("[a-f0-9]{64}")))
            a.getJSONObject("sessions").let { sessions -> sessions.keys().forEach { key -> checkProtocol(key.matches(Regex("[a-f0-9]{64}")) && sessions.strictLong(key) >= 0) } }
            checkProtocol(!a.has("following") || a.optJSONArray("following") != null)
            val following = a.optJSONArray("following") ?: JSONArray()
            checkProtocol(following.strings().distinct().size == following.length() && following.strings().all { exists(it) && it != id })
            checkProtocol(!a.has("points") || (a.get("points") is Int && a.getInt("points") >= 0))
            checkProtocol(!a.has("lastDaily") || a.strictString("lastDaily").matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            checkProtocol(!a.has("inviteCode") || a.strictString("inviteCode").matches(Regex("[A-Za-z0-9]{4,32}")))
            checkProtocol(!a.has("invitedBy") || a.isNull("invitedBy") || (uid(a.strictString("invitedBy")) && users.has(a.strictString("invitedBy"))))
            checkProtocol(!a.has("verified") || a.get("verified") is Boolean)
            checkProtocol(!a.has("banned") || a.get("banned") is Boolean)
            checkProtocol(!a.has("restricted") || a.get("restricted") is Boolean)
            checkProtocol(!a.has("announcements") || a.optJSONArray("announcements") != null)
            checkProtocol(!a.has("stories") || a.optJSONArray("stories") != null)
            a.optJSONArray("stories")?.objects()?.forEach { s ->
                checkProtocol(uid(s.strictString("id")) && s.strictString("text").length <= 2000 && validDate(s.strictString("at")) && validDate(s.strictString("expiresAt")))
                checkProtocol(s.isNull("image") || validImage(s.strictString("image")))
            }
            a.optJSONArray("announcements")?.objects()?.forEach { n ->
                checkProtocol(uid(n.strictString("id")) && n.strictString("text").length in 1..280 && validDate(n.strictString("at")))
            }
        }
        v.getJSONArray("friendships").list().forEach { raw ->
            val pair = raw as JSONArray
            checkProtocol(pair.length() == 2 && pair.strictString(0) != pair.strictString(1) && pair.strings().all(::exists))
        }
        v.getJSONObject("posts").keys().forEach { id ->
            val stored = v.getJSONObject("posts").getJSONObject(id); val p = stored.getJSONObject("post")
            val votes = stored.getJSONObject("votes"); val likes = stored.getJSONObject("commentLikes")
            checkProtocol(uid(id) && p.strictString("id") == id && exists(p.getJSONObject("author").strictString("id")))
            checkProtocol(p.strictString("privacy") == "public" && p.strictString("text").length <= 5000 && validDate(p.strictString("createdAt")) && p.strictLong("shares") >= 0)
            checkProtocol(p.strictInt("commentsCount") == p.getJSONArray("comments").length())
            checkProtocol(p.isNull("pinned") || p.get("pinned") is Boolean)
            checkProtocol(p.isNull("boostedUntil") || validDate(p.strictString("boostedUntil")))
            votes.keys().forEach { voter -> checkProtocol(exists(voter) && votes.strictString(voter) in REACTIONS) }
            REACTIONS.forEach { kind -> checkProtocol(p.getJSONObject("reactions").strictInt(kind) == votes.keys().asSequence().count { votes.strictString(it) == kind }) }
            if (!p.isNull("media")) media(p.getJSONObject("media"))
            val comments = mutableSetOf<String>()
            p.getJSONArray("comments").objects().forEach { c ->
                val cid = c.strictString("id"); val cl = likes.optJSONArray(cid) ?: JSONArray()
                checkProtocol(uid(cid) && comments.add(cid) && exists(c.getJSONObject("author").strictString("id")) && c.strictString("text").length <= 2000 && validDate(c.strictString("createdAt")))
                checkProtocol(c.isNull("image") || validImage(c.strictString("image")))
                checkProtocol(c.isNull("replyTo") || c.strictString("replyTo") in comments)
                checkProtocol(cl.strings().distinct().size == cl.length() && cl.strings().all(::exists) && cl.length() == c.strictInt("likes"))
            }
            checkProtocol(likes.keys().asSequence().all { it in comments })
        }
        val messages = mutableSetOf<String>()
        users.keys().forEach { owner ->
            val account = users.getJSONObject(owner)
            checkProtocol(!account.has("chatV1") || account.optJSONObject("chatV1") != null)
            val chats = account.optJSONObject("chatV1") ?: return@forEach
            chats.keys().forEach { id ->
                val chat = chats.getJSONObject(id); val members = chat.getJSONArray("members").strings()
                checkProtocol(uid(id) && chat.strictString("id") == id && members.size == 2 && members.all(::exists) && members[0] < members[1] && members[0] == owner)
                checkProtocol(digest("nabd-chat-v1:${members.joinToString(":")}").take(32) == id && validDate(chat.strictString("createdAt")))
                val prior = mutableSetOf<String>()
                chat.getJSONArray("messages").objects().forEach { m ->
                    val mid = m.strictString("id")
                    checkProtocol(uid(mid) && messages.add(mid) && m.strictString("senderId") in members && m.strictString("text").length <= 5000 && validDate(m.strictString("createdAt")))
                    checkProtocol(m.get("read") is Boolean && m.get("edited") is Boolean && m.get("deleted") is Boolean)
                    checkProtocol(m.isNull("replyToId") || m.strictString("replyToId") in prior)
                    val reactions = m.getJSONObject("reactions")
                    checkProtocol(reactions.keys().asSequence().all { it in members && reactions.strictString(it) in REACTIONS })
                    if (m.getBoolean("deleted")) checkProtocol(m.strictString("text").isEmpty() && m.isNull("media") && reactions.length() == 0)
                    else checkProtocol(m.strictString("text").isNotBlank() || !m.isNull("media"))
                    if (!m.isNull("media")) media(m.getJSONObject("media"))
                    prior.add(mid)
                }
            }
        }
        return v
    }
    private fun media(m: JSONObject) {
        checkProtocol(uid(m.strictString("id")) && m.strictString("kind") == "image" && validImage(m.strictString("uri")))
        checkProtocol(m.strictDouble("aspectRatio").isFinite() && m.strictDouble("aspectRatio") > 0 && m.strictInt("width") >= 0 && m.strictInt("height") >= 0)
    }
}
