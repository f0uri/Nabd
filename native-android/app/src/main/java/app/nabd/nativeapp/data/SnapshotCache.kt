package app.nabd.nativeapp.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last snapshot the server confirmed, kept on the device so the feed, the
 * profiles and the friends open the instant the app starts, while the real
 * refresh travels to the vault in the background.
 *
 * Only what the screens draw is stored — never a session, a password hash or a
 * salt — and post pictures are left out so the file stays a few kilobytes.
 */
object SnapshotCache {
    private const val FILE = "nabd-snapshot.json"

    fun save(context: Context, snapshot: Snapshot) {
        try {
            val file = AtomicFile(File(context.filesDir, FILE))
            val stream = file.startWrite()
            stream.write(write(snapshot).toString().toByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            try { File(context.filesDir, FILE).delete() } catch (_: Exception) { }
        }
    }

    fun load(context: Context): Pair<Snapshot, Long>? = try {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) null else {
            val json = JSONObject(file.readText())
            read(json) to json.optLong("savedAt", 0)
        }
    } catch (_: Exception) { null }

    fun clear(context: Context) { try { File(context.filesDir, FILE).delete() } catch (_: Exception) { } }

    private fun write(snapshot: Snapshot) = JSONObject()
        .put("savedAt", System.currentTimeMillis())
        .put("me", person(snapshot.me))
        .put("people", JSONArray(snapshot.people.map(::person)))
        .put("posts", JSONArray(snapshot.posts.map(::post)))
        .put("stories", JSONArray(snapshot.stories.map { group ->
            JSONObject().put("owner", person(group.owner)).put("seen", group.seen).put("items", JSONArray(group.items.map { story(it) })) }))
        .put("followers", JSONObject().apply { snapshot.social.followers.forEach { (id, count) -> put(id, count) } })
        .put("following", JSONObject().apply { snapshot.social.following.forEach { (id, count) -> put(id, count) } })
        .put("edges", JSONObject().apply { snapshot.social.edges.forEach { (id, list) -> put(id, JSONArray(list)) } })
        .put("notes", JSONArray(snapshot.notifications.map { note -> JSONObject().put("id", note.id).put("kind", note.kind)
            .put("actor", note.actor.id).put("post", note.postId ?: JSONObject.NULL).put("date", note.date).put("read", note.read) }))
        .put("devices", JSONArray(snapshot.devices.map { device -> JSONObject().put("id", device.id).put("label", device.label)
            .put("lastActive", device.lastActive).put("current", device.current) }))
        .put("settings", writeSettings(snapshot.settings))
        .put("blocked", JSONArray(snapshot.blocked.map { it.id }))
        .put("highlights", JSONObject().apply { snapshot.highlights.forEach { (id, groups) ->
            put(id, JSONArray(groups.map { group -> JSONObject().put("id", group.id).put("title", group.title)
                .put("stories", JSONArray(group.items.map { item -> JSONObject().put("id", item.id).put("text", item.text)
                    .put("image", item.image ?: JSONObject.NULL).put("at", item.date) })) })) } })

    private fun read(json: JSONObject): Snapshot {
        val me = readPerson(json.getJSONObject("me"))
        val people = json.optJSONArray("people")?.objects()?.map(::readPerson) ?: emptyList()
        val byId = (people + me).associateBy { it.id }
        val posts = json.optJSONArray("posts")?.objects()?.mapNotNull { readPost(it, byId, me) } ?: emptyList()
        val stories = json.optJSONArray("stories")?.objects()?.map { group ->
            val owner = readPerson(group.getJSONObject("owner"))
            StoryGroup(owner, group.optJSONArray("items")?.objects()?.map { item ->
                Story(item.getString("id"), owner, item.getString("text"), if (item.isNull("image")) null else item.getString("image"), item.getString("at"),
                    if (item.isNull("color")) null else item.getString("color"))
            } ?: emptyList(), group.optBoolean("seen"))
        } ?: emptyList()
        val notes = json.optJSONArray("notes")?.objects()?.mapNotNull { note ->
            val actor = byId[note.getString("actor")] ?: return@mapNotNull null
            Notification(note.getString("id"), note.getString("kind"), actor, if (note.isNull("post")) null else note.getString("post"),
                note.getString("date"), note.optBoolean("read"))
        } ?: emptyList()
        val devices = json.optJSONArray("devices")?.objects()?.map { device ->
            Device(device.getString("id"), device.getString("label"), device.optLong("lastActive"), device.optBoolean("current"))
        } ?: emptyList()
        val highlights = json.optJSONObject("highlights")?.let { map -> map.keys().asSequence().associateWith { id ->
            map.optJSONArray(id)?.objects()?.map { group -> StoryHighlight(group.getString("id"), group.getString("title"),
                group.optJSONArray("stories")?.objects()?.map { item -> HighlightItem(item.getString("id"), item.getString("text"),
                    if (item.isNull("image")) null else item.getString("image"), item.getString("at")) } ?: emptyList()) } ?: emptyList() } } ?: emptyMap()
        val blocked = json.optJSONArray("blocked")?.strings()?.mapNotNull { byId[it] } ?: emptyList()
        val followers = json.optJSONObject("followers")?.let { o -> o.keys().asSequence().associateWith { o.optInt(it) } } ?: emptyMap()
        val following = json.optJSONObject("following")?.let { o -> o.keys().asSequence().associateWith { o.optInt(it) } } ?: emptyMap()
        val edges = json.optJSONObject("edges")?.let { o -> o.keys().asSequence().associateWith { o.optJSONArray(it)?.strings() ?: emptyList() } } ?: emptyMap()
        return Snapshot(me, posts, people, emptyList(), SocialGraph(followers, following, edges), emptyList(), stories, notes,
            readSettings(json.optJSONObject("settings")), blocked, devices, emptyMap(), highlights, emptyMap())
    }

