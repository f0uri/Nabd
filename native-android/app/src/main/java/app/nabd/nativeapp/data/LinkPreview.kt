package app.nabd.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** What a link really says about itself: the Open Graph tags of its own page. */
data class LinkCard(val url: String, val host: String, val title: String, val description: String, val image: String?)

private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** The first real link of a text, without the trailing punctuation of a sentence. */
fun findLink(text: String): String? =
    URL_PATTERN.find(text)?.value?.trimEnd('.', ',', ';', ':', ')', ']', '}', '»', '"', '\'')

/**
 * Link previews are read straight from the page the sender linked: the app asks
 * for the document, stops after 128 KB and keeps only the Open Graph tags. No
 * script ever runs, and nothing is stored but the text of the card itself.
 */
object LinkPreview {
    private const val LIMIT = 128 * 1024
    private const val TIMEOUT = 6000
    private val cards = ConcurrentHashMap<String, LinkCard>()
    private val missed = ConcurrentHashMap.newKeySet<String>()

    fun cached(url: String): LinkCard? = cards[url]

    suspend fun fetch(url: String): LinkCard? {
        cards[url]?.let { return it }
        if (url in missed) return null
        val card = withContext(Dispatchers.IO) { runCatching { read(url) }.getOrNull() }
        if (card == null) missed.add(url) else cards[url] = card
        return card
    }

    private fun read(url: String): LinkCard? {
        val target = runCatching { URL(url) }.getOrNull() ?: return null
        if (target.protocol !in listOf("http", "https")) return null
        val connection = (target.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT; readTimeout = TIMEOUT; instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) Nabd")
            setRequestProperty("Accept", "text/html")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val type = connection.contentType.orEmpty()
            if (type.isNotBlank() && !type.contains("html", true) && !type.contains("xml", true)) return null
            val head = connection.inputStream.use { input ->
                val buffer = ByteArray(LIMIT)
                var total = 0
                while (total < LIMIT) { val read = input.read(buffer, total, LIMIT - total); if (read <= 0) break; total += read }
                String(buffer, 0, total, Charsets.UTF_8)
            }
            val tags = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(head).map { attributes(it.value) }.toList()
            val pick = { key: String -> tags.firstNotNullOfOrNull { tag ->
                val name = tag["property"] ?: tag["name"] ?: return@firstNotNullOfOrNull null
                if (name.equals(key, true)) tag["content"] else null } }
            val title = decode((pick("og:title") ?: pick("twitter:title") ?: Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)).orEmpty())
            val description = decode((pick("og:description") ?: pick("twitter:description") ?: pick("description")).orEmpty())
            val image = (pick("og:image") ?: pick("twitter:image"))?.let { absolute(url, decode(it)) }
            if (title.isBlank() && description.isBlank() && image == null) null
            else LinkCard(url, target.host.removePrefix("www."), title.ifBlank { target.host }, description.take(220), image)
        } finally { connection.disconnect() }
    }

    private fun attributes(tag: String): Map<String, String> =
        Regex("""([a-zA-Z:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""").findAll(tag).associate { match ->
            match.groupValues[1].lowercase() to (match.groupValues[2].ifEmpty { match.groupValues[3] })
        }

    private fun absolute(page: String, link: String): String? {
        if (link.startsWith("http")) return link
        val base = runCatching { URL(page) }.getOrNull() ?: return null
        return runCatching { URL(base, link).toString() }.getOrNull()
    }

    private fun decode(value: String): String = value.trim()
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace(Regex("""\s+"""), " ")
}
