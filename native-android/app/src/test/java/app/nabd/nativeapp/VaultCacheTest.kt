package app.nabd.nativeapp

import app.nabd.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

private const val CACHE_NS = "abcdef012345abcdef012345"
private val OWNER = "a".repeat(32)
private val OTHER = "b".repeat(32)
private const val CACHE_NOW = 2000000000000L

private class CountingTransport(initial: JSONObject) : VaultTransport {
    var value = initial
    var pointer = 1L
    var downloads = 0
    var calls = 0
    private var pending: JSONObject? = null
    override fun chat(): JSONObject { calls++; return JSONObject().put("pinned_message", JSONObject().put("message_id", pointer)
        .put("caption", "nabd-direct-telegram-v1")
        .put("document", JSONObject().put("file_id", "$pointer").put("file_name", "nabd-vault.json").put("file_size", 10))) }
    override fun read(fileId: String): JSONObject { downloads++; return value.copyJson() }
    override fun upload(json: String): Long { pending = JSONObject(json); return pointer + 1 }
    override fun pin(messageId: Long) { pointer = messageId; value = pending!! }
    override fun uploadMedia(bytes: ByteArray, name: String, mime: String): String = "test-media-${bytes.size}"
    override fun fetchMedia(fileId: String): ByteArray? = null
}

/** The vault lives behind one pinned file: nothing should download it twice. */
class VaultCacheTest {
    private fun account(id: String, name: String) = JSONObject()
        .put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", name)
            .put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
        .put("salt", "000102030405060708090a0b0c0d0e0f")
        .put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6")
        .put("sessions", JSONObject().put(digest("c".repeat(64)), CACHE_NOW + 1000000))

    private fun fixture() = JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", CACHE_NS)
        .put("revision", 1).put("writeId", "d".repeat(32)).put("parent", JSONObject.NULL)
        .put("users", JSONObject().put(OWNER, account(OWNER, "alice")).put(OTHER, account(OTHER, "bob")))
        .put("posts", JSONObject()).put("friendships", JSONArray())

    private fun repository(transport: CountingTransport) = NabdRepository(transport, CACHE_NS,
        object : Sessions { override var token: String? = "nabd-tg:$CACHE_NS:$OWNER:${"c".repeat(64)}" }) { CACHE_NOW }

    @Test fun pollingTwiceDownloadsTheVaultOnceAndReturnsTheSameSnapshot() {
        val transport = CountingTransport(fixture()); val repository = repository(transport)
        val first = runBlocking { repository.snapshot() }
        assertEquals(1, transport.downloads)
        val second = runBlocking { repository.snapshot() }
        assertEquals(1, transport.downloads)
        assertSame(first, second)
    }

    @Test fun aWriteIsConfirmedWithoutDownloadingTheVaultBack() {
        val transport = CountingTransport(fixture()); val repository = repository(transport)
        runBlocking { repository.snapshot() }
        val before = transport.downloads
        runBlocking { repository.followUser(OTHER, true) }
        assertEquals(before, transport.downloads)
        val fresh = runBlocking { repository.snapshot() }
        assertEquals(1, fresh.social.following[OWNER])
        assertEquals(before, transport.downloads)
    }

    @Test fun aNewPinFromAnotherDeviceIsStillDownloaded() {
        val transport = CountingTransport(fixture()); val repository = repository(transport)
        runBlocking { repository.snapshot() }
        val outside = fixture().put("revision", 9)
        transport.value = outside; transport.pointer = 77
        val fresh = runBlocking { repository.snapshot() }
        assertEquals(2, transport.downloads)
        assertTrue(fresh.posts.isEmpty())
    }

    @Test fun theSnapshotRebuildsAfterAWriteSoTheNewValueIsReal() {
        val transport = CountingTransport(fixture()); val repository = repository(transport)
        runBlocking { repository.followUser(OTHER, true) }
        runBlocking { repository.followUser(OTHER, false) }
        assertEquals(0, runBlocking { repository.snapshot() }.social.following[OWNER])
    }
}
