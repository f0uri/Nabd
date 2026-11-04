package app.nabd.nativeapp

import app.nabd.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private const val NS = "abcdef012345abcdef012345"
private val A = "a".repeat(32)
private val B = "b".repeat(32)
private const val NOW = 2000000000000L
private class MemorySession : Sessions { override var token: String? = "nabd-tg:$NS:$A:${"c".repeat(64)}" }
private fun fixture(): JSONObject {
    fun account(id: String, name: String) = JSONObject().put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", "${name}_user").put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
        .put("salt", "000102030405060708090a0b0c0d0e0f").put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6").put("sessions", JSONObject().put(digest("c".repeat(64)), NOW + 1000000))
    return JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", NS).put("revision", 1).put("parent", JSONObject.NULL).put("writeId", "d".repeat(32))
        .put("users", JSONObject().put(A, account(A, "alice")).put(B, account(B, "bob"))).put("posts", JSONObject()).put("friendships", JSONArray())
}
private class FakeTransport : VaultTransport {
    var value = fixture(); var pointer = 1L; var writes = 0; var conflict = false
    private var uploaded: JSONObject? = null
    override fun chat(): JSONObject = JSONObject().put("pinned_message", JSONObject().put("message_id", if (conflict && writes > 0) 999 else pointer).put("caption", "nabd-direct-telegram-v1").put("document", JSONObject().put("file_id", "$pointer").put("file_name", "nabd-vault.json").put("file_size", value.toString().toByteArray().size)))
    override fun read(fileId: String) = value.copyJson()
    override fun upload(json: String): Long { writes++; uploaded = JSONObject(json); return pointer + 1 }
    override fun pin(messageId: Long) { pointer = messageId; value = uploaded!! }
}

