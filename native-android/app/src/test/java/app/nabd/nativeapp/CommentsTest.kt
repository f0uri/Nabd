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
private const val PICTURE = "data:image/jpeg;base64,YQ=="

private class CommentSession(override var token: String? = "nabd-tg:$NS:$A:${"c".repeat(64)}") : Sessions

private fun account(id: String, name: String) = JSONObject()
    .put("profile", JSONObject().put("id", id).put("name", name).put("email", "$name@example.com").put("username", "${name}_user")
        .put("color", "#1877F2").put("initial", name.take(1)).put("bio", "").put("avatarUrl", JSONObject.NULL))
    .put("salt", "000102030405060708090a0b0c0d0e0f")
    .put("passwordHash", "4540121fed790917487ae1ad6b7b4aae789939d49cb522529bfffd44e37296b6")
    .put("sessions", JSONObject().put(digest("c".repeat(64)), NOW + 1000000))

private fun fixture() = JSONObject().put("app", "nabd-direct-telegram").put("version", 1).put("namespace", NS).put("revision", 1)
    .put("parent", JSONObject.NULL).put("writeId", "d".repeat(32))
    .put("users", JSONObject().put(A, account(A, "alice")).put(B, account(B, "bob"))).put("posts", JSONObject()).put("friendships", JSONArray())

private class CommentTransport(initial: JSONObject) : VaultTransport {
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

/** Replies and comment images are stored in the shared vault, never locally. */
class CommentsTest {
    @Test fun repliesKeepTheirParentAndTheRealCount() = runBlocking {
        val t = CommentTransport(fixture()); val repo = NabdRepository(t, NS, CommentSession()) { NOW }
        repo.createPost("منشور")
        val post = repo.snapshot().posts.first().id
        repo.comment(post, "تعليق أصلي")
        val root = repo.snapshot().posts.first().comments.first()
        repo.comment(post, "رد على التعليق", null, root.id)
        val comments = repo.snapshot().posts.first().comments
        assertEquals(2, comments.size)
        assertNull(comments[0].replyTo)
        assertEquals(root.id, comments[1].replyTo)
        assertEquals(A, comments[1].author.id)
        assertEquals(2, t.value.getJSONObject("posts").getJSONObject(post).getJSONObject("post").getInt("commentsCount"))
    }
    @Test fun unknownReplyTargetAndEmptyTextAreRejected() = runBlocking {
        val t = CommentTransport(fixture()); val repo = NabdRepository(t, NS, CommentSession()) { NOW }
        repo.createPost("منشور")
        val post = repo.snapshot().posts.first().id
        assertTrue(runCatching { repo.comment(post, "مرحبا", null, "f".repeat(32)) }.isFailure)
        assertTrue(runCatching { repo.comment(post, "   ", null) }.isFailure)
        assertEquals(0, repo.snapshot().posts.first().comments.size)
    }
    @Test fun commentImagesAreStoredAndValidated() = runBlocking {
        val t = CommentTransport(fixture()); val repo = NabdRepository(t, NS, CommentSession()) { NOW }
        repo.createPost("منشور")
        val post = repo.snapshot().posts.first().id
        repo.comment(post, "", PICTURE)
        assertEquals(PICTURE, repo.snapshot().posts.first().comments.first().image)
        assertTrue(runCatching { repo.comment(post, "نص", "not-an-image") }.isFailure)
        assertEquals(1, repo.snapshot().posts.first().comments.size)
    }
    @Test fun commentReactionsAreStoredInTheVaultAndReplaceEachOther() = runBlocking<Unit> {
        val t = CommentTransport(fixture()); val repo = NabdRepository(t, NS, CommentSession()) { NOW }
        repo.createPost("منشور")
        val post = repo.snapshot().posts.first().id
        repo.comment(post, "تعليق")
        val id = repo.snapshot().posts.first().comments.first().id
        repo.reactComment(post, id, "wow")
        var c = repo.snapshot().posts.first().comments.first()
        assertEquals(1, c.reactions["wow"]); assertEquals("wow", c.myReaction); assertEquals(1, c.likes)
        repo.reactComment(post, id, "haha")
        c = repo.snapshot().posts.first().comments.first()
        assertEquals(0, c.reactions["wow"]); assertEquals(1, c.reactions["haha"]); assertEquals("haha", c.myReaction); assertEquals(1, c.likes)
        repo.reactComment(post, id, "haha")
        c = repo.snapshot().posts.first().comments.first()
        assertEquals(0, c.reactions["haha"]); assertNull(c.myReaction); assertEquals(0, c.likes)
        VaultProtocol.validate(t.value, NS)
    }
    @Test fun aPulseOnACommentStaysCompatibleWithTheLegacyCounter() = runBlocking<Unit> {
        val t = CommentTransport(fixture()); val repo = NabdRepository(t, NS, CommentSession()) { NOW }
        repo.createPost("منشور")
        val post = repo.snapshot().posts.first().id
        repo.comment(post, "تعليق")
        val id = repo.snapshot().posts.first().comments.first().id
        repo.likeComment(post, id)
        var c = repo.snapshot().posts.first().comments.first()
        assertEquals("like", c.myReaction); assertEquals(1, c.likes); assertTrue(c.likedByMe)
        repo.reactComment(post, id, "wow")
        c = repo.snapshot().posts.first().comments.first()
        assertEquals("wow", c.myReaction); assertFalse(c.likedByMe); assertEquals(1, c.likes)
        repo.likeComment(post, id)
        c = repo.snapshot().posts.first().comments.first()
        assertEquals("like", c.myReaction); assertEquals(1, c.likes); assertEquals(0, c.reactions["wow"])
    }
    @Test fun oldCommentsWithoutReplyFieldsStillLoad() = runBlocking {
        val t = CommentTransport(fixture()); val repo = NabdRepository(t, NS, CommentSession()) { NOW }
        repo.createPost("منشور")
        val post = repo.snapshot().posts.first().id
        repo.comment(post, "تعليق قديم")
        val stored = t.value.getJSONObject("posts").getJSONObject(post).getJSONObject("post").getJSONArray("comments").getJSONObject(0)
        stored.remove("replyTo"); stored.remove("image")
        VaultProtocol.validate(t.value, NS)
        val loaded = repo.snapshot().posts.first().comments.first()
        assertNull(loaded.replyTo); assertNull(loaded.image)
    }
}
