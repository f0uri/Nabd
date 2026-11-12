package app.nabd.nativeapp.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

interface VaultTransport {
    fun chat(): JSONObject
    fun read(fileId: String): JSONObject
    fun upload(json: String): Long
    fun pin(messageId: Long)
    /** A photo leaves the vault and becomes a Telegram document of its own. Returns its file id. */
    fun uploadMedia(bytes: ByteArray, name: String, mime: String): String
    /** The bytes of that document, or null when Telegram no longer answers for it. */
    fun fetchMedia(fileId: String): ByteArray?
}

/** Explicit bot-token transport. No logging interceptor, public-channel fallback or automatic initialization. */
class TelegramTransport(private val token: String, private val chatId: String) : VaultTransport {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private var botVerified = false
    private val documents = object : LinkedHashMap<String, String>(3, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 2
    }

    init {
        require(token.matches(Regex("[0-9]{5,}:[A-Za-z0-9_-]{20,}")) && chatId.matches(Regex("-?[1-9][0-9]{0,19}"))) { "TELEGRAM_CONFIG_REQUIRED" }
    }

    /** Retry a Telegram FLOOD_WAIT without exposing API error strings or credentials to callers/logs. */
    private fun <T> withFloodWait(operation: String, block: () -> T): T {
        var waits = 0
        var previousSeconds = 0
        while (true) {
            try { return block() } catch (e: TelegramFloodWaitException) {
                waits++
                val seconds = if (waits == 1) e.seconds else maxOf(e.seconds, (previousSeconds.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                logFloodWait(operation, seconds)
                previousSeconds = seconds
                try { java.lang.Thread.sleep(seconds.toLong() * 1000L) }
                catch (interrupted: InterruptedException) {
                    java.lang.Thread.currentThread().interrupt()
                    throw IllegalStateException("TELEGRAM_REQUEST_FAILED")
                }
                // One backoff series has at most five attempts; if Telegram still
                // refuses the request, keep it queued and start another series.
                if (waits >= MAX_ATTEMPTS) { waits = 0; previousSeconds = 0 }
            }
        }
    }

    private fun logFloodWait(operation: String, seconds: Int) {
        // Operation comes only from fixed Telegram method names; never log request URLs or payloads.
        Log.w(TAG, "${Instant.now()} FLOOD_WAIT operation=$operation seconds=$seconds")
    }

    private fun call(method: String, payload: JSONObject = JSONObject()): JSONObject = withFloodWait(method) {
        val request = Request.Builder().url("https://api.telegram.org/bot$token/$method")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(request).execute().use(::parseResult)
        } catch (e: TelegramFloodWaitException) { throw e }
        catch (e: IOException) { throw IllegalStateException("TELEGRAM_REQUEST_FAILED") }
    }

    private fun parseResult(response: Response): JSONObject {
        val text = response.body?.let { readBounded(it.byteStream(), MAX_VAULT_BYTES).toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("TELEGRAM_REQUEST_FAILED")
        val root = try { JSONObject(text) } catch (_: Exception) {
            val wait = parseFloodWaitSeconds(text) ?: throw IllegalStateException("TELEGRAM_REQUEST_FAILED")
            throw TelegramFloodWaitException(wait, "telegram-api")
        }
        if (!response.isSuccessful || !root.optBoolean("ok")) throw apiFailure(root, text)
        return root.optJSONObject("result") ?: JSONObject().put("value", root.opt("result"))
    }

    private fun apiFailure(root: JSONObject, raw: String): RuntimeException {
        val description = root.optString("description", "")
        val retryAfter = root.optJSONObject("parameters")?.optInt("retry_after", 0)?.takeIf { it > 0 }
        val wait = retryAfter ?: parseFloodWaitSeconds(description) ?: parseFloodWaitSeconds(raw)
        if (wait != null && wait > 0) return TelegramFloodWaitException(wait, "telegram-api")
        return IllegalStateException("TELEGRAM_REQUEST_FAILED")
    }

    override fun chat(): JSONObject {
        if (!botVerified) {
            val bot = call("getMe")
            require(bot.optBoolean("is_bot") && bot.optLong("id").toString() == token.substringBefore(':')) { "TELEGRAM_INVALID_BOT" }
            botVerified = true
        }
        val chat = call("getChat", JSONObject().put("chat_id", chatId))
        require(chat.optLong("id").toString() == chatId && chat.optString("type") in listOf("private", "channel", "group", "supergroup")) { "TELEGRAM_INVALID_CHAT" }
        require(chat.optString("type") == "private" || (chat.optString("username").isEmpty() && (chat.optJSONArray("active_usernames")?.length() ?: 0) == 0)) { "TELEGRAM_PRIVATE_CHAT_REQUIRED" }
        return chat
    }

    override fun read(fileId: String): JSONObject {
        documents[fileId]?.let { return JSONObject(it) }
        val text = downloadDocument(fileId, MAX_VAULT_BYTES)
        val json = try { JSONObject(text) } catch (_: Exception) { throw IllegalArgumentException("TELEGRAM_INVALID_VAULT") }
        documents[fileId] = text
        return json
    }

    private fun downloadDocument(fileId: String, maxBytes: Int): String = downloadDocumentBytes(fileId, maxBytes).toString(Charsets.UTF_8)

    private fun downloadDocumentBytes(fileId: String, maxBytes: Int): ByteArray {
        val meta = call("getFile", JSONObject().put("file_id", fileId))
        val path = meta.optString("file_path")
        val expected = meta.optLong("file_size", -1)
        require(path.matches(Regex("[A-Za-z0-9_./-]+")) && !path.startsWith('/') && path.split('/').none { it == ".." || it == "." } && expected in 0..maxBytes.toLong()) { "TELEGRAM_INVALID_VAULT" }
        val bytes = downloadPath(path, maxBytes, "downloadFile")
        require(bytes.size == expected.toInt()) { "TELEGRAM_INVALID_VAULT" }
        return bytes
    }

    private fun downloadPath(path: String, maxBytes: Int, operation: String): ByteArray = withFloodWait(operation) {
        val request = Request.Builder().url("https://api.telegram.org/file/bot$token/$path").build()
        try {
            client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                val body = response.body ?: throw IllegalStateException("TELEGRAM_READ_FAILED")
                if (!response.isSuccessful) {
                    val raw = readBounded(body.byteStream(), 64 * 1024).toString(Charsets.UTF_8)
                    val root = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                    throw apiFailure(root, raw)
                }
                require(body.contentLength() <= maxBytes.toLong()) { "TELEGRAM_VAULT_FULL" }
                readBounded(body.byteStream(), maxBytes)
            }
        } catch (e: TelegramFloodWaitException) { throw e }
        catch (e: IOException) { throw IllegalStateException("TELEGRAM_READ_FAILED") }
    }

    override fun upload(json: String): Long {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_VAULT_BYTES) { "TELEGRAM_VAULT_FULL" }
        val result = withFloodWait("sendDocument") { uploadOnce(bytes, "nabd-vault.json", "application/json", "nabd-direct-telegram-v1") }
        val messageId = result.optLong("message_id")
        val document = result.optJSONObject("document") ?: throw IllegalStateException("TELEGRAM_WRITE_FAILED")
        val fileId = document.optString("file_id")
        require(messageId > 0 && fileId.isNotBlank() && document.optLong("file_size", -1) == bytes.size.toLong()) { "TELEGRAM_WRITE_FAILED" }

        // Verify what Telegram actually stored before commit() atomically moves the pin.
        val uploaded = downloadDocumentBytes(fileId, MAX_VAULT_BYTES)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(bytes)
        val actualHash = MessageDigest.getInstance("SHA-256").digest(uploaded)
        require(uploaded.size == bytes.size && MessageDigest.isEqual(expectedHash, actualHash)) { "TELEGRAM_UPLOAD_VERIFY_FAILED" }
        documents[fileId] = uploaded.toString(Charsets.UTF_8)
        return messageId
    }