class NativeProtocolTest {
    @Test fun unicodeAndNulPasswordMatchesExisting210000RoundHash() {
        assertEquals("4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6", passwordHash("Nabd\u0000عربية 🌍", "000102030405060708090a0b0c0d0e0f"))
    }
    @Test fun wrongPasswordNeverCreatesASessionOrWrites() = runBlocking {
        val t = FakeTransport(); val sessions = MemorySession(); val previous = sessions.token
        val repo = NabdRepository(t, NS, sessions) { NOW }
        assertTrue(runCatching { repo.login("alice_user", "wrong") }.isFailure)
        assertEquals(0, t.writes); assertEquals(previous, sessions.token)
    }
    @Test fun loginAndReadAreCompatibleWithoutDeletingOtherAccountsOrExtensions() = runBlocking {
        val t = FakeTransport(); val sessions = MemorySession()
        t.value.getJSONObject("users").getJSONObject(B).put("futureAccountField", "keep-me")
        val repo = NabdRepository(t, NS, sessions) { NOW }
        repo.login("@alice_user", "Nabd\u0000عربية 🌍")
        assertEquals("alice", repo.snapshot().me.name)
        assertEquals("keep-me", t.value.getJSONObject("users").getJSONObject(B).getString("futureAccountField"))
        assertEquals(2, t.value.getJSONObject("users").length())
        assertEquals(1, t.writes)
    }
    @Test fun changedPinRejectsWithoutPinningOverAnotherWriter() = runBlocking {
        val t = FakeTransport().apply { conflict = true }
        val repo = NabdRepository(t, NS, MemorySession()) { NOW }
        assertTrue(runCatching { repo.createPost("must not overwrite") }.isFailure)
        assertEquals(1, t.pointer); assertEquals(0, t.value.getJSONObject("posts").length())
    }
    @Test fun nativeMutationsPreserveLegacyV1SchemaAndProduceARealInteropFixture() = runBlocking {
        val t = FakeTransport(); val sessions = MemorySession(); val repo = NabdRepository(t, NS, sessions) { NOW }
        repo.createPost("منشور Kotlin حقيقي للاختبار فقط")
        val post = repo.snapshot().posts.single()
        repo.react(post.id, "love"); repo.comment(post.id, "تعليق")
        repo.follow(B, true); val thread = repo.startThread(B); repo.sendMessage(thread, "رسالة مشتركة")
        val message = repo.snapshot().threads.single().messages.single().id
        repo.sendMessage(thread, "صورة ورد", message, "data:image/jpeg;base64,YQ==", 1.0)
        repo.editMessage(thread, message, "رسالة مشتركة")
        repo.reactMessage(thread, message, "love")
        val commentId = repo.snapshot().posts.single().comments.single().id
        repo.likeComment(post.id, commentId)
        repo.updateProfile("Alice Native", "alice_native", "نبذة")
        VaultProtocol.validate(t.value, NS)
        val snapshot = repo.snapshot()
        assertEquals(1, snapshot.posts.single().likes)
        assertEquals(1, snapshot.posts.single().comments.size)
        assertEquals("رسالة مشتركة", snapshot.threads.single().messages.first().text)
        assertTrue(snapshot.people.first { it.id == B }.friend)
        File("build/native-interop.json").apply { parentFile.mkdirs() }.writeText(JSONObject().put("vault", t.value).put("namespace", NS).put("token", sessions.token).put("now", NOW).toString())
    }
    @Test fun richChatMaintainsLegacySchemaAndEnforcesOwnership() = runBlocking {
        val t = FakeTransport(); val sessions = MemorySession(); val repo = NabdRepository(t, NS, sessions) { NOW }
        val thread = repo.startThread(B)
        repo.sendMessage(thread, "original")
        val first = repo.snapshot().threads.single().messages.single().id
        repo.sendMessage(thread, "", first, "data:image/jpeg;base64,YQ==", 1.5)
        repo.editMessage(thread, first, "edited")
        repo.reactMessage(thread, first, "love")
        var messages = repo.snapshot().threads.single().messages
        assertTrue(messages.first().edited); assertEquals("edited", messages.first().text)
        assertEquals(first, messages.last().replyToId); assertNotNull(messages.last().image)
        assertEquals("love", messages.first().reactions[A])
        sessions.token = "nabd-tg:$NS:$B:${"c".repeat(64)}"
        val writes = t.writes
        assertTrue(runCatching { repo.editMessage(thread, first, "attack") }.isFailure)
        assertTrue(runCatching { repo.deleteMessage(thread, first) }.isFailure)
        assertEquals(writes, t.writes)
        repo.readThread(thread)
        assertTrue(repo.snapshot().threads.single().messages.all { it.read })
        val readWrites = t.writes; repo.readThread(thread); assertEquals(readWrites, t.writes)
        repo.reactMessage(thread, first, "wow")
        sessions.token = "nabd-tg:$NS:$A:${"c".repeat(64)}"
        repo.deleteMessage(thread, first)
        messages = repo.snapshot().threads.single().messages
        assertTrue(messages.first().deleted); assertTrue(messages.first().text.isEmpty())
        assertTrue(messages.first().reactions.isEmpty()); assertNotNull(messages.last().image)
        assertTrue(runCatching { repo.reactMessage(thread, first, "like") }.isFailure)
        assertTrue(runCatching { repo.sendMessage(thread, "bad reply", "e".repeat(32)) }.isFailure)
        VaultProtocol.validate(t.value, NS)
    }
    @Test fun commentLikesAndModerationAreScopedAndCleanUpIndexes() = runBlocking {
        val t = FakeTransport(); val sessions = MemorySession(); val repo = NabdRepository(t, NS, sessions) { NOW }
        repo.createPost("post"); val post = repo.snapshot().posts.single().id
        repo.comment(post, "mine"); val mine = repo.snapshot().posts.single().comments.single().id
        repo.likeComment(post, mine)
        assertTrue(repo.snapshot().posts.single().comments.single().likedByMe)
        repo.likeComment(post, mine); assertEquals(0, repo.snapshot().posts.single().comments.single().likes)
        sessions.token = "nabd-tg:$NS:$B:${"c".repeat(64)}"
        val writes = t.writes
        assertTrue(runCatching { repo.deleteComment(post, mine) }.isFailure); assertEquals(writes, t.writes)
        repo.comment(post, "other"); val other = repo.snapshot().posts.single().comments.last().id
        repo.likeComment(post, other)
        sessions.token = "nabd-tg:$NS:$A:${"c".repeat(64)}"
        repo.deleteComment(post, other)
        assertEquals(1, repo.snapshot().posts.single().comments.size)
        assertFalse(t.value.getJSONObject("posts").getJSONObject(post).getJSONObject("commentLikes").has(other))
        repo.updateProfile("Alice Native", "alice_native", "bio", "data:image/jpeg;base64,YQ==")
        assertNotNull(repo.snapshot().me.avatar)
        repo.updateProfile("Alice Native", "alice_native", "bio", removeAvatar = true)
        assertNull(repo.snapshot().me.avatar)
        VaultProtocol.validate(t.value, NS)
    }
    @Test fun invalidOrForeignSnapshotIsNeverRepaired() {
        assertTrue(runCatching { VaultProtocol.validate(fixture().put("namespace", "foreign"), NS) }.isFailure)
        assertTrue(runCatching { VaultProtocol.validate(fixture().put("extraRoot", 1), NS) }.isFailure)
        assertTrue(runCatching { VaultProtocol.validate(fixture().put("constructor", "bad"), NS) }.isFailure)
    }
}
