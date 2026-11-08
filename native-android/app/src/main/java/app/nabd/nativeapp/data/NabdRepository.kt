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
data class Person(val id: String, val name: String, val username: String, val bio: String, val avatar: String?, val friend: Boolean, val following: Boolean = false,
    val points: Int = 0, val verified: Boolean = false, val banned: Boolean = false, val restricted: Boolean = false, val inviteCode: String = "", val joined: String = "")
data class Comment(val id: String, val author: Person, val text: String, val likes: Int, val likedByMe: Boolean = false,
    val date: String = "", val replyTo: String? = null, val image: String? = null, val reactions: Map<String, Int> = emptyMap(), val myReaction: String? = null,
    val pending: Boolean = false, val failed: Boolean = false)
data class FeedPost(val id: String, val author: Person, val text: String, val date: String, val image: String?, val ratio: Float, val myReaction: String?, val likes: Int, val comments: List<Comment>, val pinned: Boolean = false, val boostedUntil: String? = null, val boosted: Boolean = false, val breakdown: Map<String, Int> = emptyMap(), val privacy: String = "public", val voters: Map<String, String> = emptyMap(), val pending: Boolean = false, val failed: Boolean = false)
/** Real follow graph read from the vault: account id -> count. */
data class SocialGraph(val followers: Map<String, Int> = emptyMap(), val following: Map<String, Int> = emptyMap(), val edges: Map<String, List<String>> = emptyMap())
data class Announcement(val id: String, val text: String, val date: String)
/** A story is real vault data: it expires on the server 24h after it was written. */
data class Story(val id: String, val owner: Person, val text: String, val image: String?, val date: String)
data class StoryGroup(val owner: Person, val items: List<Story>, val seen: Boolean)
/** A notification is written by the acting account into the receiving account, on the server. */
data class Notification(val id: String, val kind: String, val actor: Person, val postId: String?, val date: String, val read: Boolean)
data class Message(val id: String, val sender: String, val text: String, val deleted: Boolean, val edited: Boolean, val image: String?, val read: Boolean, val replyToId: String? = null, val reactions: Map<String, String> = emptyMap(), val date: String = "", val audio: VoiceNote? = null,
    val pending: Boolean = false, val failed: Boolean = false)
data class Thread(val id: String, val other: Person, val messages: List<Message>)
/** One real session of the account: every device that signed in is a session in the vault. */
data class Device(val id: String, val label: String, val lastActive: Long, val current: Boolean)
data class Snapshot(val me: Person, val posts: List<FeedPost>, val people: List<Person>, val threads: List<Thread>, val social: SocialGraph = SocialGraph(), val announcements: List<Announcement> = emptyList(), val stories: List<StoryGroup> = emptyList(), val notifications: List<Notification> = emptyList(),
    val settings: UserSettings = UserSettings(), val blocked: List<Person> = emptyList(), val devices: List<Device> = emptyList(), val postPrivacy: Map<String, String> = emptyMap(), val highlights: Map<String, List<StoryHighlight>> = emptyMap(), val polls: Map<String, Poll> = emptyMap(), val muted: Set<String> = emptySet(), val reels: List<ReelItem> = emptyList(),
    val reelPulses: Map<String, Int> = emptyMap(), val reelComments: Map<String, List<ReelComment>> = emptyMap(), val myReelPulses: Map<String, String> = emptyMap())

/** Protocol-compatible v1 Telegram adapter. All JSON/crypto/network work is on IO;
 * per-device writes serialize, pin checks detect SOME races, not atomic CAS.
 * The backend is the existing shared vault, NOT Firebase or a new empty database.
 */
