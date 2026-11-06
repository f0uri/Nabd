package app.nabd.nativeapp

import app.nabd.nativeapp.data.findTikTok
import app.nabd.nativeapp.data.tikTokEmbedUrl
import app.nabd.nativeapp.data.tikTokPageUrl
import app.nabd.nativeapp.data.tikTokPreviewFromOEmbed
import app.nabd.nativeapp.data.withoutTikTok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detection, short links and the oEmbed answer are pure: no network, no invention. */
class TikTokTest {
    private val longId = "7234567890123456789"

    @Test fun readsTheCanonicalLink() {
        val ref = findTikTok("شوف هاد https://www.tiktok.com/@f0uri/video/$longId?is_copy_url=1")!!
        assertEquals(longId, ref.id)
        assertFalse(ref.short)
    }

    @Test fun readsTheTwoShortHostsAndMarksThemForResolution() {
        val vm = findTikTok("https://vm.tiktok.com/ZMhvXyabc/")!!
        assertEquals("ZMhvXyabc", vm.id)
        assertTrue(vm.short)
        val vt = findTikTok("vt.tiktok.com/ZSjkLmno/")!!
        assertEquals("ZSjkLmno", vt.id)
        assertTrue(vt.short)
        assertTrue(findTikTok("https://www.tiktok.com/video/$longId")!!.id == longId)
    }

    @Test fun ignoresEverythingThatIsNotAVideo() {
        assertNull(findTikTok("https://www.tiktok.com/@f0uri"))
        assertNull(findTikTok("https://instagram.com/reel/Cw9_abCDEfg/"))
        assertNull(findTikTok("ما كاين تا رابط"))
    }

    @Test fun theCardReplacesTheLinkAndKeepsTheRestOfTheMessage() {
        val text = "شوف هاد https://www.tiktok.com/@f0uri/video/$longId عجبني"
        val ref = findTikTok(text)!!
        assertEquals("شوف هاد عجبني", withoutTikTok(text, ref))
    }

    @Test fun theOfficialUrlsAreUsed() {
        assertEquals("https://www.tiktok.com/embed/v2/$longId", tikTokEmbedUrl(longId))
        assertEquals("https://www.tiktok.com/video/$longId", tikTokPageUrl(longId))
    }

    @Test fun theOEmbedAnswerIsReadLiterally() {
        val answer = """{"thumbnail_url":"https://p16-sign.tiktokcdn.com/cover.jpg","author_name":"f0uri","title":"نبض"}"""
        val preview = tikTokPreviewFromOEmbed(longId, answer)!!
        assertEquals("@f0uri", preview.author)
        assertEquals("https://p16-sign.tiktokcdn.com/cover.jpg", preview.thumbnail)
        assertEquals("نبض", preview.title)
        assertNull(tikTokPreviewFromOEmbed(longId, "not json"))
        assertNull(tikTokPreviewFromOEmbed(longId, "{}"))
    }
}
