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
    /** The comments sheet slides up with a spring: touch only lands once the node is really on screen. */
    private fun waitForTag(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) {
            runCatching {
                val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
                val height = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
                node.boundsInWindow.bottom > 0f && node.boundsInWindow.top >= 0f && node.boundsInWindow.bottom <= height
            }.isSuccess
        }
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
        waitForTag("comment-like-${c.id}")
        compose.onNodeWithTag("comment-like-${c.id}").performClick(); assertEquals(c.id, liked)
        compose.onNodeWithTag("comment-reply-${c.id}").assertExists()
        compose.onNodeWithTag("comment-delete-${c.id}").performClick()
        waitForTag("comment-delete-confirm")
        compose.onNodeWithTag("comment-delete-confirm").performClick(); assertEquals(c.id, deleted)
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
