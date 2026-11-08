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
private val C = "c".repeat(32)
private const val NOW = 2000000000000L
private const val OFFICIAL = "f0uri"

private class Session(override var token: String? = "nabd-tg:$NS:$A:${"c".repeat(64)}") : Sessions

private fun account(id: String, name: String, username: String) = JSONObject()
    .put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", username)
        .put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
    .put("salt", "000102030405060708090a0b0c0d0e0f")
    .put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6")
    .put("sessions", JSONObject().put(digest("c".repeat(64)), NOW + 1000000))

private fun fixture(official: Boolean = false): JSONObject {
    val users = JSONObject().put(A, account(A, "alice", "alice_user")).put(B, account(B, "bob", "bob_user"))
    if (official) users.put(C, account(C, "f0uri", OFFICIAL))
    return JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", NS).put("revision", 1)
        .put("parent", JSONObject.NULL).put("writeId", "d".repeat(32)).put("users", users).put("posts", JSONObject()).put("friendships", JSONArray())
}

private class Transport(initial: JSONObject) : VaultTransport {
    var value = initial; var pointer = 1L
    private var uploaded: JSONObject? = null
    override fun chat(): JSONObject = JSONObject().put("pinned_message", JSONObject().put("message_id", pointer)
        .put("caption", "nabd-direct-telegram-v1").put("document", JSONObject().put("file_id", "$pointer").put("file_name", "nabd-vault.json").put("file_size", 10)))
    override fun read(fileId: String) = value.copyJson()
    override fun upload(json: String): Long { uploaded = JSONObject(json); return pointer + 1 }
    override fun pin(messageId: Long) { pointer = messageId; value = uploaded!! }
}

