package app.nabd.nativeapp

import app.nabd.nativeapp.data.ReelPreviews
import app.nabd.nativeapp.data.findReel
import app.nabd.nativeapp.data.reelEmbedFallbackUrl
import app.nabd.nativeapp.data.reelEmbedUrl
import app.nabd.nativeapp.data.reelPreviewFromOEmbed
import app.nabd.nativeapp.data.withoutReel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Detection and cover parsing are pure: no network, no invented data. */
class ReelsTest {
    private val reelEndpointsTriedOnce = 4

    @Test fun readsEveryOfficialReelForm() {
        assertEquals("Cw9_abCDEfg", findReel("شوف هاد https://www.instagram.com/reel/Cw9_abCDEfg/?igsh=abc")!!.id)
        assertEquals("Cw9_abCDEfg", findReel("instagram.com/reels/Cw9_abCDEfg/")!!.id)
        assertEquals("Cw9_abCDEfg", findReel("https://instagr.am/p/Cw9_abCDEfg")!!.id)
        assertEquals("Cw9_abCDEfg", findReel("http://m.instagram.com/reel/Cw9_abCDEfg")!!.id)
    }

    @Test fun ignoresEverythingThatIsNotAReel() {
        assertNull(findReel("https://instagram.com/f0uri/"))
        assertNull(findReel("https://youtube.com/watch?v=abcdefgh"))
        assertNull(findReel("ما كاين تا رابط"))
    }

    @Test fun theCardReplacesTheRawLink() {
        val link = "https://www.instagram.com/reel/Cw9_abCDEfg/?igsh=abc"
        val ref = findReel("شوف  $link  وقل لي رأيك")!!
        assertEquals("شوف وقل لي رأيك", withoutReel("شوف  $link  وقل لي رأيك", ref))
        assertTrue(withoutReel(link, ref).isEmpty())
    }

    @Test fun playsTheOfficialEmbedOnly() {
        assertEquals("https://www.instagram.com/p/Cw9_abCDEfg/embed/captioned/", reelEmbedUrl("Cw9_abCDEfg"))
        assertEquals("https://www.instagram.com/reel/Cw9_abCDEfg/embed/captioned/", reelEmbedFallbackUrl("Cw9_abCDEfg"))
    }

    @Test fun readsTheCoverAndAuthorPublishedByInstagram() {
        val body = """{"thumbnail_url":"https://scontent.cdninstagram.com/v/thumb.jpg","author_name":"f0uri","title":"reel"}"""
        val preview = reelPreviewFromOEmbed("Cw9_abCDEfg", body)!!
        assertEquals("https://scontent.cdninstagram.com/v/thumb.jpg", preview.thumbnail)
        assertEquals("@f0uri", preview.author)
    }

    @Test fun neverInventsACover() {
        assertNull(reelPreviewFromOEmbed("Cw9_abCDEfg", "not json at all"))
        assertNull(reelPreviewFromOEmbed("Cw9_abCDEfg", "{}"))
        assertNull(reelPreviewFromOEmbed("Cw9_abCDEfg", """{"thumbnail_url":"http://insecure.example/thumb.jpg"}"""))
    }

    @Test fun aCoverIsFetchedOnceAndRemembered() {
        var calls = 0
        val body = """{"thumbnail_url":"https://scontent.cdninstagram.com/v/t.jpg","author_name":"f0uri"}"""
        val previews = ReelPreviews { calls++; body }
        val first = runBlocking { previews.forId("Cw9abCDEfgh") }!!
        val second = runBlocking { previews.forId("Cw9abCDEfgh") }!!
        assertEquals(1, calls)
        assertEquals("https://scontent.cdninstagram.com/v/t.jpg", first.thumbnail)
        assertEquals(first, second)
    }

    /** A reel with no cover must stay a miss, never an exception, and never a second lookup. */
    @Test fun aReelWithoutACoverIsRememberedAsAMiss() {
        var calls = 0
        val previews = ReelPreviews { calls++; null }
        runBlocking { assertNull(previews.forId("Cw9abCDEfgh")); assertNull(previews.forId("Cw9abCDEfgh")) }
        assertEquals(reelEndpointsTriedOnce, calls)
    }
}
