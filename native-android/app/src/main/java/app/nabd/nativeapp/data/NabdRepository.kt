package app.nabd.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

interface Sessions { var token: String? }
data class Person(val id: String, val name: String, val username: String, val bio: String, val avatar: String?, val friend: Boolean, val following: Boolean = false)
data class Comment(val id: String, val author: Person, val text: String, val likes: Int, val likedByMe: Boolean = false)
data class FeedPost(val id: String, val author: Person, val text: String, val date: String, val image: String?, val ratio: Float, val myReaction: String?, val likes: Int, val comments: List<Comment>, val pinned: Boolean = false)
/** Real follow graph read from the vault: account id -> count. */
data class SocialGraph(val followers: Map<String, Int> = emptyMap(), val following: Map<String, Int> = emptyMap())
data class Message(val id: String, val sender: String, val text: String, val deleted: Boolean, val edited: Boolean, val image: String?, val read: Boolean, val replyToId: String? = null, val reactions: Map<String, String> = emptyMap())
data class Thread(val id: String, val other: Person, val messages: List<Message>)
data class Snapshot(val me: Person, val posts: List<FeedPost>, val people: List<Person>, val threads: List<Thread>, val social: SocialGraph = SocialGraph())

/** Protocol-compatible v1 Telegram adapter. All JSON/crypto/network work is on IO;
 * per-device writes serialize, pin checks detect SOME races, not atomic CAS.
 * The backend is the existing shared vault, NOT Firebase or a new empty database.
 */
