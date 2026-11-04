package app.nabd.nativeapp

import app.nabd.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

private const val NOTE_NS = "abcdef012345abcdef012345"
private val NOTE_A = "a".repeat(32)
private val NOTE_B = "b".repeat(32)
private const val NOTE_NOW = 2000000000000L

private class NotificationSession(override var token: String?) : Sessions

private fun noteAccount(id: String, name: String) = JSONObject()
    .put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", "${name}_user")
        .put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
    .put("salt", "000102030405060708090a0b0c0d0e0f")
    .put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6")
    .put("sessions", JSONObject().put(digest("c".repeat(64)), NOTE_NOW + 1000000))

private fun noteFixture() = JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", NOTE_NS).put("revision", 1)
    .put("parent", JSONObject.NULL).put("writeId", "d".repeat(32))
    .put("users", JSONObject().put(NOTE_A, noteAccount(NOTE_A, "alice")).put(NOTE_B, noteAccount(NOTE_B, "bob")))
    .put("posts", JSONObject()).put("friendships", JSONArray())

private class NotificationTransport(initial: JSONObject) : VaultTransport {
    var value = initial; var pointer = 1L
    private var uploaded: JSONObject? = null
    override fun chat(): JSONObject = JSONObject().put("pinned_message", JSONObject().put("message_id", pointer)
        .put("caption", "nabd-direct-telegram-v1").put("document", JSONObject().put("file_id", "$pointer").put("file_name", "nabd-vault.json").put("file_size", 10)))
    override fun read(fileId: String) = value.copyJson()
    override fun upload(json: String): Long { uploaded = JSONObject(json); return pointer + 1 }
    override fun pin(messageId: Long) { pointer = messageId; value = uploaded!! }
}

private fun NotificationTransport.owner() = NabdRepository(this, NOTE_NS, NotificationSession("nabd-tg:$NOTE_NS:$NOTE_A:${"c".repeat(64)}")) { NOTE_NOW }
private fun NotificationTransport.other() = NabdRepository(this, NOTE_NS, NotificationSession("nabd-tg:$NOTE_NS:$NOTE_B:${"c".repeat(64)}")) { NOTE_NOW }

/** Notifications are real vault records written by the acting account, never local counters. */
class NotificationsTest {
    @Test fun pulsesCommentsAndFollowsReachTheOtherAccount() = runBlocking {
        val t = NotificationTransport(noteFixture())
        t.owner().createPost("منشور أليس")
        val post = t.owner().snapshot().posts.first().id
        val bob = t.other()
        bob.react(post, "love")
        bob.comment(post, "تعليق بوب")
        bob.followUser(NOTE_A, true)
        val notes = t.owner().snapshot().notifications
        assertEquals(3, notes.size)
        assertEquals(setOf("pulse", "comment", "follow"), notes.map { it.kind }.toSet())
        assertTrue(notes.all { it.actor.id == NOTE_B })
        assertEquals(post, notes.first { it.kind == "pulse" }.postId)
        assertEquals(post, notes.first { it.kind == "comment" }.postId)
        assertNull(notes.first { it.kind == "follow" }.postId)
    }
    @Test fun identicalEventsCollapseIntoOneNotification() = runBlocking {
        val t = NotificationTransport(noteFixture())
        t.owner().createPost("منشور")
        val post = t.owner().snapshot().posts.first().id
        t.other().react(post, "love")
        t.other().react(post, "love")
        val notes = t.owner().snapshot().notifications
        assertEquals(1, notes.size)
        assertEquals("pulse", notes.first().kind)
    }
    @Test fun repliesUseTheirOwnKindAndLinkThePost() = runBlocking {
        val t = NotificationTransport(noteFixture())
        t.owner().createPost("منشور")
        val post = t.owner().snapshot().posts.first().id
        t.owner().comment(post, "تعليق أليس")
        val root = t.owner().snapshot().posts.first().comments.first().id
        t.other().comment(post, "رد بوب", null, root)
        val notes = t.owner().snapshot().notifications
        assertEquals(1, notes.size)
        assertEquals("reply", notes.first().kind)
        assertEquals(post, notes.first().postId)
    }
    @Test fun ownActionsNeverNotifyYourself() = runBlocking {
        val t = NotificationTransport(noteFixture())
        t.owner().createPost("منشور")
        val post = t.owner().snapshot().posts.first().id
        t.owner().react(post, "love")
        t.owner().comment(post, "تعليقي")
        assertTrue(t.owner().snapshot().notifications.isEmpty())
    }
    @Test fun markingReadIsStoredInTheVault() = runBlocking {
        val t = NotificationTransport(noteFixture())
        t.owner().createPost("منشور")
        t.other().followUser(NOTE_A, true)
        assertTrue(t.owner().snapshot().notifications.any { !it.read })
        t.owner().markNotificationsRead()
        val notes = t.owner().snapshot().notifications
        assertEquals(1, notes.size)
        assertTrue(notes.all { it.read })
    }
    @Test fun anAccountWithoutNotificationsStillLoads() = runBlocking<Unit> {
        val t = NotificationTransport(noteFixture())
        assertTrue(t.owner().snapshot().notifications.isEmpty())
        VaultProtocol.validate(noteFixture(), NOTE_NS)
    }
}
