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
 * TikTok videos are never downloaded, stored or re-broadcast by this app. Only
 * the official id travels inside a message; the video itself is streamed by
 * TikTok's own embed page, and every thumbnail comes from TikTok's oEmbed.
 */
data class TikTokRef(val id: String, val raw: String, val short: Boolean = false)

/** https://www.tiktok.com/@user/video/1234567890123456789 with or without a scheme or query. */
private val TIKTOK_LONG = Regex("""(?:https?://)?(?:www\.|m\.)?tiktok\.com/@[\w.\-]+/video/(\d{6,})(?:[/?#][^\s<>]*)?""", RegexOption.IGNORE_CASE)

/** The two short hosts: vm.tiktok.com/XXXX and vt.tiktok.com/XXXX. */
private val TIKTOK_SHORT = Regex("""(?:https?://)?(?:vm|vt)\.tiktok\.com/([\w\-]{4,})(?:[/?#][^\s<>]*)?""", RegexOption.IGNORE_CASE)

/** The canonical /video/{id} form TikTok also answers to. */
private val TIKTOK_IDENTIFIER = Regex("""(?:https?://)?(?:www\.|m\.)?tiktok\.com/(?:video|v)/(\d{6,})(?:[/?#][^\s<>]*)?""", RegexOption.IGNORE_CASE)

/** First TikTok video found in a text, or null when the text carries none. */
fun findTikTok(text: String): TikTokRef? {
    TIKTOK_LONG.find(text)?.let { return TikTokRef(it.groupValues[1], it.value) }
    TIKTOK_IDENTIFIER.find(text)?.let { return TikTokRef(it.groupValues[1], it.value) }
    TIKTOK_SHORT.find(text)?.let { return TikTokRef(it.groupValues[1], it.value, short = true) }
    return null
}

/** The public page of the video, the only thing the official oEmbed is asked about. */
fun tikTokPageUrl(id: String) = "https://www.tiktok.com/video/$id"
/** The official embed page. Sound, views and likes stay on TikTok. */
fun tikTokEmbedUrl(id: String) = "https://www.tiktok.com/embed/v2/$id"
/** The text of a message once the raw link has been replaced by its card. */
fun withoutTikTok(text: String, ref: TikTokRef): String =
    text.replace(ref.raw, " ").replace(Regex("[ \t\u00A0]+"), " ").trim()

/** Cover, author and title exactly as TikTok publishes them. */
data class TikTokPreview(val id: String, val author: String?, val thumbnail: String?, val title: String?)

private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

/** One lookup, cached for the whole session: never one call per render. */
class TikTokPreviews internal constructor(private val fetch: suspend (String) -> String?) {
    private val hits = ConcurrentHashMap<String, TikTokPreview>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun forId(id: String): TikTokPreview? {
        hits[id]?.let { return it }
        if (id in misses) return null
        val found = runCatching { lookup(id) }.getOrNull()
        if (found != null) hits[id] = found else misses.add(id)
        return found
    }

    private suspend fun lookup(id: String): TikTokPreview? {
        val body = fetch("https://www.tiktok.com/oembed?url=" + encode(tikTokPageUrl(id))) ?: return null
        return runCatching { tikTokPreviewFromOEmbed(id, body) }.getOrNull()
    }
}

/** The official oEmbed answer, read literally. A missing cover is allowed. */
fun tikTokPreviewFromOEmbed(id: String, body: String): TikTokPreview? {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
    val thumbnail = json.optString("thumbnail_url").takeIf { it.startsWith("https://") }
    val author = json.optString("author_name").takeIf { it.isNotBlank() }?.let { "@${it.trimStart('@')}" }
    val title = json.optString("title").takeIf { it.isNotBlank() }
    if (thumbnail == null && author == null && title == null) return null
    return TikTokPreview(id, author, thumbnail, title)
}

object TikTokLibrary {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()

    private val resolved = ConcurrentHashMap<String, String>()

    val previews by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TikTokPreviews { url -> withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } }
    }

    /**
     * A short link only hides the real id: the redirect chain is followed once
     * and the long number TikTok answers with is kept for the whole session.
     */
    suspend fun resolve(ref: TikTokRef): String? {
        resolved[ref.raw]?.let { return it }
        if (!ref.short) { resolved[ref.raw] = ref.id; return ref.id }
        val found = withContext(Dispatchers.IO) { runCatching { follow(ref.raw) }.getOrNull() }
        if (found != null) resolved[ref.raw] = found
        return found
    }

    private fun follow(start: String): String? {
        val url = if (start.startsWith("http")) start else "https://$start"
        runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                TIKTOK_LONG.find(response.request.url.toString())?.groupValues?.get(1)?.let { return it }
                TIKTOK_IDENTIFIER.find(response.request.url.toString())?.groupValues?.get(1)?.let { return it }
            }
        }
        // Some short links answer with a page instead of a redirect.
        return runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val final = response.request.url.toString()
                TIKTOK_LONG.find(final)?.groupValues?.get(1)
                    ?: TIKTOK_IDENTIFIER.find(final)?.groupValues?.get(1)
                    ?: Regex("\"id\"\\s*:\\s*\"(\\d{6,})\"").find(body)?.groupValues?.get(1)
                    ?: Regex("/video/(\\d{6,})").find(body)?.groupValues?.get(1)
            }
        }.getOrNull()
    }
}
