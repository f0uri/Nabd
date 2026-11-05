package app.nabd.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Instagram reels are never downloaded, stored or re-broadcast by this app.
 * Only the official id of the reel travels inside a message or a post; the video
 * itself is streamed by Instagram's own embed page inside the app.
 */
data class ReelRef(val id: String, val raw: String)

/** reel/…, reels/…, p/… and the short instagr.am host, with or without a scheme or query. */
private val REEL_LINK = Regex("""(?:https?://)?(?:www\.|m\.)?(?:instagram\.com|instagr\.am)/(?:reel|reels|p|tv)/([A-Za-z0-9_.-]{5,})(?:[/?#][^\s<>]*)?""", RegexOption.IGNORE_CASE)

/** First Instagram reel found in a text, or null when the text carries none. */
fun findReel(text: String): ReelRef? {
    val match = REEL_LINK.find(text) ?: return null
    val id = match.groupValues[1].trim('-', '.', '_')
    if (id.isBlank() || id.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '_' && it != '-' && it != '.' }) return null
    return ReelRef(id, match.value)
}

/** The public page of the reel, used for the official oEmbed lookup only. */
fun reelPageUrl(id: String) = "https://www.instagram.com/reel/$id/"

/** The official Instagram embed page. Video, sound and views stay on Instagram. */
fun reelEmbedUrl(id: String) = "https://www.instagram.com/reel/$id/embed/captioned/"

/** The text of a message or post once the raw link has been replaced by its card. */
fun withoutReel(text: String, ref: ReelRef): String =
    text.replace(ref.raw, " ").replace(Regex("[ \t\u00A0]+"), " ").trim()

/** Cover image and author name as published by Instagram itself. */
data class ReelPreview(val id: String, val author: String?, val thumbnail: String?)

private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

/** The two official oEmbed endpoints, tried in order until one answers. */
fun reelOEmbedUrls(id: String): List<String> {
    val page = encode(reelPageUrl(id))
    return listOf("https://api.instagram.com/oembed/?url=$page&format=json", "https://i.instagram.com/api/v1/oembed/?url=$page&format=json")
}

/** Parses the official oEmbed answer. A missing cover is allowed; a broken answer is not invented. */
fun reelPreviewFromOEmbed(id: String, body: String): ReelPreview? {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
    val thumbnail = json.optString("thumbnail_url").takeIf { it.startsWith("https://") }
    val author = json.optString("author_name").takeIf { it.isNotBlank() }?.let { "@${it.trimStart('@')}" }
    if (thumbnail == null && author == null) return null
    return ReelPreview(id, author, thumbnail)
}

/** Cover lookups are cached for the whole session: one real call per reel, never per render. */
class ReelPreviews internal constructor(private val fetch: suspend (String) -> String?) {
    // A reel without a cover is a miss, not a crash: ConcurrentHashMap holds no nulls.
    private val hits = ConcurrentHashMap<String, ReelPreview>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun forId(id: String): ReelPreview? {
        hits[id]?.let { return it }
        if (id in misses) return null
        val found = runCatching { lookup(id) }.getOrNull()
        if (found != null) hits[id] = found else misses.add(id)
        return found
    }

    private suspend fun lookup(id: String): ReelPreview? {
        for (url in reelOEmbedUrls(id)) {
            val body = runCatching { fetch(url) }.getOrNull() ?: continue
            val parsed = runCatching { reelPreviewFromOEmbed(id, body) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}

/** One small client for cover lookups only; every byte of video stays on Instagram's servers. */
object ReelLibrary {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    val previews by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReelPreviews { url -> withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } }
    }
}