    private fun uploadOnce(bytes: ByteArray, name: String, mime: String, caption: String): JSONObject {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId).addFormDataPart("caption", caption)
            .addFormDataPart("document", name, bytes.toRequestBody(mime.toMediaType())).build()
        val request = Request.Builder().url("https://api.telegram.org/bot$token/sendDocument").post(multipart).build()
        return try {
            client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build().newCall(request).execute().use(::parseResult)
        } catch (e: TelegramFloodWaitException) { throw e }
        catch (e: IOException) { throw IllegalStateException("TELEGRAM_WRITE_FAILED") }
    }

    /** A photo becomes its own Telegram document; vault writes keep only its preview and file id. */
    override fun uploadMedia(bytes: ByteArray, name: String, mime: String): String {
        require(bytes.size in 1..MAX_MEDIA_BYTES && name.matches(Regex("[A-Za-z0-9._-]{1,64}")) && mime.matches(Regex("[a-z]+/[a-z0-9.+-]{1,32}"))) { "VALIDATION" }
        val result = withFloodWait("sendDocument") { uploadOnce(bytes, name, mime, MEDIA_CAPTION) }
        val document = result.optJSONObject("document") ?: throw IllegalStateException("TELEGRAM_WRITE_FAILED")
        val id = document.optString("file_id")
        require(id.length in 8..256 && document.optLong("file_size", -1) in 1..MAX_MEDIA_BYTES.toLong()) { "VALIDATION" }
        return id
    }

    override fun fetchMedia(fileId: String): ByteArray? {
        if (!fileId.matches(Regex("[A-Za-z0-9_-]{8,256}"))) return null
        return try {
            val meta = call("getFile", JSONObject().put("file_id", fileId))
            val path = meta.optString("file_path"); val size = meta.optLong("file_size", 0)
            if (!path.matches(Regex("[A-Za-z0-9_./-]+")) || path.startsWith('/') || path.split('/').any { it == ".." } || size <= 0 || size > MAX_MEDIA_BYTES) null
            else downloadPath(path, MAX_MEDIA_BYTES, "downloadMedia").takeIf { it.size == size.toInt() }
        } catch (e: TelegramFloodWaitException) { throw e }
        catch (_: Exception) { null }
    }

    override fun pin(messageId: Long) {
        require(call("pinChatMessage", JSONObject().put("chat_id", chatId).put("message_id", messageId).put("disable_notification", true)).optBoolean("value")) { "TELEGRAM_WRITE_FAILED" }
    }

    companion object {
        private const val TAG = "NabdTelegram"
        private const val MAX_ATTEMPTS = 5

        /** Case-insensitive Telegram FLOOD_WAIT / retry-after parsing; returns null for unrelated errors. */
        internal fun parseFloodWaitSeconds(text: String): Int? {
            val patterns = listOf(
                Regex("\\bFLOOD[_\\s-]*WAIT(?:[_\\s:=-]+)(\\d+)\\b", RegexOption.IGNORE_CASE),
                Regex("\\bretry[_\\s-]*after\\s*[:=]?\\s*(\\d+)\\b", RegexOption.IGNORE_CASE),
                Regex("\\bwait\\s+(\\d+)\\s+seconds?\\b", RegexOption.IGNORE_CASE)
            )
            return patterns.firstNotNullOfOrNull { pattern ->
                pattern.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
            }
        }
    }
}

/** Retryable only inside the background operation queue; never includes a credential or response body. */
internal class TelegramFloodWaitException(val seconds: Int, val operation: String) : RuntimeException("TELEGRAM_FLOOD_WAIT")
