package app.nabd.nativeapp.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** One clip of the discovery feed: a real TikTok video and what TikTok says about it. */
data class TikTokClip(val id: String, val url: String, val theme: String, val author: String, val thumbnail: String, val title: String)

/**
 * The curated list lives in the repository, so the discovery feed can be
 * refreshed without publishing a new release. A copy travels inside the app so
 * the very first open works even before the network answers.
 */
private const val SEED_REMOTE = "https://raw.githubusercontent.com/f0uri/Nabd/main/native-android/reels-seed.json"
private const val SEED_ASSET = "reels-seed.json"
private const val SEED_CACHE = "nabd-reel-seeds.json"
private const val CLIP_CACHE = "nabd-reel-clips.json"
/** The list is re-read a few times a day, never once per screen. */
private const val CLIP_TTL_MS = 6L * 60 * 60 * 1000

object TikTokDiscovery {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    /** The seed file, read literally: only links TikTok can be asked about are kept. */
    fun readSeed(json: String): List<Pair<String, String>> {
        val array = runCatching { JSONObject(json).optJSONArray("clips") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val url = entry.optString("url")
            if (findTikTok(url) == null) null else url to entry.optString("theme", "")
        }
    }

    /** The cached copy when it exists, the bundled one otherwise. */
    fun seeds(context: Context): List<Pair<String, String>> {
        val cached = runCatching {
            val file = File(context.filesDir, SEED_CACHE)
            if (file.exists()) readSeed(file.readText()).takeIf { it.isNotEmpty() } else null
        }.getOrNull()
        if (cached != null) return cached
        return runCatching { context.assets.open(SEED_ASSET).bufferedReader().use { readSeed(it.readText()) } }.getOrDefault(emptyList())
    }

    /** Downloads the list from the repository so it can change without a release. */
    suspend fun refreshSeed(context: Context) = withContext(Dispatchers.IO) {
        val body = runCatching {
            client.newCall(Request.Builder().url(SEED_REMOTE).get().build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext
        val list = readSeed(body)
        if (list.isEmpty()) return@withContext
        try {
            val file = AtomicFile(File(context.filesDir, SEED_CACHE))
            val stream = file.startWrite()
            stream.write(body.toByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            try { File(context.filesDir, SEED_CACHE).delete() } catch (_: Exception) { }
        }
    }

    /**
     * Every clip is asked about before it is shown: the official oEmbed answers
     * with the cover and the author, and a link TikTok no longer answers for is
     * dropped instead of being replaced by a placeholder.
     */
    suspend fun clips(context: Context, refresh: Boolean = false): List<TikTokClip> {
        val cached = cached(context)
        if (cached != null && !refresh) return cached
        if (refresh) refreshSeed(context)
        val found = mutableListOf<TikTokClip>()
        for ((url, theme) in seeds(context)) {
            val ref = findTikTok(url) ?: continue
            val id = runCatching { TikTokLibrary.resolve(ref) }.getOrNull() ?: continue
            val preview = runCatching { TikTokLibrary.previews.forId(id) }.getOrNull() ?: continue
            found += TikTokClip(id, url, theme, preview.author ?: "تيك توك", preview.thumbnail.orEmpty(), preview.title.orEmpty())
        }
        if (found.isNotEmpty()) save(context, found)
        return found
    }

    fun cached(context: Context): List<TikTokClip>? = try {
        val file = File(context.filesDir, CLIP_CACHE)
        if (!file.exists()) null else {
            val json = JSONObject(file.readText())
            if (System.currentTimeMillis() - json.optLong("at") > CLIP_TTL_MS) null
            else {
                val array = json.optJSONArray("clips")
                if (array == null) null else (0 until array.length()).mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    TikTokClip(entry.getString("id"), entry.optString("url"), entry.optString("theme"),
                        entry.optString("author"), entry.optString("thumbnail"), entry.optString("title"))
                }.takeIf { it.isNotEmpty() }
            }
        }
    } catch (_: Exception) { null }

    fun save(context: Context, clips: List<TikTokClip>) {
        try {
            val file = AtomicFile(File(context.filesDir, CLIP_CACHE))
            val stream = file.startWrite()
            stream.write(JSONObject().put("at", System.currentTimeMillis())
                .put("clips", JSONArray(clips.map { clip -> JSONObject().put("id", clip.id).put("url", clip.url)
                    .put("theme", clip.theme).put("author", clip.author).put("thumbnail", clip.thumbnail).put("title", clip.title) }))
                .toString().toByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            try { File(context.filesDir, CLIP_CACHE).delete() } catch (_: Exception) { }
        }
    }
}
