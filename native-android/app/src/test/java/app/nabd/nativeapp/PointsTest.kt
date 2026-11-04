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
private const val DAY = 86400000L
private const val PASSWORD = "Nabd\u0000عربية 🌍"

private class PointsSession(override var token: String? = "nabd-tg:$NS:$A:${"c".repeat(64)}") : Sessions

private fun account(id: String, name: String, username: String, inviteCode: String? = null) = JSONObject()
    .put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", username)
        .put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
    .put("salt", "000102030405060708090a0b0c0d0e0f")
    .put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6")
    .put("sessions", JSONObject().put(digest("c".repeat(64)), NOW + 1000000))
    .let { if (inviteCode == null) it else it.put("inviteCode", inviteCode) }

private fun fixture() = JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", NS).put("revision", 1)
    .put("parent", JSONObject.NULL).put("writeId", "d".repeat(32))
    .put("users", JSONObject().put(A, account(A, "alice", "alice_user", "ALICECODE")).put(B, account(B, "bob", "bob_user")).put(C, account(C, "f0uri", "f0uri")))
    .put("posts", JSONObject()).put("friendships", JSONArray())

private class PointsTransport(initial: JSONObject) : VaultTransport {
    var value = initial; var pointer = 1L
    private var uploaded: JSONObject? = null
    override fun chat(): JSONObject = JSONObject().put("pinned_message", JSONObject().put("message_id", pointer)
        .put("caption", "nabd-direct-telegram-v1").put("document", JSONObject().put("file_id", "$pointer").put("file_name", "nabd-vault.json").put("file_size", 10)))
    override fun read(fileId: String) = value.copyJson()
    override fun upload(json: String): Long { uploaded = JSONObject(json); return pointer + 1 }
    override fun pin(messageId: Long) { pointer = messageId; value = uploaded!! }
}

