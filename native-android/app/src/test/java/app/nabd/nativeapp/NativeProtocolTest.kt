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
        repo.updateProfile("Alice Native", "alice_native", "نبذة")
        VaultProtocol.validate(t.value, NS)
        val snapshot = repo.snapshot()
        assertEquals(1, snapshot.posts.single().likes)
        assertEquals(1, snapshot.posts.single().comments.size)
        assertEquals("رسالة مشتركة", snapshot.threads.single().messages.single().text)
        assertTrue(snapshot.people.first { it.id == B }.friend)
        File("build/native-interop.json").apply { parentFile.mkdirs() }.writeText(JSONObject().put("vault", t.value).put("namespace", NS).put("token", sessions.token).put("now", NOW).toString())
    }
    @Test fun invalidOrForeignSnapshotIsNeverRepaired() {
        assertTrue(runCatching { VaultProtocol.validate(fixture().put("namespace", "foreign"), NS) }.isFailure)
        assertTrue(runCatching { VaultProtocol.validate(fixture().put("extraRoot", 1), NS) }.isFailure)
        assertTrue(runCatching { VaultProtocol.validate(fixture().put("constructor", "bad"), NS) }.isFailure)
    }
}
