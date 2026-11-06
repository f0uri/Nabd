package app.nabd.nativeapp.data

import android.content.Context
import android.util.AtomicFile
import app.nabd.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * One vertical clip published on Pixabay. The video is never copied, re-encoded
 * or stored by this app: the player streams it straight from Pixabay's own
 * servers and the photographer is credited under every clip.
 */
data class PixabayVideo(val id: String, val page: String, val video: String, val duration: Int, val author: String, val avatar: String)

/** Themes of the discovery feed. Each refresh asks for the next one in turn. */
val PIXABAY_QUERIES = listOf("nature", "city", "sports", "food")

object Pixabay {
    private const val ENDPOINT = "https://pixabay.com/api/videos/"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    /**
     * The key is injected at build time from the repository secret and never
     * written in the source. Without it the discovery feed simply stays empty
     * instead of inventing content.
     */
    val enabled: Boolean get() = BuildConfig.PIXABAY_API_KEY.isNotBlank()

    /** Short vertical clips only: they loop well and cost the device little. */
    suspend fun videos(query: String, page: Int = 1, perPage: Int = 16): List<PixabayVideo> {
        if (!enabled) return emptyList()
        val url = ENDPOINT + "?key=" + BuildConfig.PIXABAY_API_KEY +
            "&q=" + URLEncoder.encode(query, "UTF-8") +
            "&orientation=vertical&video_type=film&min_duration=3&max_duration=60" +
            "&per_page=$perPage&page=$page&safesearch=true"
        val body = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull()
        }
        return parse(body)
    }

    /** The official answer, read literally: a clip without a file is not a clip. */
    fun parse(body: String?): List<PixabayVideo> {
        val hits = runCatching { JSONObject(body ?: return emptyList()).optJSONArray("hits") }.getOrNull() ?: return emptyList()
        return (0 until hits.length()).mapNotNull { index ->
            val hit = hits.optJSONObject(index) ?: return@mapNotNull null
            val files = hit.optJSONObject("videos") ?: return@mapNotNull null
            val file = files.optJSONObject("medium") ?: files.optJSONObject("small") ?: files.optJSONObject("large")
                ?: return@mapNotNull null
            val url = file.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val id = hit.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PixabayVideo(id, hit.optString("pageURL").takeIf { it.startsWith("https://") }.orEmpty(), url,
                hit.optInt("duration"), hit.optString("user").trim(), hit.optString("userImageURL"))
        }
    }
}

/**
 * The free quota is five thousand lookups an hour for the whole key, so one
 * answer is kept on the device for hours and reused instead of asking again.
 */
object PixabayCache {
    private const val FILE = "nabd-pixabay.json"
    private const val TTL_MS = 6L * 60 * 60 * 1000

    fun save(context: Context, videos: List<PixabayVideo>) {
        try {
            val file = AtomicFile(File(context.filesDir, FILE))
            val stream = file.startWrite()
            stream.write(write(videos).toString().toByteArray())
            file.finishWrite(stream)
        } catch (_: Exception) {
            try { File(context.filesDir, FILE).delete() } catch (_: Exception) { }
        }
    }

    fun load(context: Context): List<PixabayVideo>? = try {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) null else {
            val json = JSONObject(file.readText())
            val fresh = now() - json.optLong("at") < TTL_MS
            if (fresh && json.optBoolean("enabled", true)) json.optJSONArray("clips")?.objects()?.map(::read) else null
        }
    } catch (_: Exception) { null }

    fun clear(context: Context) { try { File(context.filesDir, FILE).delete() } catch (_: Exception) { } }

    private fun write(videos: List<PixabayVideo>) = JSONObject().put("at", now()).put("enabled", true)
        .put("clips", org.json.JSONArray(videos.map { clip -> JSONObject().put("id", clip.id).put("page", clip.page)
            .put("video", clip.video).put("duration", clip.duration).put("author", clip.author).put("avatar", clip.avatar) }))

    private fun read(value: JSONObject) = PixabayVideo(value.getString("id"), value.optString("page"), value.getString("video"),
        value.optInt("duration"), value.optString("author"), value.optString("avatar"))

    private fun now() = System.currentTimeMillis()
}