/** Points, boosts, badges and moderation are real vault writes: no invented counter. */
class PointsTest {
    @Test fun dailyLoginAwardsPointsOncePerDayOnly() = runBlocking {
        val t = PointsTransport(fixture())
        NabdRepository(t, NS, PointsSession()) { NOW }.login("alice_user", PASSWORD)
        assertEquals(POINTS_DAILY_LOGIN, NabdRepository(t, NS, PointsSession()) { NOW }.snapshot().me.points)
        NabdRepository(t, NS, PointsSession()) { NOW }.login("alice_user", PASSWORD)
        assertEquals(POINTS_DAILY_LOGIN, NabdRepository(t, NS, PointsSession()) { NOW }.snapshot().me.points)
        NabdRepository(t, NS, PointsSession()) { NOW + DAY }.login("alice_user", PASSWORD)
        assertEquals(2 * POINTS_DAILY_LOGIN, NabdRepository(t, NS, PointsSession()) { NOW + DAY }.snapshot().me.points)
    }
    @Test fun postsAndInvitesAwardRealPoints() = runBlocking {
        val t = PointsTransport(fixture()); val sessions = PointsSession()
        val alice = NabdRepository(t, NS, sessions) { NOW }
        alice.createPost("منشور")
        assertEquals(POINTS_POST, alice.snapshot().me.points)
        val inviter = t.value.getJSONObject("users").getJSONObject(A).optInt("points", 0)
        NabdRepository(t, NS, PointsSession()) { NOW }.register("new user", "new@example.com", "password123", "ALICECODE")
        assertEquals(inviter + POINTS_INVITE, t.value.getJSONObject("users").getJSONObject(A).getInt("points"))
        val created = t.value.getJSONObject("users").objects().first { it.getJSONObject("profile").getString("email") == "new@example.com" }
        assertEquals(A, created.getString("invitedBy"))
        assertEquals(0, created.optInt("points", 0))
    }
    @Test fun unknownInviteCodeIsRejected() = runBlocking {
        val t = PointsTransport(fixture())
        assertTrue(runCatching { NabdRepository(t, NS, PointsSession()) { NOW }.register("new user", "new@example.com", "password123", "NOPE") }.isFailure)
        assertEquals(0, t.value.getJSONObject("users").length() - 3)
    }
    @Test fun boostSpendsPointsWithoutTouchingReactionCounts() = runBlocking {
        val t = PointsTransport(fixture()); val repo = NabdRepository(t, NS, PointsSession()) { NOW }
        repeat(BOOST_COST / POINTS_POST) { repo.createPost("منشور ${it + 1}") }
        assertEquals(BOOST_COST, repo.snapshot().me.points)
        val older = repo.snapshot().posts.last()
        repo.react(older.id, "love")
        val likesBefore = repo.snapshot().posts.find { it.id == older.id }!!.likes
        repo.boostPost(older.id)
        val boosted = repo.snapshot().posts.find { it.id == older.id }!!
        assertEquals(0, repo.snapshot().me.points)
        assertTrue(boosted.boosted)
        assertEquals(likesBefore, boosted.likes)
        assertEquals(older.id, repo.snapshot().posts.first().id)
        assertTrue(runCatching { repo.boostPost(older.id) }.isFailure)
        assertEquals(0, repo.snapshot().me.points)
    }
    @Test fun boostExpiryIsReadFromTheServerValue() = runBlocking {
        val t = PointsTransport(fixture()); val repo = NabdRepository(t, NS, PointsSession()) { NOW }
        repeat(BOOST_COST / POINTS_POST) { repo.createPost("منشور ${it + 1}") }
        val older = repo.snapshot().posts.last()
        repo.boostPost(older.id)
        assertTrue(repo.snapshot().posts.first().boosted)
        assertFalse(NabdRepository(t, NS, PointsSession()) { NOW + DAY + 1 }.snapshot().posts.first { it.id == older.id }.boosted)
    }
    @Test fun onlyTheOfficialAccountMayVerifyRestrictOrAnnounce() = runBlocking {
        val t = PointsTransport(fixture())
        val alice = NabdRepository(t, NS, PointsSession()) { NOW }
        assertTrue(runCatching { alice.setVerified(B, true) }.isFailure)
        assertTrue(runCatching { alice.setRestriction(B, true, null) }.isFailure)
        assertTrue(runCatching { alice.announce("إعلان") }.isFailure)
        val official = NabdRepository(t, NS, PointsSession("nabd-tg:$NS:$C:${"c".repeat(64)}")) { NOW }
        official.setVerified(B, true)
        official.setRestriction(B, null, true)
        official.announce("إعلان رسمي")
        val bob = NabdRepository(t, NS, PointsSession("nabd-tg:$NS:$B:${"c".repeat(64)}")) { NOW }.snapshot()
        assertTrue(bob.people.first { it.id == B }.verified)
        assertTrue(bob.people.first { it.id == B }.restricted)
        assertEquals("إعلان رسمي", bob.announcements.first().text)
    }
    @Test fun bannedAccountCannotActAndRestrictedAccountCannotPost() = runBlocking {
        val t = PointsTransport(fixture())
        val official = NabdRepository(t, NS, PointsSession("nabd-tg:$NS:$C:${"c".repeat(64)}")) { NOW }
        official.setRestriction(B, null, true)
        assertTrue(runCatching { NabdRepository(t, NS, PointsSession("nabd-tg:$NS:$B:${"c".repeat(64)}")) { NOW }.createPost("ممنوع") }.isFailure)
        official.setRestriction(B, true, null)
        assertTrue(runCatching { NabdRepository(t, NS, PointsSession()) { NOW }.login("bob_user", PASSWORD) }.isFailure)
        assertTrue(runCatching { official.setRestriction(C, true, null) }.isFailure)
    }
    @Test fun validatorRejectsNegativePointsAndBadAnnouncements() = runBlocking {
        val f = fixture(); VaultProtocol.validate(f, NS)
        f.getJSONObject("users").getJSONObject(A).put("points", -1)
        assertTrue(runCatching { VaultProtocol.validate(f, NS) }.isFailure)
        f.getJSONObject("users").getJSONObject(A).put("points", 5); VaultProtocol.validate(f, NS)
        f.getJSONObject("users").getJSONObject(C).put("announcements", JSONArray()
            .put(JSONObject().put("id", "e".repeat(32)).put("text", "x".repeat(281)).put("at", isoDate(NOW))))
        assertTrue(runCatching { VaultProtocol.validate(f, NS) }.isFailure)
    }
}