    private fun person(value: Person) = JSONObject().put("id", value.id).put("name", value.name).put("username", value.username)
        .put("bio", value.bio).put("avatar", value.avatar ?: JSONObject.NULL).put("friend", value.friend).put("following", value.following)
        .put("points", value.points).put("verified", value.verified).put("banned", value.banned).put("restricted", value.restricted)
        .put("inviteCode", value.inviteCode).put("joined", value.joined)

    private fun readPerson(value: JSONObject) = Person(value.getString("id"), value.getString("name"), value.getString("username"),
        value.optString("bio", ""), if (value.isNull("avatar")) null else value.getString("avatar"), value.optBoolean("friend"),
        value.optBoolean("following"), value.optInt("points"), value.optBoolean("verified"), value.optBoolean("banned"),
        value.optBoolean("restricted"), value.optString("inviteCode", ""), value.optString("joined", ""))

    private fun post(value: FeedPost) = JSONObject().put("id", value.id).put("author", value.author.id).put("text", value.text)
        .put("date", value.date).put("image", value.image ?: JSONObject.NULL).put("ratio", value.ratio.toDouble())
        .put("myReaction", value.myReaction ?: JSONObject.NULL).put("likes", value.likes).put("pinned", value.pinned)
        .put("privacy", value.privacy).put("date2", value.date).put("location", value.location ?: JSONObject.NULL)
        .put("shares", value.shares).put("edited", value.edited)
        .put("breakdown", JSONObject().apply { value.breakdown.forEach { (kind, count) -> put(kind, count) } })
        .put("voters", JSONObject().apply { value.voters.forEach { (voter, kind) -> put(voter, kind) } })
        .put("comments", JSONArray(value.comments.map { comment -> JSONObject().put("id", comment.id).put("author", comment.author.id)
            .put("text", comment.text).put("likes", comment.likes).put("liked", comment.likedByMe).put("date", comment.date)
            .put("replyTo", comment.replyTo ?: JSONObject.NULL).put("image", comment.image ?: JSONObject.NULL)
            .put("myReaction", comment.myReaction ?: JSONObject.NULL).put("pinned", comment.pinned).put("edited", comment.edited) }))

    private fun readPost(value: JSONObject, byId: Map<String, Person>, me: Person): FeedPost? {
        val author = byId[value.getString("author")] ?: me
        val comments = value.optJSONArray("comments")?.objects()?.map { c ->
            Comment(c.getString("id"), byId[c.getString("author")] ?: me, c.getString("text"), c.optInt("likes"), c.optBoolean("liked"),
                c.optString("date", ""), if (c.isNull("replyTo")) null else c.getString("replyTo"),
                if (c.isNull("image")) null else c.getString("image"), emptyMap(), if (c.isNull("myReaction")) null else c.getString("myReaction"),
                pinned = c.optBoolean("pinned"), edited = c.optBoolean("edited"))
        } ?: emptyList()
        val breakdown = value.optJSONObject("breakdown")?.let { o -> o.keys().asSequence().associateWith { o.optInt(it) } } ?: emptyMap()
        val voters = value.optJSONObject("voters")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } } ?: emptyMap()
        return FeedPost(value.getString("id"), author, value.getString("text"), value.optString("date", ""),
            if (value.isNull("image")) null else value.getString("image"), value.optDouble("ratio", 1.0).toFloat(),
            if (value.isNull("myReaction")) null else value.getString("myReaction"), value.optInt("likes"), comments,
            value.optBoolean("pinned"), null, false, breakdown, value.optString("privacy", "public"), voters,
            location = if (value.isNull("location")) null else value.getString("location"),
            shares = value.optInt("shares", 0), edited = value.optBoolean("edited"))
    }

    private fun story(value: Story) = JSONObject().put("id", value.id).put("text", value.text)
        .put("image", value.image ?: JSONObject.NULL).put("at", value.date).put("color", value.color ?: JSONObject.NULL)
}
