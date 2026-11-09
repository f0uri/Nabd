package app.nabd.nativeapp

import app.nabd.nativeapp.data.AUDIO_PLACEHOLDER
import app.nabd.nativeapp.data.MAX_VAULT_BYTES
import app.nabd.nativeapp.data.MediaFiles
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A vault that no longer fits used to refuse every write, so nothing could be
 * published. These tests prove the pictures leave it instead: each one becomes a
 * Telegram document of its own and only a small preview stays behind.
 */
class MediaSpillTest {
    private fun picture(bytes: Int) = "data:image/jpeg;base64," + "A".repeat(bytes)
    private fun vault(pictures: List<String>): JSONObject {
        val posts = JSONObject()
        pictures.forEachIndexed { index, data ->
            posts.put("post$index", JSONObject().put("post", JSONObject().put("id", "post$index").put("text", "منشور")
                .put("comments", JSONArray())
                .put("media", JSONObject().put("id", "media$index").put("kind", "image").put("mime", "image/jpeg").put("uri", data))))
        }
        return JSONObject().put("app", "nabd-direct-telegram").put("posts", posts)
    }
    private fun account(recording: String, avatar: String): JSONObject {
        val audio = JSONObject().put("note1", JSONObject().put("uri", recording).put("seconds", 12).put("bars", JSONArray(listOf(1, 2, 3))))
        val chat = JSONObject().put("id", "chat1").put("members", JSONArray(listOf("a", "b")))
            .put("createdAt", "2026-01-01T00:00:00.000Z").put("messages", JSONArray()).put("audioV1", audio)
        return JSONObject().put("app", "nabd-direct-telegram").put("posts", JSONObject())
            .put("users", JSONObject().put("u1", JSONObject()
                .put("profile", JSONObject().put("id", "u1").put("avatarUrl", avatar))
                .put("chatV1", JSONObject().put("chat1", chat))))
    }

    private fun media(v: JSONObject, index: Int) = v.getJSONObject("posts").getJSONObject("post$index").getJSONObject("post").getJSONObject("media")
    private fun size(v: JSONObject) = v.toString().toByteArray().size

    @Test fun aVaultThatNoLongerFitsGetsLighter() = runBlocking {
        val v = vault(listOf(picture(120_000), picture(120_000), picture(120_000)))
        val before = size(v)
        var sent = 0
        val moved = MediaFiles.spill(v, needed = before / 2) { sent++; picture(4_000) to "file-id-$sent" }
        assertTrue(moved >= 1)
        assertTrue("the vault is smaller afterwards", size(v) < before / 2)
    }

    @Test fun thePreviewStaysAndTheIdTravelsWithIt() = runBlocking {
        val v = vault(listOf(picture(100_000)))
        val moved = MediaFiles.spill(v, needed = 1) { picture(3_000) to "the-file-id" }
        assertEquals(1, moved)
        val media = media(v, 0)
        assertEquals(picture(3_000), media.getString("uri"))
        assertEquals("the-file-id", media.getString("fileId"))
    }

    @Test fun theBiggestPictureLeavesFirst() = runBlocking {
        val v = vault(listOf(picture(30_000), picture(150_000)))
        MediaFiles.spill(v, needed = 40_000) { picture(2_000) to "id" }
        assertEquals("id", media(v, 1).optString("fileId"))
        assertEquals(picture(30_000), media(v, 0).getString("uri"))
    }

    @Test fun aSmallPictureStaysInside() = runBlocking {
        val v = vault(listOf(picture(6_000)))
        var sent = 0
        val moved = MediaFiles.spill(v, needed = 1) { sent++; picture(1_000) to "id" }
        assertEquals(0, moved)
        assertEquals(0, sent)
    }

    @Test fun nothingMovesWhenTheVaultAlreadyFits() = runBlocking {
        val v = vault(listOf(picture(100_000)))
        val before = v.toString()
        var sent = 0
        val moved = MediaFiles.spill(v, needed = MAX_VAULT_BYTES) { sent++; picture(1_000) to "id" }
        assertEquals(0, moved)
        assertEquals(before, v.toString())
    }

    @Test fun aPictureTelegramRefusesStaysWhereItIs() = runBlocking {
        val v = vault(listOf(picture(90_000)))
        val moved = MediaFiles.spill(v, needed = 1) { null }
        assertEquals(0, moved)
        assertEquals(picture(90_000), media(v, 0).getString("uri"))
        assertFalse(media(v, 0).has("fileId"))
    }

    @Test fun aRecordingLeavesTheVaultToo() = runBlocking {
        val v = account("data:audio/3gp;base64," + "A".repeat(80_000), picture(80_000))
        var sounds = 0; var pictures = 0
        val moved = MediaFiles.spill(v, needed = 1) { data ->
            if (data.startsWith("data:audio")) { sounds++; AUDIO_PLACEHOLDER to "audio-$sounds" }
            else { pictures++; picture(2_000) to "picture-$pictures" }
        }
        assertEquals(2, moved)
        val note = v.getJSONObject("users").getJSONObject("u1").getJSONObject("chatV1").getJSONObject("chat1")
            .getJSONObject("audioV1").getJSONObject("note1")
        assertEquals(AUDIO_PLACEHOLDER, note.getString("uri"))
        assertTrue(note.getString("fileId").startsWith("audio-"))
        assertTrue(v.getJSONObject("users").getJSONObject("u1").getJSONObject("profile").getString("fileId").startsWith("picture-"))
    }

    @Test fun aSmallRecordingStaysWhereItIs() = runBlocking {
        val v = account("data:audio/3gp;base64," + "A".repeat(4_000), picture(4_000))
        var sent = 0
        val moved = MediaFiles.spill(v, needed = 1) { sent++; picture(1_000) to "id" }
        assertEquals(0, moved)
        assertEquals(0, sent)
    }

    @Test fun aReferenceIsCarriedAndRecognised() {
        val reference = MediaFiles.reference("a-file-id")
        assertTrue(MediaFiles.isReference(reference))
        assertFalse(MediaFiles.isReference("data:image/jpeg;base64,AAAA"))
        assertFalse(MediaFiles.isReference(null))
        assertEquals("a-file-id", reference.removePrefix("tg:"))
    }
}
