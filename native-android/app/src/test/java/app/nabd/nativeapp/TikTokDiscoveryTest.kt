package app.nabd.nativeapp

import app.nabd.nativeapp.data.TikTokDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The discovery list is read literally: only links TikTok can be asked about survive. */
class TikTokDiscoveryTest {
    private val seed = """{"schema":1,"clips":[
        {"url":"https://www.tiktok.com/@mountainfacts/video/7567518417930964254","theme":"nature"},
        {"url":"https://www.tiktok.com/@foodi.ma/video/7368519385633262891","theme":"food"},
        {"url":"https://vm.tiktok.com/ZShoRT/","theme":"short"},
        {"url":"https://www.tiktok.com/@somebody","theme":"profile"},
        {"url":"not a link","theme":"junk"}]}"""

    @Test fun keepsOnlyRealVideos() {
        val clips = TikTokDiscovery.readSeed(seed)
        assertEquals(3, clips.size)
        assertEquals("nature", clips.first().second)
    }

    @Test fun ignoresBrokenJson() {
        assertTrue(TikTokDiscovery.readSeed("").isEmpty())
        assertTrue(TikTokDiscovery.readSeed("{}").isEmpty())
        assertTrue(TikTokDiscovery.readSeed("not json at all").isEmpty())
    }

    @Test fun keepsTheOrderOfTheFile() {
        val clips = TikTokDiscovery.readSeed(seed)
        assertTrue(clips[0].first.endsWith("7567518417930964254"))
        assertTrue(clips[1].first.endsWith("7368519385633262891"))
    }

    @Test fun theBundledListIsUsable() {
        val json = java.io.File("src/main/assets/reels-seed.json").readText()
        val clips = TikTokDiscovery.readSeed(json)
        assertTrue(clips.size >= 12)
        assertTrue(clips.all { it.second.isNotBlank() })
        assertTrue(clips.distinctBy { it.first }.size == clips.size)
    }
}
