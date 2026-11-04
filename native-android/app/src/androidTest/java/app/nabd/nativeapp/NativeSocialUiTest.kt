package app.nabd.nativeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.nabd.nativeapp.data.*
import app.nabd.nativeapp.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeSocialUiTest {
    @get:Rule val compose = createComposeRule()
    private val self = "a".repeat(32)
    private val other = Person("b".repeat(32), "Bob", "bob", "", null, false)
    /** Sheet and dialog animations must settle before a touch can land on the emulator. */
    private fun settle(time: Long = 1_000) {
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(time)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
    private fun waitForTag(tag: String) {
        settle(3_000)
        runCatching { compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess } }
    }
    private fun click(tag: String) {
        waitForTag(tag)
        try { compose.onNodeWithTag(tag).performClick() } catch (e: Throwable) { settle(); compose.onNodeWithTag(tag).performClick() }
    }
    @Test fun chatReplyEditDeleteAndReactionsInvokeScopedCallbacks() {
        val mine = Message("c".repeat(32), self, "mine", false, false, null, true)
        val theirs = Message("d".repeat(32), other.id, "theirs", false, false, null, true)
        var sentReply: String? = null; var edited: String? = null; var deleted: String? = null; var reacted: Pair<String,String?>? = null
        compose.setContent { NabdTheme { RichChat(Thread("e".repeat(32), other, listOf(mine, theirs)), self, false, Modifier.fillMaxSize(), {},
            { _, reply, _, _, done -> sentReply = reply; done() }, { id, _, done -> edited = id; done() },
            { deleted = it }, { id, kind -> reacted = id to kind }, {}, {}) } }
        compose.onNodeWithTag("message-actions-${theirs.id}").performClick()
        compose.onNodeWithText("رد", substring = false).performClick()
        compose.onNodeWithTag("message-input").performTextInput("answer")
        compose.onNodeWithTag("message-send").performClick()
        assertEquals(theirs.id, sentReply)
        compose.onNodeWithTag("message-actions-${mine.id}").performClick()
        compose.onNodeWithText("تعديل", substring = false).performClick()
        compose.onNodeWithTag("message-input").performTextClearance()
        compose.onNodeWithTag("message-input").performTextInput("edited")
        compose.onNodeWithTag("message-send").performClick()
        assertEquals(mine.id, edited)
        compose.onNodeWithTag("message-actions-${mine.id}").performClick()
        compose.onNodeWithText("حذف", substring = false).performClick()
        compose.onNodeWithText("حذف", substring = false).performClick()
        assertEquals(mine.id, deleted)
        compose.onNodeWithTag("message-actions-${theirs.id}").performClick()
        compose.onNodeWithTag("reaction-love").performClick()
        assertEquals(theirs.id to "love", reacted)
    }
    @Test fun commentModerationAndLikesAreConnected() {
        var liked: String? = null; var deleted: String? = null
        val c = Comment("c".repeat(32), other, "comment", 0)
        val me = other.copy(id = self)
        val post = FeedPost("d".repeat(32), me, "post", "", null, 1f, null, 0, listOf(c))
        compose.setContent { NabdTheme { CommentsSheet(post, me, false, null, {}, { _, _, _, _ -> }, { liked = it }, { deleted = it }) } }
        click("comment-like-${c.id}"); assertEquals(c.id, liked)
        compose.onNodeWithTag("comment-reply-${c.id}").assertExists()
        click("comment-delete-${c.id}")
        settle()
        click("comment-delete-confirm"); assertEquals(c.id, deleted)
    }
    @Test fun profileSupportsExplicitAvatarRemovalWithoutErasingItOnOrdinarySave() {
        var removed = false
        val person = other.copy(id = self, avatar = "data:image/jpeg;base64,YQ==")
        compose.setContent { NabdTheme { ProfileDialog(person, false, null, {}, { _, _, _, _, remove -> removed = remove }) } }
        compose.onNodeWithTag("profile-save").performClick(); assertFalse(removed)
        compose.onNodeWithText("إزالة صورة الحساب", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("profile-save").performClick(); assertTrue(removed)
    }
    @Test fun localStoryFormNeedsRealTextOrPhotoBeforeSaving() {
        var saved: String? = null
        compose.setContent { NabdTheme { StoryComposer(false, null, {}, { text, _ -> saved = text }) } }
        compose.onNodeWithText("حفظ محلياً").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("قصة من المستخدم")
        compose.onNodeWithText("حفظ محلياً").performClick()
        assertEquals("قصة من المستخدم", saved)
    }
}
