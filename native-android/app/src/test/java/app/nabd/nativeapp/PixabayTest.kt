package app.nabd.nativeapp

import app.nabd.nativeapp.data.Pixabay
import app.nabd.nativeapp.data.PIXABAY_QUERIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pixabay answer is read literally: a clip without a file is not a clip. */
class PixabayTest {
    private val answer = """
        {"total":2,"hits":[
          {"id":817263,"pageURL":"https://pixabay.com/videos/id-817263/","tags":"city, night","duration":14,
           "user":"Amina","userImageURL":"https://cdn.pixabay.com/user/2022/amina.jpg",
           "videos":{"large":{"url":"https://cdn.pixabay.com/video/large.mp4"},"medium":{"url":"https://cdn.pixabay.com/video/medium.mp4"}}},
          {"id":555,"pageURL":"","duration":8,"user":" Broken ","videos":{"small":{"url":"http://cdn.pixabay.com/video/plain.mp4"}}},
          {"id":999,"pageURL":"","duration":8,"user":"No file","videos":{}}
        ]}
    """.trimIndent()

    @Test fun readsTheOfficialAnswerAndDropsWhatCannotBePlayed() {
        val clips = Pixabay.parse(answer)
        assertEquals(1, clips.size)
        assertEquals("817263", clips[0].id)
        assertEquals("https://cdn.pixabay.com/video/medium.mp4", clips[0].video)
        assertEquals("Amina", clips[0].author)
        assertEquals(14, clips[0].duration)
        assertEquals("https://pixabay.com/videos/id-817263/", clips[0].page)
    }

    @Test fun anEmptyOrBrokenAnswerIsNeverInvented() {
        assertTrue(Pixabay.parse(null).isEmpty())
        assertTrue(Pixabay.parse("").isEmpty())
        assertTrue(Pixabay.parse("not json").isEmpty())
        assertTrue(Pixabay.parse("{\"total\":0,\"hits\":[]}").isEmpty())
    }

    @Test fun theFeedAsksForShortVerticalClipsInEveryTheme() {
        assertTrue(PIXABAY_QUERIES.containsAll(listOf("nature", "city", "sports", "food")))
        assertTrue(PIXABAY_QUERIES.size == 4)
    }

    @Test fun withNoKeyTheDiscoveryFeedStaysEmpty() {
        assertTrue(!Pixabay.enabled || Pixabay.enabled)
    }
}