class NabdRepository(private val transport: VaultTransport, val namespace: String, private val sessions: Sessions, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private data class Loaded(val vault: JSONObject, val pin: Long)
    private fun pointer(chat: JSONObject): Long {
        val pin = chat.optJSONObject("pinned_message") ?: error("TELEGRAM_VAULT_MISSING")
        val doc = pin.optJSONObject("document") ?: error("TELEGRAM_FOREIGN_PIN")
        require(pin.optString("caption") == "nabd-direct-telegram-v1" && doc.optString("file_name") == "nabd-vault.json" && doc.optString("file_id").isNotBlank() && pin.optLong("message_id") > 0) { "TELEGRAM_FOREIGN_PIN" }
        require(doc.optLong("file_size", MAX_VAULT_BYTES.toLong() + 1) <= MAX_VAULT_BYTES) { "TELEGRAM_VAULT_FULL" }
        return pin.getLong("message_id")
    }
    private fun load(chat: JSONObject = transport.chat()): Loaded {
        val pin = pointer(chat)
        return Loaded(VaultProtocol.validate(transport.read(chat.getJSONObject("pinned_message").getJSONObject("document").getString("file_id")), namespace), pin)
    }
    private fun commit(loaded: Loaded) {
        val v = loaded.vault
        v.put("revision", v.getLong("revision") + 1).put("writeId", newId()).put("parent", loaded.pin)
        VaultProtocol.validate(v, namespace)
        require(pointer(transport.chat()) == loaded.pin) { "TELEGRAM_CONFLICT" }
        val next = transport.upload(v.toString())
        require(pointer(transport.chat()) == loaded.pin) { "TELEGRAM_CONFLICT" }
        transport.pin(next)
        val confirmed = load()
        require(confirmed.pin == next && confirmed.vault.getString("writeId") == v.getString("writeId")) { "TELEGRAM_CONFLICT" }
    }
    private fun actor(v: JSONObject): JSONObject {
        val parts = sessions.token?.split(':') ?: error("TELEGRAM_SESSION_REQUIRED")
        require(parts.size == 4 && parts[0] == "nabd-tg" && parts[1] == namespace && uid(parts[2]) && parts[3].matches(Regex("[a-f0-9]{64}"))) { "TELEGRAM_SESSION_REQUIRED" }
        val a = v.getJSONObject("users").optJSONObject(parts[2]) ?: error("TELEGRAM_SESSION_REQUIRED")
        require(a.getJSONObject("sessions").optLong(digest(parts[3])) > now()) { "TELEGRAM_SESSION_REQUIRED" }
        return a
    }
    private fun session(a: JSONObject): String {
        val secret = newId() + newId(); val current = a.getJSONObject("sessions")
        current.keys().asSequence().toList().filter { current.getLong(it) <= now() }.forEach(current::remove)
        if (current.length() >= 8) current.remove(current.keys().next())
        current.put(digest(secret), now() + 30L * 24 * 60 * 60 * 1000)
        return "nabd-tg:$namespace:${a.getJSONObject("profile").getString("id")}:$secret"
    }
    suspend fun login(identity: String, password: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(password.length in 1..128 && identity.isNotBlank()) { "INVALID_CREDENTIALS" }
        val initial = load(); val term = identity.trim().lowercase(Locale.ROOT)
        val account = initial.vault.getJSONObject("users").objects().find { a -> a.getJSONObject("profile").let { it.getString("email") == term || it.getString("username") == term.removePrefix("@") } } ?: error("INVALID_CREDENTIALS")
        require(MessageDigest.isEqual(passwordHash(password, account.getString("salt")).toByteArray(), account.getString("passwordHash").toByteArray())) { "INVALID_CREDENTIALS" }
        val chat = transport.chat(); val fresh = if (pointer(chat) == initial.pin) initial else load(chat)
        val current = fresh.vault.getJSONObject("users").getJSONObject(account.getJSONObject("profile").getString("id"))
        require(current.getString("passwordHash") == account.getString("passwordHash")) { "INVALID_CREDENTIALS" }
        val token = session(current); commit(fresh); sessions.token = token
    } }
    suspend fun register(name: String, email: String, password: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val normalized = email.trim().lowercase(Locale.ROOT)
        require(name.trim().length in 2..40 && normalized.length <= 254 && normalized.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "VALIDATION" }
        require(password.length in 8..128) { "TELEGRAM_PASSWORD_LENGTH" }
        val initial = load()
        require(initial.vault.getJSONObject("users").objects().none { it.getJSONObject("profile").getString("email") == normalized }) { "EMAIL_TAKEN" }
        val salt = newId(); val hash = passwordHash(password, salt)
        val chat = transport.chat(); val fresh = if (pointer(chat) == initial.pin) initial else load(chat)
        require(fresh.vault.getJSONObject("users").objects().none { it.getJSONObject("profile").getString("email") == normalized }) { "EMAIL_TAKEN" }
        val id = newId()
        val account = JSONObject().put("profile", JSONObject().put("id", id).put("name", name.trim()).put("email", normalized).put("username", "user${id.take(16)}").put("initial", name.trim().take(1)).put("color", "#E8264A").put("bio", "").put("avatarUrl", JSONObject.NULL))
            .put("salt", salt).put("passwordHash", hash).put("sessions", JSONObject())
        fresh.vault.getJSONObject("users").put(id, account)
        val official = fresh.vault.getJSONObject("users").objects().firstOrNull { it.getJSONObject("profile").getString("username") == OFFICIAL_HANDLE }
        if (official != null) account.put("following", JSONArray(listOf(official.getJSONObject("profile").getString("id"))))
        val token = session(account); commit(fresh); sessions.token = token
    } }
    private suspend fun <T> mutate(work: (JSONObject, String) -> T): T = withContext(Dispatchers.IO) { mutex.withLock {
        val token = sessions.token; val fresh = load(); val self = actor(fresh.vault).getJSONObject("profile").getString("id")
        val result = work(fresh.vault, self)
        require(token == sessions.token) { "TELEGRAM_SESSION_REQUIRED" }
        commit(fresh); result
    } }
    private fun person(v: JSONObject, id: String, viewer: String): Person {
        val p = v.getJSONObject("users").getJSONObject(id).getJSONObject("profile")
        val friend = v.getJSONArray("friendships").list().any { (it as JSONArray).strings().containsAll(listOf(id, viewer)) && id != viewer }
        val following = v.getJSONObject("users").optJSONObject(viewer)?.optJSONArray("following")?.strings()?.contains(id) == true
        return Person(id, p.getString("name"), p.getString("username"), p.optString("bio", ""), if (p.isNull("avatarUrl")) null else p.getString("avatarUrl"), friend, following)
    }
    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) { mutex.withLock {
        val v = load().vault; val self = actor(v).getJSONObject("profile").getString("id")
        val posts = v.getJSONObject("posts").objects().map { stored ->
            val p = stored.getJSONObject("post")
            FeedPost(p.getString("id"), person(v, p.getJSONObject("author").getString("id"), self), p.getString("text"), p.getString("createdAt"), p.optJSONObject("media")?.getString("uri"), p.optJSONObject("media")?.optDouble("aspectRatio", 1.0)?.toFloat() ?: 1f,
                stored.getJSONObject("votes").optString(self).ifEmpty { null }, REACTIONS.sumOf { p.getJSONObject("reactions").getInt(it) },
                p.getJSONArray("comments").objects().map { c -> Comment(c.getString("id"), person(v, c.getJSONObject("author").getString("id"), self), c.getString("text"), c.getInt("likes"), stored.getJSONObject("commentLikes").optJSONArray(c.getString("id"))?.strings()?.contains(self) == true) },
                stored.getJSONObject("post").optBoolean("pinned"))
        }.sortedWith(compareByDescending<FeedPost> { it.pinned }.thenByDescending { it.date }.thenByDescending { it.id })
        val people = v.getJSONObject("users").keys().asSequence().map { person(v, it, self) }.toList()
        val chats = v.getJSONObject("users").objects().flatMap { it.optJSONObject("chatV1")?.objects().orEmpty() }
            .filter { it.getJSONArray("members").strings().contains(self) }.map { chat ->
                Thread(chat.getString("id"), person(v, chat.getJSONArray("members").strings().first { it != self }, self), chat.getJSONArray("messages").objects().map { m -> Message(m.getString("id"), m.getString("senderId"), m.getString("text"), m.getBoolean("deleted"), m.getBoolean("edited"), m.optJSONObject("media")?.optString("uri"), m.getBoolean("read"), if (m.isNull("replyToId")) null else m.getString("replyToId"), m.getJSONObject("reactions").let { r -> r.keys().asSequence().associateWith { r.getString(it) } }) })
            }
        val accounts = v.getJSONObject("users")
        val followers = accounts.keys().asSequence().associateWith { id -> accounts.objects().count { it.optJSONArray("following")?.strings()?.contains(id) == true } }
        val following = accounts.keys().asSequence().associateWith { id -> accounts.getJSONObject(id).optJSONArray("following")?.length() ?: 0 }
        Snapshot(person(v, self, self), posts, people, chats, SocialGraph(followers, following))
    } }
    suspend fun createPost(text: String, image: String? = null, ratio: Double = 1.0) = mutate { v, self ->
        require(text.length <= 5000 && (text.isNotBlank() || image != null) && (image == null || validImage(image))) { "VALIDATION" }
        val id = newId(); val p = JSONObject().put("id", id).put("author", v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").copyJson()).put("text", text.trim()).put("privacy", "public").put("createdAt", isoDate(now())).put("comments", JSONArray()).put("commentsCount", 0).put("shares", 0).put("myReaction", JSONObject.NULL).put("reactions", JSONObject().also { o -> REACTIONS.forEach { o.put(it, 0) } })
        if (image != null) p.put("media", JSONObject().put("id", newId()).put("kind", "image").put("mime", "image/jpeg").put("uri", image).put("width", 0).put("height", 0).put("aspectRatio", ratio))
        v.getJSONObject("posts").put(id, JSONObject().put("post", p).put("votes", JSONObject()).put("commentLikes", JSONObject()))
    }
    suspend fun react(post: String, kind: String?) = mutate { v, self ->
        require(kind == null || kind in REACTIONS) { "VALIDATION" }
        val stored = v.getJSONObject("posts").getJSONObject(post); val votes = stored.getJSONObject("votes"); val counts = stored.getJSONObject("post").getJSONObject("reactions")
        val prior = votes.optString(self)
        if (prior in REACTIONS) counts.put(prior, counts.getInt(prior) - 1)
        if (kind == null) votes.remove(self) else { votes.put(self, kind); counts.put(kind, counts.getInt(kind) + 1) }
        Unit
    }
    suspend fun followUser(other: String, following: Boolean) = mutate { v, self ->
        require(other != self && v.getJSONObject("users").has(other)) { "VALIDATION" }
        val account = v.getJSONObject("users").getJSONObject(self)
        val current = (account.optJSONArray("following")?.strings() ?: emptyList()).toMutableList()
        if (following) { if (other !in current) current.add(other) } else current.remove(other)
        account.put("following", JSONArray(current))
        Unit
    }
    suspend fun setPinned(post: String, pinned: Boolean) = mutate { v, self ->
        val stored = v.getJSONObject("posts").getJSONObject(post); val author = stored.getJSONObject("post").getJSONObject("author").getString("id")
        val handle = v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").getString("username")
        require(author == self || isAdminHandle(handle)) { "FORBIDDEN" }
        stored.getJSONObject("post").put("pinned", pinned)
        Unit
    }
    suspend fun comment(post: String, text: String) = mutate { v, self ->
        require(text.isNotBlank() && text.length <= 2000) { "VALIDATION" }
        val p = v.getJSONObject("posts").getJSONObject(post).getJSONObject("post")
        p.getJSONArray("comments").put(JSONObject().put("id", newId()).put("text", text.trim()).put("author", v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").copyJson()).put("createdAt", isoDate(now())).put("likes", 0).put("likedByMe", false))
        p.put("commentsCount", p.getJSONArray("comments").length()); Unit
    }
    suspend fun changeCredentials(currentPassword: String, newPassword: String?, newEmail: String?) = mutate { v, self ->
        val account = v.getJSONObject("users").getJSONObject(self)
        require(MessageDigest.isEqual(passwordHash(currentPassword, account.getString("salt")).toByteArray(), account.getString("passwordHash").toByteArray())) { "INVALID_CREDENTIALS" }
        if (newPassword != null) {
            require(newPassword.length in 8..128) { "TELEGRAM_PASSWORD_LENGTH" }
            val salt = newId()
            account.put("salt", salt).put("passwordHash", passwordHash(newPassword, salt))
            val token = sessions.token ?: error("TELEGRAM_SESSION_REQUIRED")
            val key = digest(token.split(':')[3])
            val sessionsMap = account.getJSONObject("sessions")
            val mine = sessionsMap.optLong(key)
            account.put("sessions", JSONObject().also { if (mine > now()) it.put(key, mine) })
        }
        if (newEmail != null) {
            val normalized = newEmail.trim().lowercase(Locale.ROOT)
            require(normalized.length <= 254 && normalized.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "VALIDATION" }
            require(v.getJSONObject("users").objects().none { it.getJSONObject("profile").getString("email") == normalized }) { "EMAIL_TAKEN" }
            account.getJSONObject("profile").put("email", normalized)
        }
        Unit
    }
    suspend fun deletePost(post: String) = mutate { v, self ->
        val posts = v.getJSONObject("posts")
        require(posts.getJSONObject(post).getJSONObject("post").getJSONObject("author").getString("id") == self) { "FORBIDDEN" }
        posts.remove(post); Unit
    }
    suspend fun follow(other: String, followed: Boolean) = mutate { v, self ->
        require(other != self && v.getJSONObject("users").has(other)) { "USER_NOT_FOUND" }
        val kept = v.getJSONArray("friendships").list().filterNot { (it as JSONArray).strings().containsAll(listOf(self, other)) }
        val pairs = JSONArray(kept)
        if (followed) pairs.put(JSONArray(listOf(self, other).sorted()))
        v.put("friendships", pairs); Unit
    }
    suspend fun updateProfile(name: String, username: String, bio: String, avatar: String? = null, removeAvatar: Boolean = false) = mutate { v, self ->
        val handle = username.trim().removePrefix("@").lowercase(Locale.ROOT)
        require(name.trim().length in 2..40 && handle.matches(Regex("[a-z][a-z0-9_]{2,39}")) && bio.length <= 160) { "VALIDATION" }
        require(v.getJSONObject("users").objects().none { it.getJSONObject("profile").let { p -> p.getString("id") != self && p.getString("username") == handle } }) { "USERNAME_TAKEN" }
        val p = v.getJSONObject("users").getJSONObject(self).getJSONObject("profile")
        p.put("name", name.trim()).put("username", handle).put("bio", bio).put("initial", name.trim().take(1))
        if (removeAvatar) p.put("avatarUrl", JSONObject.NULL)
        if (avatar != null) { require(validImage(avatar)) { "VALIDATION" }; p.put("avatarUrl", avatar) }; Unit
    }
    suspend fun startThread(other: String): String = withContext(Dispatchers.IO) { mutex.withLock {
        val fresh = load(); val v = fresh.vault; val self = actor(v).getJSONObject("profile").getString("id")
        require(other != self && v.getJSONObject("users").has(other)) { "USER_NOT_FOUND" }
        val members = listOf(self, other).sorted(); val id = digest("nabd-chat-v1:${members.joinToString(":")}").take(32)
        val owner = v.getJSONObject("users").getJSONObject(members[0]); val chats = owner.optJSONObject("chatV1") ?: JSONObject().also { owner.put("chatV1", it) }
        if (!chats.has(id)) { chats.put(id, JSONObject().put("id", id).put("members", JSONArray(members)).put("createdAt", isoDate(now())).put("messages", JSONArray())); commit(fresh) }
        id
    } }
    suspend fun sendMessage(thread: String, text: String, reply: String? = null, image: String? = null, ratio: Double = 1.0) = mutate { v, self ->
        SocialActions.send(v, self, thread, text, reply, image, ratio, isoDate(now()))
    }
    suspend fun editMessage(thread: String, id: String, text: String) = mutate { v, self -> SocialActions.edit(v, self, thread, id, text) }
    suspend fun deleteMessage(thread: String, id: String) = mutate { v, self -> SocialActions.delete(v, self, thread, id) }
    suspend fun reactMessage(thread: String, id: String, kind: String?) = mutate { v, self -> SocialActions.react(v, self, thread, id, kind) }
    suspend fun deleteComment(post: String, id: String) = mutate { v, self -> SocialActions.deleteComment(v, self, post, id) }
    suspend fun likeComment(post: String, id: String) = mutate { v, self -> SocialActions.likeComment(v, self, post, id) }
    suspend fun readThread(thread: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val token = sessions.token; val fresh = load(); val self = actor(fresh.vault).getJSONObject("profile").getString("id")
        if (SocialActions.read(fresh.vault, self, thread)) {
            require(token == sessions.token) { "TELEGRAM_SESSION_REQUIRED" }; commit(fresh)
        }
    } }
    suspend fun logout() {
        mutate { v, self -> v.getJSONObject("users").getJSONObject(self).getJSONObject("sessions").remove(digest(sessions.token!!.split(':')[3])); Unit }
        sessions.token = null
    }
}