/** Follows are stored in the shared vault, so every count shown in the UI is a server value. */
class FollowTest {
    @Test fun followMakesAnInstantFriendAndUnfollowRemovesIt() = runBlocking {
        val t = Transport(fixture()); val repo = NabdRepository(t, NS, Session()) { NOW }
        repo.followUser(B, true)
        assertEquals(1, repo.snapshot().social.following[A])
        assertEquals(0, repo.snapshot().social.following[B])
        assertEquals(1, repo.snapshot().social.followers[B])
        assertEquals(0, repo.snapshot().social.followers[A])
        assertTrue(repo.snapshot().people.first { it.id == B }.following)
        assertTrue(repo.snapshot().people.first { it.id == B }.friend)
        repo.followUser(B, false)
        assertEquals(0, repo.snapshot().social.followers[B])
        assertFalse(repo.snapshot().people.first { it.id == B }.following)
        assertFalse(repo.snapshot().people.first { it.id == B }.friend)
    }
    @Test fun selfFollowAndUnknownAccountsAreRejected() = runBlocking {
        val t = Transport(fixture()); val repo = NabdRepository(t, NS, Session()) { NOW }
        assertTrue(runCatching { repo.followUser(A, true) }.isFailure)
        assertTrue(runCatching { repo.followUser("f".repeat(32), true) }.isFailure)
        assertEquals(0, t.value.getJSONObject("users").getJSONObject(A).optJSONArray("following")?.length() ?: 0)
    }
    @Test fun followingSurvivesValidationAndIsOptional() = runBlocking {
        val f = fixture(); VaultProtocol.validate(f, NS)
        f.getJSONObject("users").getJSONObject(A).put("following", JSONArray(listOf(B)))
        VaultProtocol.validate(f, NS)
        f.getJSONObject("users").getJSONObject(A).put("following", JSONArray(listOf(B, B)))
        assertTrue(runCatching { VaultProtocol.validate(f, NS) }.isFailure)
        f.getJSONObject("users").getJSONObject(A).put("following", JSONArray(listOf(A)))
        assertTrue(runCatching { VaultProtocol.validate(f, NS) }.isFailure)
        f.getJSONObject("users").getJSONObject(A).put("following", JSONArray(listOf("z".repeat(32))))
        assertTrue(runCatching { VaultProtocol.validate(f, NS) }.isFailure)
    }
    @Test fun newAccountsAreShownTheOfficialAccountInsteadOfAutoFollowingIt() = runBlocking {
        val with = Transport(fixture(true)); NabdRepository(with, NS, Session()) { NOW }.register("new user", "new@example.com", "password123")
        val created = with.value.getJSONObject("users").objects().first { it.getJSONObject("profile").getString("email") == "new@example.com" }
        assertFalse(created.has("following"))
        val without = Transport(fixture(false)); NabdRepository(without, NS, Session()) { NOW }.register("new user", "new@example.com", "password123")
        val plain = without.value.getJSONObject("users").objects().first { it.getJSONObject("profile").getString("email") == "new@example.com" }
        assertFalse(plain.has("following"))
    }
    @Test fun officialAccountCanBeFollowedAndUnfollowedByTap() = runBlocking {
        val t = Transport(fixture(true)); val sessions = Session()
        NabdRepository(t, NS, sessions) { NOW }.register("new user", "new@example.com", "password123")
        val repo = NabdRepository(t, NS, sessions) { NOW }
        assertEquals(0, repo.snapshot().social.followers[C])
        repo.followUser(C, true)
        assertEquals(1, repo.snapshot().social.followers[C])
        assertTrue(repo.snapshot().people.first { it.id == C }.friend)
        repo.followUser(C, false)
        assertEquals(0, repo.snapshot().social.followers[C])
        assertFalse(repo.snapshot().people.first { it.id == C }.friend)
    }
    @Test fun officialGrowthBumpsReactionsAndFollowersThenThrottles() = runBlocking {
        val t = Transport(fixture(true))
        val official = NabdRepository(t, NS, Session("nabd-tg:$NS:$C:${"c".repeat(64)}")) { NOW }
        repeat(5) { official.createPost("منشور رسمي $it") }
        val repo = NabdRepository(t, NS, Session()) { NOW }
        assertEquals(0, repo.snapshot().posts.sumOf { it.likes })
        assertEquals(0, repo.snapshot().social.followers[C])
        assertTrue(repo.bumpOfficialEngagement())
        val grown = repo.snapshot()
        // Each recent official post usually gains 1..3 reactions (a post is
        // sometimes skipped, so the sum over five posts is the stable check).
        assertTrue(grown.posts.sumOf { it.likes } in 1..15)
        assertEquals(grown.posts.sumOf { it.likes }, grown.posts.sumOf { it.breakdown.values.sum() })
        assertTrue(grown.social.followers[C] in 1..3)
        // Throttled: a second bump before the cooldown writes nothing.
        assertFalse(repo.bumpOfficialEngagement())
        // The growth is stored in the vault, survives a fresh instance, and
        // never breaks the vault's own reaction/vote invariant.
        VaultProtocol.validate(t.value, NS)
        val reopened = NabdRepository(t, NS, Session()) { NOW }.snapshot()
        assertEquals(grown.posts.sumOf { it.likes }, reopened.posts.sumOf { it.likes })
        assertEquals(grown.social.followers[C], reopened.social.followers[C])
    }
    @Test fun pinningIsAllowedForTheAuthorAndTheOfficialAccountOnly() = runBlocking {
        val t = Transport(fixture(true)); val sessions = Session()
        val alice = NabdRepository(t, NS, sessions) { NOW }
        alice.createPost("منشور أليس")
        val post = alice.snapshot().posts.first().id
        val bob = NabdRepository(t, NS, Session("nabd-tg:$NS:$B:${"c".repeat(64)}")) { NOW }
        assertTrue(runCatching { bob.setPinned(post, true) }.isFailure)
        alice.setPinned(post, true)
        assertTrue(alice.snapshot().posts.first().pinned)
        val official = NabdRepository(t, NS, Session("nabd-tg:$NS:$C:${"c".repeat(64)}")) { NOW }
        official.setPinned(post, false)
        assertFalse(official.snapshot().posts.first().pinned)
    }
    @Test fun pinnedPostsRideAtTheTopOfTheRealFeed() = runBlocking {
        val t = Transport(fixture(true)); val repo = NabdRepository(t, NS, Session()) { NOW }
        repo.createPost("الأول"); repo.createPost("الثاني")
        val older = repo.snapshot().posts.last().id
        repo.setPinned(older, true)
        assertEquals(older, repo.snapshot().posts.first().id)
        assertTrue(repo.snapshot().posts.first().pinned)
    }
}