class NabdRepository(private val transport: VaultTransport, val namespace: String, private val sessions: Sessions, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private data class Loaded(val vault: JSONObject, val pin: Long)
    /**
     * The vault is downloaded once per pinned message. A poll, or an action that
     * sees the same pin, reuses the copy that was already validated in memory
     * instead of pulling the whole file over the network again.
     */
    private var current: Loaded? = null
    private var built: Pair<Long, Snapshot>? = null
    private fun pointer(chat: JSONObject): Long {
        val pin = chat.optJSONObject("pinned_message") ?: error("TELEGRAM_VAULT_MISSING")
        val doc = pin.optJSONObject("document") ?: error("TELEGRAM_FOREIGN_PIN")
        require(pin.optString("caption") == "nabd-direct-telegram-v1" && doc.optString("file_name") == "nabd-vault.json" && doc.optString("file_id").isNotBlank() && pin.optLong("message_id") > 0) { "TELEGRAM_FOREIGN_PIN" }
        require(doc.optLong("file_size", MAX_VAULT_BYTES.toLong() + 1) <= MAX_VAULT_BYTES) { "TELEGRAM_VAULT_FULL" }
        return pin.getLong("message_id")
    }
    private fun load(chat: JSONObject = transport.chat()): Loaded {
        val pin = pointer(chat)
        current?.takeIf { it.pin == pin }?.let { return it }
        return Loaded(VaultProtocol.validate(transport.read(chat.getJSONObject("pinned_message").getJSONObject("document").getString("file_id")), namespace), pin)
            .also { current = it }
    }
    private fun commit(loaded: Loaded) {
        val v = loaded.vault
        v.put("revision", v.getLong("revision") + 1).put("writeId", newId()).put("parent", loaded.pin)
        VaultProtocol.validate(v, namespace)
        require(pointer(transport.chat()) == loaded.pin) { "TELEGRAM_CONFLICT" }
        val text = v.toString()
        val size = text.toByteArray(Charsets.UTF_8).size
        require(size <= MAX_VAULT_BYTES) { "TELEGRAM_VAULT_FULL" }
        val next = transport.upload(text)
        require(pointer(transport.chat()) == loaded.pin) { "TELEGRAM_CONFLICT" }
        transport.pin(next)
        // Confirmation without a second download: the pinned document has to be the
        // very message we just sent, carrying the file we wrote a moment ago.
        require(pointer(transport.chat()) == next) { "TELEGRAM_CONFLICT" }
        current = Loaded(v, next); built = null
    }
    private fun actor(v: JSONObject): JSONObject {
        val parts = sessions.token?.split(':') ?: error("TELEGRAM_SESSION_REQUIRED")
        require(parts.size == 4 && parts[0] == "nabd-tg" && parts[1] == namespace && uid(parts[2]) && parts[3].matches(Regex("[a-f0-9]{64}"))) { "TELEGRAM_SESSION_REQUIRED" }
        val a = v.getJSONObject("users").optJSONObject(parts[2]) ?: error("TELEGRAM_SESSION_REQUIRED")
        require(a.getJSONObject("sessions").optLong(digest(parts[3])) > now()) { "TELEGRAM_SESSION_REQUIRED" }
        require(!a.optBoolean("banned")) { "ACCOUNT_BANNED" }
        return a
    }
    private fun session(a: JSONObject): String {
        val secret = newId() + newId(); val current = a.getJSONObject("sessions")
        current.keys().asSequence().toList().filter { current.getLong(it) <= now() }.forEach(current::remove)
        if (current.length() >= 8) current.remove(current.keys().next())
        val key = digest(secret)
        current.put(key, now() + SESSION_MS)
        // The connected-devices list is the real session map, named by the device it belongs to.
        val label = listOfNotNull(android.os.Build.MANUFACTURER, android.os.Build.MODEL).map { it.trim() }.filter { it.isNotBlank() }
            .distinct().joinToString(" ").ifBlank { "جهاز نبض" }
        val devices = a.optJSONObject("devicesV1") ?: JSONObject()
        devices.put(key, JSONObject().put("label", label.take(40)).put("at", now()))
        a.put("devicesV1", devices)
        return "nabd-tg:$namespace:${a.getJSONObject("profile").getString("id")}:$secret"
    }
    suspend fun login(identity: String, password: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(password.length in 1..128 && identity.isNotBlank()) { "INVALID_CREDENTIALS" }
        val initial = load(); val term = identity.trim().lowercase(Locale.ROOT)
        val account = initial.vault.getJSONObject("users").objects().find { a -> a.getJSONObject("profile").let { it.getString("email") == term || it.getString("username") == term.removePrefix("@") } } ?: error("INVALID_CREDENTIALS")
        require(MessageDigest.isEqual(passwordHash(password, account.getString("salt")).toByteArray(), account.getString("passwordHash").toByteArray())) { "INVALID_CREDENTIALS" }
        val chat = transport.chat(); val fresh = if (pointer(chat) == initial.pin) initial else load(chat)
        current = null; built = null
        val target = fresh.vault.getJSONObject("users").getJSONObject(account.getJSONObject("profile").getString("id"))
        require(target.getString("passwordHash") == account.getString("passwordHash")) { "INVALID_CREDENTIALS" }
        require(!target.optBoolean("banned")) { "ACCOUNT_BANNED" }
        // Signing in again is how a temporarily deactivated account comes back.
        if (target.optBoolean("deactivated")) target.put("deactivated", false)
        val today = isoDate(now()).substring(0, 10)
        if (target.optString("lastDaily") != today) { target.put("lastDaily", today); target.put("points", target.optInt("points", 0) + POINTS_DAILY_LOGIN) }
        val token = session(target); commit(fresh); sessions.token = token
    } }
    suspend fun register(name: String, email: String, password: String, invite: String? = null) = withContext(Dispatchers.IO) { mutex.withLock {
        val normalized = email.trim().lowercase(Locale.ROOT)
        require(name.trim().length in 2..40 && normalized.length <= 254 && normalized.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "VALIDATION" }
        require(password.length in 8..128) { "TELEGRAM_PASSWORD_LENGTH" }
        val initial = load()
        require(initial.vault.getJSONObject("users").objects().none { it.getJSONObject("profile").getString("email") == normalized }) { "EMAIL_TAKEN" }
        val code = invite?.trim().orEmpty()
        require(code.isEmpty() || code.matches(Regex("[A-Za-z0-9]{4,32}"))) { "INVITE_NOT_FOUND" }
        val salt = newId(); val hash = passwordHash(password, salt)
        val chat = transport.chat(); val fresh = if (pointer(chat) == initial.pin) initial else load(chat)
        require(fresh.vault.getJSONObject("users").objects().none { it.getJSONObject("profile").getString("email") == normalized }) { "EMAIL_TAKEN" }
        current = null; built = null
        val id = newId()
        val inviter = if (code.isEmpty()) null else fresh.vault.getJSONObject("users").objects()
            .find { it.optString("inviteCode", "").equals(code, ignoreCase = true) }
        require(code.isEmpty() || inviter != null) { "INVITE_NOT_FOUND" }
        val account = JSONObject().put("profile", JSONObject().put("id", id).put("name", name.trim()).put("email", normalized).put("username", "user${id.take(16)}").put("initial", name.trim().take(1)).put("color", "#E8264A").put("bio", "").put("avatarUrl", JSONObject.NULL))
            .put("salt", salt).put("passwordHash", hash).put("sessions", JSONObject()).put("joinedAt", isoDate(now()))
        fresh.vault.getJSONObject("users").put(id, account)
        account.put("inviteCode", id.take(12))
        if (inviter != null) {
            account.put("invitedBy", inviter.getJSONObject("profile").getString("id"))
            inviter.put("points", inviter.optInt("points", 0) + POINTS_INVITE)
        } else account.put("invitedBy", JSONObject.NULL)
        // No auto-follow here: the new account is shown the official profile
        // right after registration and follows it with their own tap.
        val token = session(account); commit(fresh); sessions.token = token
    } }
    private suspend fun <T> mutate(work: (JSONObject, String) -> T): T = withContext(Dispatchers.IO) { mutex.withLock {
        val token = sessions.token; val fresh = load(); val self = actor(fresh.vault).getJSONObject("profile").getString("id")
        current = null; built = null
        val result = work(fresh.vault, self)
        require(token == sessions.token) { "TELEGRAM_SESSION_REQUIRED" }
        commit(fresh); result
    } }
    private fun person(v: JSONObject, id: String, viewer: String): Person {
        val a = v.getJSONObject("users").getJSONObject(id); val p = a.getJSONObject("profile")
        val friend = v.getJSONArray("friendships").list().any { (it as JSONArray).strings().containsAll(listOf(id, viewer)) && id != viewer }
        val following = v.getJSONObject("users").optJSONObject(viewer)?.optJSONArray("following")?.strings()?.contains(id) == true
        return Person(id, p.getString("name"), p.getString("username"), p.optString("bio", ""), if (p.isNull("avatarUrl")) null else p.getString("avatarUrl"), friend, following,
            a.optInt("points", 0), a.optBoolean("verified"), a.optBoolean("banned"), a.optBoolean("restricted"), a.optString("inviteCode", ""), a.optString("joinedAt", ""))
    }
    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) { mutex.withLock { build() } }
    /** Parsed once per pin, so polling that changes nothing stays one small call. */
    private fun build(): Snapshot {
        val loaded = load(); built?.takeIf { it.first == loaded.pin }?.let { return it.second }
        val v = loaded.vault; val self = actor(v).getJSONObject("profile").getString("id")
        val mine = v.getJSONObject("users").getJSONObject(self)
        val settings = readSettings(mine.optJSONObject("settingsV1"))
        // Official-account auto-engagement: the extra reactions per post and
        // the extra followers stored on the official account ("growthV1") are
        // added to the real counts below. The stored reaction totals are
        // never touched, so the vault's reaction/vote invariant stays valid.
        val officialAccount = v.getJSONObject("users").objects().find { it.getJSONObject("profile").getString("username") == OFFICIAL_HANDLE }
        val growth = officialAccount?.optJSONObject("growthV1")
        val growthPosts = growth?.optJSONObject("posts")?.let { stored ->
            stored.keys().asSequence().associateWith { id ->
                val entry = stored.optJSONObject(id) ?: JSONObject()
                REACTIONS.associateWith { kind -> entry.optInt(kind, 0) }
            }
        }.orEmpty()
        val growthFollowers = growth?.optInt("followers", 0) ?: 0
        val officialId = officialAccount?.getJSONObject("profile")?.getString("id")
        // Real per-post visibility, stored by its author: "friends" never leaves the vault.
        val privacy = v.getJSONObject("users").objects().associate { a -> a.getJSONObject("profile").getString("id") to (a.optJSONObject("postPrivacyV1") ?: JSONObject()) }
            .let { map -> v.getJSONObject("posts").keys().asSequence().associate { id ->
                val author = v.getJSONObject("posts").getJSONObject(id).getJSONObject("post").getJSONObject("author").getString("id")
                id to (map[author]?.optString(id)?.takeIf { it == "friends" } ?: "public") } }
        val visible = { author: String -> author == self || v.getJSONArray("friendships").list().any { (it as JSONArray).strings().containsAll(listOf(author, self)) } }
        val alive = { id: String -> id == self || !v.getJSONObject("users").getJSONObject(id).optBoolean("deactivated") }
        val posts = v.getJSONObject("posts").objects().map { stored ->
            val p = stored.getJSONObject("post")
            val extra = growthPosts[p.getString("id")].orEmpty()
            FeedPost(p.getString("id"), person(v, p.getJSONObject("author").getString("id"), self), p.getString("text"), p.getString("createdAt"), p.optJSONObject("media")?.getString("uri"), p.optJSONObject("media")?.optDouble("aspectRatio", 1.0)?.toFloat() ?: 1f,
                stored.getJSONObject("votes").optString(self).ifEmpty { null }, REACTIONS.sumOf { p.getJSONObject("reactions").getInt(it) + (extra[it] ?: 0) },
                p.getJSONArray("comments").objects().map { c ->
                    val pulses = stored.getJSONObject("commentLikes").optJSONArray(c.getString("id"))?.strings().orEmpty()
                    val extra = c.optJSONObject("reactions")
                    Comment(c.getString("id"), person(v, c.getJSONObject("author").getString("id"), self), c.getString("text"), c.getInt("likes"), pulses.contains(self), c.getString("createdAt"),
                        if (c.isNull("replyTo")) null else c.getString("replyTo"), if (c.isNull("image")) null else c.getString("image"),
                        REACTIONS.associateWith { if (it == "like") pulses.size else extra?.optInt(it, 0) ?: 0 },
                        if (pulses.contains(self)) "like" else c.optJSONObject("votes")?.optString(self)?.ifEmpty { null })
                },
                stored.getJSONObject("post").optBoolean("pinned"), p.optString("boostedUntil").ifEmpty { null },
                p.optString("boostedUntil").ifEmpty { null }?.let { it > isoDate(now()) } == true,
                REACTIONS.associateWith { p.getJSONObject("reactions").getInt(it) + (extra[it] ?: 0) }, privacy[p.getString("id")] ?: "public",
                stored.optJSONObject("votes")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty())
        }.filter { p -> alive(p.author.id) && (privacy[p.id] != "friends" || visible(p.author.id)) }
        .filter { p -> p.author.id !in blockedIds(mine) }
        .sortedWith(compareByDescending<FeedPost> { it.pinned }.thenByDescending { it.boosted }.thenByDescending { it.date }.thenByDescending { it.id })
        val people = v.getJSONObject("users").keys().asSequence().filter { alive(it) }.map { person(v, it, self) }.toList()
        val hidden = mine.optJSONArray("hiddenMessagesV1")?.strings()?.toSet().orEmpty()
        val chats = v.getJSONObject("users").objects().flatMap { it.optJSONObject("chatV1")?.objects().orEmpty() }
            .filter { it.getJSONArray("members").strings().contains(self) }.map { chat ->
                Thread(chat.getString("id"), person(v, chat.getJSONArray("members").strings().first { it != self }, self), chat.getJSONArray("messages").objects()
                    .filter { it.getString("id") !in hidden }.map { m ->
                    val note = chat.optJSONObject("audioV1")?.optJSONObject(m.getString("id"))
                    Message(m.getString("id"), m.getString("senderId"), m.getString("text"), m.getBoolean("deleted"), m.getBoolean("edited"), m.optJSONObject("media")?.optString("uri"), m.getBoolean("read"),
                        if (m.isNull("replyToId")) null else m.getString("replyToId"), m.getJSONObject("reactions").let { r -> r.keys().asSequence().associateWith { r.getString(it) } }, m.optString("createdAt"),
                        note?.let { VoiceNote(it.getString("uri"), it.getInt("seconds"), it.getJSONArray("bars").let { bars -> (0 until bars.length()).map { index -> bars.optInt(index, 0) } }) }) })
            }
        val accounts = v.getJSONObject("users")
        val blocked = blockedIds(mine).mapNotNull { id -> if (accounts.has(id)) person(v, id, self) else null }
        val devices = mine.getJSONObject("sessions").let { active ->
            val labels = mine.optJSONObject("devicesV1") ?: JSONObject()
            val current = digest(sessions.token?.split(':')?.getOrNull(3).orEmpty())
            active.keys().asSequence().map { key -> Device(key, labels.optJSONObject(key)?.optString("label")?.takeIf { it.isNotBlank() } ?: "جهاز آخر",
                labels.optJSONObject(key)?.optLong("at") ?: (active.getLong(key) - SESSION_MS), key == current) }
            .sortedWith(compareByDescending<Device> { it.current }.thenByDescending { it.lastActive }).toList()
        }
        val highlights = accounts.keys().asSequence().associateWith { id ->
            accounts.getJSONObject(id).optJSONArray("highlightsV1")?.objects()?.map { g ->
                StoryHighlight(g.getString("id"), g.getString("title"), g.getJSONArray("stories").objects().map { s ->
                    HighlightItem(s.getString("id"), s.getString("text"), if (s.isNull("image")) null else s.getString("image"), s.getString("at")) })
            }?.sortedByDescending { it.items.firstOrNull()?.date.orEmpty() }.orEmpty()
        }
        val polls = v.getJSONObject("posts").keys().asSequence().mapNotNull { id ->
            val author = v.getJSONObject("posts").getJSONObject(id).getJSONObject("post").getJSONObject("author").getString("id")
            val raw = accounts.getJSONObject(author).optJSONObject("pollsV1")?.optJSONObject(id) ?: return@mapNotNull null
            val options = raw.getJSONArray("options").objects().map { PollOption(it.getString("id"), it.getString("label")) }
            val votes = raw.optJSONObject("votes")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
            id to Poll(raw.getString("question"), options, votes.filterKeys { accounts.has(it) }, raw.optString("endsAt"))
        }.toMap()
        val edges = accounts.keys().asSequence().associateWith { id -> accounts.getJSONObject(id).optJSONArray("following")?.strings().orEmpty() }
        val followers = accounts.keys().asSequence().associateWith { id -> edges.values.count { id in it } + if (id == officialId) growthFollowers else 0 }
        val following = accounts.keys().asSequence().associateWith { id -> accounts.getJSONObject(id).optJSONArray("following")?.length() ?: 0 }
        val official = accounts.objects().find { it.getJSONObject("profile").getString("username") == OFFICIAL_HANDLE }
        val announcements = official?.optJSONArray("announcements")?.objects()
            ?.map { Announcement(it.getString("id"), it.getString("text"), it.getString("at")) }?.sortedByDescending { it.date }.orEmpty()
        val stamp = isoDate(now())
        val groups = accounts.objects().filter { a ->
            val owner = a.getJSONObject("profile").getString("id")
            alive(owner) && (owner == self || readSettings(a.optJSONObject("settingsV1")).stories != "friends" || visible(owner))
        }.mapNotNull { a ->
            val items = a.optJSONArray("stories")?.objects()?.filter { it.getString("expiresAt") > stamp }
                ?.map { s -> Story(s.getString("id"), person(v, a.getJSONObject("profile").getString("id"), self), s.getString("text"), if (s.isNull("image")) null else s.getString("image"), s.getString("at")) }
                .orEmpty()
            if (items.isEmpty()) null else person(v, a.getJSONObject("profile").getString("id"), self) to items.sortedBy { it.date }
        }
        val ordered = groups.sortedWith(compareByDescending<Pair<Person, List<Story>>> { it.first.id == self }
            .thenByDescending { isAdminHandle(it.first.username) }.thenBy { it.first.name })
        val me = accounts.getJSONObject(self)
        val notes = me.optJSONArray("notifications")?.objects()
            ?.map { n -> Notification(n.getString("id"), n.getString("kind"), person(v, n.getJSONObject("actor").getString("id"), self),
                if (n.isNull("post")) null else n.getString("post"), n.getString("at"), n.optBoolean("read")) }
            ?.filter { n -> !settings.quiet && settings.notify[n.kind] != false && n.actor.id !in blockedIds(me) }
            ?.sortedByDescending { it.date }.orEmpty()
        val reelPulses = mutableMapOf<String, Int>()
        val reelComments = mutableMapOf<String, MutableList<ReelComment>>()
        accounts.keys().asSequence().forEach { id ->
            val account = accounts.getJSONObject(id)
            account.optJSONObject("reelVotesV1")?.let { votes -> votes.keys().asSequence().forEach { key ->
                if (votes.optString(key) in REACTIONS) reelPulses[key] = (reelPulses[key] ?: 0) + 1 } }
            account.optJSONObject("reelCommentsV1")?.let { all -> all.keys().asSequence().forEach { key ->
                val list = reelComments.getOrPut(key) { mutableListOf() }
                all.optJSONArray(key)?.objects()?.forEach { c ->
                    list += ReelComment(c.getString("id"), person(v, id, self), c.getString("text"), c.getString("date")) } } }
        }
        val myReelPulses = mine.optJSONObject("reelVotesV1")?.let { o -> o.keys().asSequence().mapNotNull { key ->
            o.optString(key).takeIf { it in REACTIONS }?.let { key to it } }.toMap() } ?: emptyMap()
        val reels = accounts.keys().asSequence().filter { alive(it) }.flatMap { id ->
            val account = accounts.getJSONObject(id)
            val published = account.optJSONObject("reelsV1")
            if (published == null) emptySequence() else published.keys().asSequence().mapNotNull { rid ->
                val reel = published.optJSONObject(rid) ?: return@mapNotNull null
                val key = publishedReelKey(rid)
                ReelItem(key, reel.optString("source", REEL_SOURCE_LINK), reel.optString("ref"), reel.optString("video"),
                    reel.optString("cover"), reel.optString("author").takeIf { it.isNotBlank() } ?: "حساب نبض",
                    person(v, id, self), reel.optString("caption"), reel.optString("date"),
                    reelPulses[key] ?: 0, myReelPulses[key], (reelComments[key] ?: emptyList()).sortedBy { it.date })
            }
        }.filter { reel -> reel.author?.id !in blockedIds(mine) }.sortedByDescending { it.date }.toList()
        return Snapshot(person(v, self, self), posts, people, chats, SocialGraph(followers, following, edges), announcements,
            ordered.map { (owner, items) -> StoryGroup(owner, items, false) }, notes, settings, blocked, devices, privacy, highlights, polls,
            mine.optJSONArray("mutedV1")?.strings()?.toSet().orEmpty(), reels, reelPulses, reelComments.mapValues { (_, list) -> list.sortedBy { it.date } },
            myReelPulses).also { built = loaded.pin to it }
    }
    /** The secret of the session this device is using right now. */
    private fun currentSecret(): String = sessions.token?.split(':')?.getOrNull(3) ?: error("TELEGRAM_SESSION_REQUIRED")

    private fun blockedIds(account: JSONObject): Set<String> =
        account.optJSONArray("blockedV1")?.strings()?.filter(::uid)?.toSet().orEmpty()

    /** Preferences live on the server, inside the account, so they follow it anywhere. */
    suspend fun saveSettings(next: UserSettings) = mutate { v, self -> v.getJSONObject("users").getJSONObject(self).put("settingsV1", writeSettings(next)) }

    suspend fun setBlocked(id: String, blocked: Boolean) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        require(id != self && v.getJSONObject("users").has(id)) { "USER_NOT_FOUND" }
        val list = me.optJSONArray("blockedV1")?.strings()?.toMutableList() ?: mutableListOf()
        if (blocked) { if (id !in list) list += id } else list -= id
        me.put("blockedV1", JSONArray(list))
    }

    /** Ends one real session: that device is signed out everywhere it was used. */
    suspend fun logoutDevice(id: String) = mutate { v, self -> v.getJSONObject("users").getJSONObject(self).getJSONObject("sessions").remove(id) }

    suspend fun logoutOtherDevices() = mutate { v, self ->
        val active = v.getJSONObject("users").getJSONObject(self).getJSONObject("sessions")
        val keep = digest(currentSecret())
        active.keys().asSequence().toList().filter { it != keep }.forEach(active::remove)
    }

    /** Signs this session out for good: it is the one action that ends the session everywhere. */
    suspend fun revokeSession() = mutate { v, self ->
        v.getJSONObject("users").getJSONObject(self).getJSONObject("sessions").remove(digest(currentSecret()))
    }

    /** Temporary deactivation: the account disappears for everyone until it signs in again. */
    /**
     * Reels are links, never files: the vault stores who published what and what
     * the community said about it, while the video keeps streaming from the
     * service that owns it. Nothing is uploaded to a server of ours.
     */
    suspend fun publishReel(source: String, ref: String, video: String, cover: String, caption: String): String = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        val id = newId()
        val published = me.optJSONObject("reelsV1") ?: JSONObject().also { me.put("reelsV1", it) }
        published.put(id, JSONObject().put("source", source).put("ref", ref).put("video", video).put("cover", cover)
            .put("author", me.getJSONObject("profile").getString("name")).put("caption", caption).put("date", isoDate(now())))
        id
    }
    suspend fun deleteReel(id: String) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        me.optJSONObject("reelsV1")?.remove(id)
        me.optJSONObject("reelVotesV1")?.remove(publishedReelKey(id))
        me.optJSONObject("reelCommentsV1")?.remove(publishedReelKey(id))
    }
    suspend fun pulseReel(key: String, kind: String?) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        val votes = me.optJSONObject("reelVotesV1") ?: JSONObject().also { me.put("reelVotesV1", it) }
        if (kind == null) votes.remove(key) else votes.put(key, kind)
    }
    suspend fun commentReel(key: String, text: String) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        val all = me.optJSONObject("reelCommentsV1") ?: JSONObject().also { me.put("reelCommentsV1", it) }
        val list = all.optJSONArray(key) ?: JSONArray().also { all.put(key, it) }
        list.put(JSONObject().put("id", newId()).put("text", text).put("date", isoDate(now())))
    }
    suspend fun deleteReelComment(key: String, id: String) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        val all = me.optJSONObject("reelCommentsV1") ?: return@mutate
        val list = all.optJSONArray(key) ?: return@mutate
        all.put(key, JSONArray(list.objects().filter { it.getString("id") != id }))
    }

    /** Muting is mine only: the vault keeps the list of threads I silenced. */
    suspend fun setMuted(thread: String, muted: Boolean) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        val list = me.optJSONArray("mutedV1")?.strings()?.toMutableList() ?: mutableListOf()
        if (muted) { if (thread !in list) list += thread } else list -= thread
        me.put("mutedV1", JSONArray(list))
    }
    /** A message I removed on my side only; the other side keeps its own copy. */
    suspend fun deleteMessageForMe(thread: String, message: String) = mutate { v, self -> hide(v, self, listOf(message)) }
    /** Emptying the thread hides every message from my side; the vault keeps theirs. */
    suspend fun clearThread(thread: String) = mutate { v, self ->
        hide(v, self, SocialActions.chat(v, self, thread).getJSONArray("messages").objects().map { it.getString("id") })
    }
    private fun hide(v: JSONObject, self: String, ids: List<String>) {
        val me = v.getJSONObject("users").getJSONObject(self)
        val hidden = me.optJSONArray("hiddenMessagesV1")?.strings()?.toMutableList() ?: mutableListOf()
        me.put("hiddenMessagesV1", JSONArray((hidden + ids).distinct().filter(::uid)))
    }

    suspend fun setDeactivated(value: Boolean) = mutate { v, self -> v.getJSONObject("users").getJSONObject(self).put("deactivated", value) }

    /** Deactivation hides the account from everyone, and it needs the real password. */
    suspend fun deactivate(password: String) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        require(MessageDigest.isEqual(passwordHash(password, me.getString("salt")).toByteArray(), me.getString("passwordHash").toByteArray())) { "INVALID_CREDENTIALS" }
        me.put("deactivated", true)
    }

    /**
     * Permanent deletion, after the real password. Every trace the vault keeps of
     * the account goes with it, and no dangling reference is left behind.
     */
    suspend fun deleteAccount(password: String) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        require(MessageDigest.isEqual(passwordHash(password, me.getString("salt")).toByteArray(), me.getString("passwordHash").toByteArray())) { "INVALID_CREDENTIALS" }
        val users = v.getJSONObject("users"); val posts = v.getJSONObject("posts")
        posts.keys().asSequence().toList().forEach { id ->
            val stored = posts.getJSONObject(id); val p = stored.getJSONObject("post")
            if (p.getJSONObject("author").getString("id") == self) { posts.remove(id); return@forEach }
            val votes = stored.getJSONObject("votes"); val likes = stored.getJSONObject("commentLikes")
            if (votes.has(self)) { val kind = votes.getString(self); votes.remove(self)
                val reactions = p.getJSONObject("reactions"); reactions.put(kind, (reactions.getInt(kind) - 1).coerceAtLeast(0)) }
            val kept = p.getJSONArray("comments").objects().filter { c -> c.getJSONObject("author").getString("id") != self }
            kept.forEach { c ->
                val cid = c.getString("id"); val pulses = likes.optJSONArray(cid)?.strings().orEmpty().toMutableList()
                if (self in pulses) pulses.remove(self)
                c.optJSONObject("votes")?.remove(self)
                c.optJSONObject("reactions")?.let { r -> r.keys().asSequence().toList().forEach { k -> if (r.getInt(k) <= 0) r.remove(k) } }
                val counts = c.optJSONObject("reactions")
                val extra = counts?.keys()?.asSequence()?.sumOf { counts.getInt(it) } ?: 0
                if (pulses.isEmpty()) likes.remove(cid) else likes.put(cid, JSONArray(pulses))
                c.put("likes", pulses.size + extra)
            }
            p.put("comments", JSONArray(kept)).put("commentsCount", kept.size)
            likes.keys().asSequence().toList().filter { it !in kept.map { c -> c.getString("id") } }.forEach(likes::remove)
        }
        val pairs = v.getJSONArray("friendships").list().filterNot { (it as JSONArray).strings().contains(self) }
        v.put("friendships", JSONArray(pairs))
        users.keys().asSequence().toList().forEach { id ->
            val other = users.getJSONObject(id)
            other.optJSONArray("following")?.let { list -> other.put("following", JSONArray(list.strings().filter { it != self })) }
            other.optJSONArray("blockedV1")?.let { list -> other.put("blockedV1", JSONArray(list.strings().filter { it != self })) }
            other.optJSONArray("notifications")?.let { list -> other.put("notifications", JSONArray(list.objects().filter { it.getJSONObject("actor").getString("id") != self })) }
            if (other.optString("invitedBy") == self) other.put("invitedBy", JSONObject.NULL)
            other.optJSONObject("chatV1")?.let { chats -> chats.keys().asSequence().toList()
                .filter { chats.getJSONObject(it).getJSONArray("members").strings().contains(self) }.forEach(chats::remove) }
        }
        users.remove(self)
    }

    suspend fun createPost(text: String, image: String? = null, ratio: Double = 1.0, privacy: String = "public") = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self); require(!me.optBoolean("restricted")) { "ACCOUNT_RESTRICTED" }
        require(text.length <= 5000 && (text.isNotBlank() || image != null) && (image == null || validImage(image))) { "VALIDATION" }
        require(privacy in VISIBILITY_CHOICES) { "VALIDATION" }
        val id = newId(); val p = JSONObject().put("id", id).put("author", v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").copyJson()).put("text", text.trim()).put("privacy", "public").put("createdAt", isoDate(now())).put("comments", JSONArray()).put("commentsCount", 0).put("shares", 0).put("myReaction", JSONObject.NULL).put("reactions", JSONObject().also { o -> REACTIONS.forEach { o.put(it, 0) } })
        if (image != null) p.put("media", JSONObject().put("id", newId()).put("kind", "image").put("mime", "image/jpeg").put("uri", image).put("width", 0).put("height", 0).put("aspectRatio", ratio))
        me.put("points", me.optInt("points", 0) + POINTS_POST)
        v.getJSONObject("posts").put(id, JSONObject().put("post", p).put("votes", JSONObject()).put("commentLikes", JSONObject()))
        // "عام" stays in the post for the legacy reader; the real audience is ours.
        if (privacy == "friends") me.put("postPrivacyV1", (me.optJSONObject("postPrivacyV1") ?: JSONObject()).put(id, "friends"))
    }

    /** A poll is published as a real post carrying its question; the options travel with it. */
    suspend fun createPollPost(question: String, options: List<String>, days: Int, privacy: String = "public") = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self); require(!me.optBoolean("restricted")) { "ACCOUNT_RESTRICTED" }
        val clean = options.map { it.trim() }.filter { it.isNotBlank() }
        require(question.trim().length in 1..500 && clean.size in 2..4 && clean.all { it.length <= 60 } && days in 1..7) { "VALIDATION" }
        require(privacy in VISIBILITY_CHOICES) { "VALIDATION" }
        val id = newId(); val p = JSONObject().put("id", id).put("author", v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").copyJson()).put("text", question.trim()).put("privacy", "public").put("createdAt", isoDate(now())).put("comments", JSONArray()).put("commentsCount", 0).put("shares", 0).put("myReaction", JSONObject.NULL).put("reactions", JSONObject().also { o -> REACTIONS.forEach { o.put(it, 0) } })
        me.put("points", me.optInt("points", 0) + POINTS_POST)
        v.getJSONObject("posts").put(id, JSONObject().put("post", p).put("votes", JSONObject()).put("commentLikes", JSONObject()))
        val poll = JSONObject().put("question", question.trim()).put("endsAt", isoDate(now() + days * 24L * 60 * 60 * 1000))
            .put("options", JSONArray(clean.map { JSONObject().put("id", newId()).put("label", it) })).put("votes", JSONObject())
        me.put("pollsV1", (me.optJSONObject("pollsV1") ?: JSONObject()).put(id, poll))
        if (privacy == "friends") me.put("postPrivacyV1", (me.optJSONObject("postPrivacyV1") ?: JSONObject()).put(id, "friends"))
    }

    /** One real vote per account: the vault simply refuses a second one. */
    suspend fun votePoll(post: String, option: String) = mutate { v, self ->
        val stored = v.getJSONObject("posts").optJSONObject(post) ?: error("VALIDATION")
        val author = stored.getJSONObject("post").getJSONObject("author").getString("id")
        val poll = v.getJSONObject("users").getJSONObject(author).optJSONObject("pollsV1")?.optJSONObject(post) ?: error("VALIDATION")
        require(poll.getJSONArray("options").objects().any { it.getString("id") == option }) { "VALIDATION" }
        if (poll.optString("endsAt").isNotBlank() && runCatching { java.time.Instant.parse(poll.getString("endsAt")) }.getOrNull()?.isBefore(java.time.Instant.now()) == true) error("POLL_CLOSED")
        val votes = poll.optJSONObject("votes") ?: JSONObject()
        require(!votes.has(self)) { "POLL_ALREADY_VOTED" }
        votes.put(self, option); poll.put("votes", votes)
    }

    /** Keeps ended stories on the profile: the copy carries its image past the 24 hours. */
    suspend fun createHighlight(title: String, storyIds: List<String>) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        require(title.trim().length in 1..40 && storyIds.isNotEmpty()) { "VALIDATION" }
        val mine = me.optJSONArray("stories")?.objects().orEmpty()
        val chosen = storyIds.mapNotNull { id -> mine.find { it.getString("id") == id } }
        require(chosen.isNotEmpty()) { "VALIDATION" }
        val group = JSONObject().put("id", newId()).put("title", title.trim())
            .put("stories", JSONArray(chosen.map { s -> JSONObject().put("id", newId()).put("text", s.getString("text"))
                .put("image", if (s.isNull("image")) JSONObject.NULL else s.getString("image")).put("at", s.getString("at")) }))
        me.put("highlightsV1", (me.optJSONArray("highlightsV1") ?: JSONArray()).put(group))
    }

    suspend fun removeHighlight(id: String) = mutate { v, self ->
        val me = v.getJSONObject("users").getJSONObject(self)
        me.put("highlightsV1", JSONArray((me.optJSONArray("highlightsV1")?.objects() ?: emptyList()).filter { it.getString("id") != id }))
    }

    /** The audio stays in the vault next to its message; the message text stays readable for old clients. */
    suspend fun sendVoice(thread: String, uri: String, seconds: Int, bars: List<Int>) = mutate { v, self ->
        require(validAudio(uri) && seconds in 1..MAX_VOICE_SECONDS && bars.size in 1..MAX_VOICE_BARS) { "VALIDATION" }
        require(bars.all { it in 0..100 }) { "VALIDATION" }
        val chat = SocialActions.chat(v, self, thread)
        val id = newId()
        chat.getJSONArray("messages").put(JSONObject().put("id", id).put("senderId", self).put("text", VOICE_LABEL).put("createdAt", isoDate(now()))
            .put("read", false).put("edited", false).put("deleted", false).put("replyToId", JSONObject.NULL)
            .put("reactions", JSONObject()).put("media", JSONObject.NULL))
        val audio = chat.optJSONObject("audioV1") ?: JSONObject()
        audio.put(id, JSONObject().put("uri", uri).put("seconds", seconds).put("bars", JSONArray(bars.take(MAX_VOICE_BARS))))
        chat.put("audioV1", audio)
    }
    suspend fun react(post: String, kind: String?) = mutate { v, self ->
        require(kind == null || kind in REACTIONS) { "VALIDATION" }
        val stored = v.getJSONObject("posts").getJSONObject(post); val votes = stored.getJSONObject("votes"); val counts = stored.getJSONObject("post").getJSONObject("reactions")
        val prior = votes.optString(self)
        if (prior in REACTIONS) counts.put(prior, counts.getInt(prior) - 1)
        if (kind == null) votes.remove(self) else { votes.put(self, kind); counts.put(kind, counts.getInt(kind) + 1) }
        if (kind != null) notify(v, self, stored.getJSONObject("post").getJSONObject("author").getString("id"), "pulse", post)
        Unit
    }
    suspend fun followUser(other: String, following: Boolean) = mutate { v, self ->
        require(other != self && v.getJSONObject("users").has(other)) { "VALIDATION" }
        val account = v.getJSONObject("users").getJSONObject(self)
        val current = (account.optJSONArray("following")?.strings() ?: emptyList()).toMutableList()
        if (following) { if (other !in current) current.add(other) } else current.remove(other)
        account.put("following", JSONArray(current))
        // Follow == friend: the friendship pair is created the moment the
        // follow lands, and removed the moment it is undone. No friend
        // request, no pending state, no acceptance step.
        val kept = v.getJSONArray("friendships").list().filterNot { (it as JSONArray).strings().containsAll(listOf(self, other)) }
        val pairs = JSONArray(kept)
        if (following) pairs.put(JSONArray(listOf(self, other).sorted()))
        v.put("friendships", pairs)
        if (following) notify(v, self, other, "follow", null)
        Unit
    }
    /**
     * Official-account auto-engagement. Any signed-in user running the app
     * nudges the official account's numbers forward: 20 reactions spread
     * over the reaction types of all official posts, old and new, and a few
     * extra followers. Throttled to one vault write per minute; when nothing
     * is due no write happens at all, so a refresh or poll stays cheap. The
     * growth is stored on the official account ("growthV1") and applied when
     * the snapshot is built, so it survives app restarts.
     */
    suspend fun bumpOfficialEngagement(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val token = sessions.token ?: return@withLock false
        val fresh = load()
        actor(fresh.vault)
        val v = fresh.vault
        val official = v.getJSONObject("users").objects().find { it.getJSONObject("profile").getString("username") == OFFICIAL_HANDLE } ?: return@withLock false
        val officialId = official.getJSONObject("profile").getString("id")
        val growth = official.optJSONObject("growthV1") ?: JSONObject()
        val lastAt = growth.optLong("lastAt", 0)
        val nowMs = now()
        if (nowMs - lastAt < GROWTH_MIN_INTERVAL_MS) return@withLock false
        current = null; built = null
        val random = kotlin.random.Random(nowMs)
        val kept = JSONObject()
        val posts = growth.optJSONObject("posts") ?: JSONObject()
        posts.keys().asSequence().forEach { id ->
            val entry = posts.optJSONObject(id) ?: return@forEach
            if (v.getJSONObject("posts").optJSONObject(id) == null) return@forEach
            kept.put(id, entry)
        }
        val officialPostIds = v.getJSONObject("posts").keys().asSequence().filter { id ->
            v.getJSONObject("posts").getJSONObject(id).getJSONObject("post").getJSONObject("author").getString("id") == officialId
        }.toList()
        repeat(GROWTH_REACTIONS_PER_BUMP) {
            if (officialPostIds.isEmpty()) return@repeat
            val id = officialPostIds.random(random)
            val entry = kept.optJSONObject(id) ?: JSONObject().also { kept.put(id, it) }
            val kind = REACTIONS.random(random)
            entry.put(kind, entry.optInt(kind, 0) + 1)
        }
        growth.put("lastAt", nowMs)
        growth.put("followers", growth.optInt("followers", 0) + random.nextInt(1, 4))
        growth.put("posts", kept)
        official.put("growthV1", growth)
        require(token == sessions.token) { "TELEGRAM_SESSION_REQUIRED" }
        commit(fresh)
        true
    } }
    suspend fun setPinned(post: String, pinned: Boolean) = mutate { v, self ->
        val stored = v.getJSONObject("posts").getJSONObject(post); val author = stored.getJSONObject("post").getJSONObject("author").getString("id")
        val handle = v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").getString("username")
        require(author == self || isAdminHandle(handle)) { "FORBIDDEN" }
        stored.getJSONObject("post").put("pinned", pinned)
        Unit
    }
    suspend fun addStory(text: String, image: String? = null) = mutate { v, self ->
        val clean = text.trim()
        require(clean.isNotBlank() || image != null) { "VALIDATION" }
        require(clean.length <= 2000) { "VALIDATION" }
        require(image == null || validImage(image)) { "VALIDATION" }
        val account = v.getJSONObject("users").getJSONObject(self)
        val now = this.now()
        val kept = (account.optJSONArray("stories")?.objects() ?: emptyList()).filter { it.getString("expiresAt") > isoDate(now) }.take(19)
        val items = JSONArray()
        kept.forEach { items.put(it) }
        items.put(JSONObject().put("id", newId()).put("text", clean).put("at", isoDate(now)).put("expiresAt", isoDate(now + STORY_TTL_MS)).put("image", image ?: JSONObject.NULL))
        account.put("stories", items)
        Unit
    }
    suspend fun removeStory(id: String) = mutate { v, self ->
        val account = v.getJSONObject("users").getJSONObject(self)
        val now = isoDate(this.now())
        val kept = (account.optJSONArray("stories")?.objects() ?: emptyList()).filter { it.getString("id") != id && it.getString("expiresAt") > now }
        val items = JSONArray(); kept.forEach { items.put(it) }
        account.put("stories", items)
        Unit
    }
    suspend fun boostPost(post: String) = mutate { v, self ->
        val stored = v.getJSONObject("posts").getJSONObject(post)
        require(stored.getJSONObject("post").getJSONObject("author").getString("id") == self) { "FORBIDDEN" }
        val me = v.getJSONObject("users").getJSONObject(self)
        require(me.optInt("points", 0) >= BOOST_COST) { "NOT_ENOUGH_POINTS" }
        me.put("points", me.optInt("points", 0) - BOOST_COST)
        stored.getJSONObject("post").put("boostedUntil", isoDate(now() + BOOST_MS))
        Unit
    }
    /** Appends a real notification to another account, replacing an identical unread one. */
    private fun notify(v: JSONObject, self: String, target: String, kind: String, post: String?) {
        if (target == self) return
        val account = v.getJSONObject("users").optJSONObject(target) ?: return
        val list = account.optJSONArray("notifications") ?: JSONArray()
        val kept = list.objects().filter { !(it.getString("kind") == kind && it.getJSONObject("actor").getString("id") == self && (if (it.isNull("post")) null else it.getString("post")) == post) }
        val items = JSONArray(); kept.take(49).forEach { items.put(it) }
        items.put(JSONObject().put("id", newId()).put("kind", kind).put("actor", v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").copyJson())
            .put("post", post ?: JSONObject.NULL).put("at", isoDate(now())).put("read", false))
        account.put("notifications", items)
    }
    suspend fun markNotificationsRead() = mutate { v, self ->
        v.getJSONObject("users").getJSONObject(self).optJSONArray("notifications")?.objects()?.forEach { it.put("read", true) }
        Unit
    }
    private fun admin(v: JSONObject, self: String) {
        require(isAdminHandle(v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").getString("username"))) { "FORBIDDEN" }
    }
    suspend fun setVerified(target: String, verified: Boolean) = mutate { v, self ->
        admin(v, self); v.getJSONObject("users").getJSONObject(target).put("verified", verified); Unit
    }
    suspend fun setRestriction(target: String, banned: Boolean?, restricted: Boolean?) = mutate { v, self ->
        admin(v, self); require(target != self) { "FORBIDDEN" }
        val account = v.getJSONObject("users").getJSONObject(target)
        if (banned != null) account.put("banned", banned)
        if (restricted != null) account.put("restricted", restricted)
        Unit
    }
    suspend fun announce(text: String) = mutate { v, self ->
        admin(v, self)
        val clean = text.trim(); require(clean.length in 1..280) { "VALIDATION" }
        val account = v.getJSONObject("users").getJSONObject(self)
        val list = account.optJSONArray("announcements") ?: JSONArray()
        list.put(JSONObject().put("id", newId()).put("text", clean).put("at", isoDate(now())))
        while (list.length() > 20) list.remove(0)
        account.put("announcements", list)
        Unit
    }
    suspend fun comment(post: String, text: String, image: String? = null, replyTo: String? = null) = mutate { v, self ->
        require(!v.getJSONObject("users").getJSONObject(self).optBoolean("restricted")) { "ACCOUNT_RESTRICTED" }
        val clean = text.trim()
        require((clean.isNotBlank() || image != null) && clean.length <= 2000) { "VALIDATION" }
        require(image == null || validImage(image)) { "VALIDATION" }
        val p = v.getJSONObject("posts").getJSONObject(post).getJSONObject("post")
        val comments = p.getJSONArray("comments")
        if (replyTo != null) require((0 until comments.length()).any { comments.getJSONObject(it).getString("id") == replyTo }) { "VALIDATION" }
        comments.put(JSONObject().put("id", newId()).put("text", clean).put("author", v.getJSONObject("users").getJSONObject(self).getJSONObject("profile").copyJson())
            .put("createdAt", isoDate(now())).put("likes", 0).put("likedByMe", false)
            .put("replyTo", replyTo ?: JSONObject.NULL).put("image", image ?: JSONObject.NULL))
        p.put("commentsCount", comments.length())
        val parent = if (replyTo == null) null else comments.objects().find { it.getString("id") == replyTo }
        val target = if (parent != null) parent.getJSONObject("author").getString("id") else p.getJSONObject("author").getString("id")
        notify(v, self, target, if (parent != null) "reply" else "comment", post)
        Unit
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
    suspend fun reactComment(post: String, id: String, kind: String) = mutate { v, self -> SocialActions.reactComment(v, self, post, id, kind) }
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
