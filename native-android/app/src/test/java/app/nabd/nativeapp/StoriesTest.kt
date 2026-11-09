package app.nabd.nativeapp

import app.nabd.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

private const val NS = "abcdef012345abcdef012345"
private val A = "a".repeat(32)
private val B = "b".repeat(32)
private const val NOW = 2000000000000L
private const val HOUR = 3600000L

private class StorySession(override var token: String? = "nabd-tg:$NS:$A:${"c".repeat(64)}") : Sessions

private fun account(id: String, name: String) = JSONObject()
    .put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", "${name}_user")
        .put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
    .put("salt", "000102030405060708090a0b0c0d0e0f")
    .put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6")
    .put("sessions", JSONObject().put(digest("c".repeat(64)), NOW + 100 * 24 * HOUR))

private fun fixture() = JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", NS).put("revision", 1)
    .put("parent", JSONObject.NULL).put("writeId", "d".repeat(32))
    .put("users", JSONObject().put(A, account(A, "alice")).put(B, account(B, "bob"))).put("posts", JSONObject()).put("friendships", JSONArray())

private class StoryTransport(initial: JSONObject) : VaultTransport {
    var value = initial; var pointer = 1L
    private var uploaded: JSONObject? = null
    override fun chat(): JSONObject = JSONObject().put("pinned_message", JSONObject().put("message_id", pointer)
        .put("caption", "nabd-direct-telegram-v1").put("document", JSONObject().put("file_id", "$pointer").put("file_name", "nabd-vault.json").put("file_size", 10)))
    override fun read(fileId: String) = value.copyJson()
    override fun upload(json: String): Long { uploaded = JSONObject(json); return pointer + 1 }
    override fun pin(messageId: Long) { pointer = messageId; value = uploaded!! }
    override fun uploadMedia(bytes: ByteArray, name: String, mime: String): String = "test-media-${bytes.size}"
    override fun fetchMedia(fileId: String): ByteArray? = null
}

/** Stories are real vault records shared with everyone, and they expire on the server. */
class StoriesTest {
    @Test fun publishedStoryIsVisibleToAnotherAccount() = runBlocking {
        val t = StoryTransport(fixture())
        NabdRepository(t, NS, StorySession()) { NOW }.addStory("قصة أليس")
        val mine = NabdRepository(t, NS, StorySession()) { NOW }.snapshot().stories
        val theirs = NabdRepository(t, NS, StorySession("nabd-tg:$NS:$B:${"c".repeat(64)}")) { NOW }.snapshot().stories
        assertEquals(1, mine.size)
        assertEquals(1, theirs.size)
        assertEquals(A, theirs.first().owner.id)
        assertEquals("قصة أليس", theirs.first().items.first().text)
        assertEquals(isoDate(NOW + 24 * HOUR), t.value.getJSONObject("users").getJSONObject(A).getJSONArray("stories").getJSONObject(0).getString("expiresAt"))
    }
    @Test fun storiesDisappearAfterTheirRealExpiry() = runBlocking {
        val t = StoryTransport(fixture())
        NabdRepository(t, NS, StorySession()) { NOW }.addStory("قصة")
        assertTrue(NabdRepository(t, NS, StorySession()) { NOW + 23 * HOUR }.snapshot().stories.isNotEmpty())
        assertTrue(NabdRepository(t, NS, StorySession()) { NOW + 25 * HOUR }.snapshot().stories.isEmpty())
    }
    @Test fun removingAStoryDropsOnlyThatOne() = runBlocking {
        val t = StoryTransport(fixture()); val repo = NabdRepository(t, NS, StorySession()) { NOW }
        repo.addStory("الأولى"); repo.addStory("الثانية")
        val first = repo.snapshot().stories.first { it.owner.id == A }.items.first().id
        repo.removeStory(first)
        val items = repo.snapshot().stories.first { it.owner.id == A }.items
        assertEquals(1, items.size)
        assertEquals("الثانية", items.first().text)
    }
    @Test fun emptyStoryAndBadImageAreRejected() = runBlocking {
        val t = StoryTransport(fixture()); val repo = NabdRepository(t, NS, StorySession()) { NOW }
        assertTrue(runCatching { repo.addStory("   ") }.isFailure)
        assertTrue(runCatching { repo.addStory("نص", "not-an-image") }.isFailure)
        assertTrue(repo.snapshot().stories.isEmpty())
    }
    @Test fun myStoryGroupComesFirstAndAdminSecond() = runBlocking {
        val t = StoryTransport(fixture())
        val admin = account("c".repeat(32), "owner"); admin.getJSONObject("profile").put("username", "f0uri")
        t.value.getJSONObject("users").put("c".repeat(32), admin)
        val repo = NabdRepository(t, NS, StorySession()) { NOW }
        repo.addStory("قصتي")
        NabdRepository(t, NS, StorySession("nabd-tg:$NS:${"c".repeat(32)}:${"c".repeat(64)}")) { NOW }.addStory("إعلان")
        val owners = repo.snapshot().stories.map { it.owner.id }
        assertEquals(listOf(A, "c".repeat(32)), owners)
    }
}
