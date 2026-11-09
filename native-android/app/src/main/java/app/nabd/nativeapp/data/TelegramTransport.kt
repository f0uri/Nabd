package app.nabd.nativeapp.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

/** Same explicit, extractable-bot-token transport as the existing application.
 * No logging interceptor, public-channel fallback or automatic initialization. */
class TelegramTransport(private val token: String, private val chatId: String) : VaultTransport {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private var botVerified = false
    private val documents = object : LinkedHashMap<String, String>(3, .75f, true) { override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 2 }
    init {
        require(token.matches(Regex("[0-9]{5,}:[A-Za-z0-9_-]{20,}")) && chatId.matches(Regex("-?[1-9][0-9]{0,19}"))) { "TELEGRAM_CONFIG_REQUIRED" }
    }
    private fun call(method: String, payload: JSONObject = JSONObject()): JSONObject {
        try {
            val request = Request.Builder().url("https://api.telegram.org/bot$token/$method")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful)
                val json = JSONObject(readBounded(response.body!!.byteStream(), MAX_VAULT_BYTES).toString(Charsets.UTF_8))
                require(json.getBoolean("ok"))
                return if (json.optJSONObject("result") != null) json.getJSONObject("result") else JSONObject().put("value", json.get("result"))
            }
        } catch (e: java.io.IOException) { error("TELEGRAM_REQUEST_FAILED") }
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
        val meta = call("getFile", JSONObject().put("file_id", fileId)); val path = meta.optString("file_path")
        require(path.matches(Regex("[A-Za-z0-9_./-]+")) && !path.startsWith('/') && path.split('/').none { it == ".." || it == "." } && meta.has("file_size") && meta.getLong("file_size") in 0..MAX_VAULT_BYTES.toLong()) { "TELEGRAM_INVALID_VAULT" }
        try {
            // The vault can be up to 2 MB and the user's connection can be
            // slow: a full download gets a longer per-call timeout instead
            // of failing at 30s and looking like "no connection".
            client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build().newCall(Request.Builder().url("https://api.telegram.org/file/bot$token/$path").build()).execute().use { response ->
                require(response.isSuccessful)
                val body = response.body!!
                require(body.contentLength() <= MAX_VAULT_BYTES)
                val bytes = readBounded(body.byteStream(), MAX_VAULT_BYTES)
                require(bytes.size <= MAX_VAULT_BYTES)
                val text = bytes.toString(Charsets.UTF_8)
                val json = JSONObject(text); documents[fileId] = text
                return json
            }
        } catch (e: java.io.IOException) { error("TELEGRAM_READ_FAILED") }
    }
    override fun upload(json: String): Long {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_VAULT_BYTES) { "TELEGRAM_VAULT_FULL" }
        try {
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId).addFormDataPart("caption", "nabd-direct-telegram-v1")
                .addFormDataPart("document", "nabd-vault.json", bytes.toRequestBody("application/json".toMediaType())).build()
            // Same long timeout as the download: a slow 2 MB upload is not
            // a lost connection.
            client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build().newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendDocument").post(multipart).build()).execute().use { response ->
                require(response.isSuccessful)
                val jsonResponse = JSONObject(readBounded(response.body!!.byteStream(), MAX_VAULT_BYTES).toString(Charsets.UTF_8)); require(jsonResponse.getBoolean("ok"))
                val result = jsonResponse.getJSONObject("result")
                require(result.getLong("message_id") > 0 && result.getJSONObject("document").getString("file_name") == "nabd-vault.json" && result.getJSONObject("document").getString("file_id").isNotEmpty())
                return result.getLong("message_id")
            }
        } catch (e: java.io.IOException) { error("TELEGRAM_WRITE_FAILED") }
    }
    /**
     * The vault only ever holds text, so a picture gets a document of its own:
     * one upload, and the vault keeps a small preview plus the id this returns.
     */
    override fun uploadMedia(bytes: ByteArray, name: String, mime: String): String {
        require(bytes.size in 1..MAX_MEDIA_BYTES && name.matches(Regex("[A-Za-z0-9._-]{1,64}")) && mime.matches(Regex("[a-z]+/[a-z0-9.+-]{1,32}"))) { "VALIDATION" }
        try {
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId).addFormDataPart("caption", MEDIA_CAPTION)
                .addFormDataPart("document", name, bytes.toRequestBody(mime.toMediaType())).build()
            client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build().newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendDocument").post(multipart).build()).execute().use { response ->
                require(response.isSuccessful)
                val json = JSONObject(readBounded(response.body!!.byteStream(), MAX_VAULT_BYTES).toString(Charsets.UTF_8)); require(json.getBoolean("ok"))
                val document = json.getJSONObject("result").getJSONObject("document")
                val id = document.getString("file_id")
                require(id.length in 8..256 && document.getLong("file_size") <= MAX_MEDIA_BYTES.toLong()) { "VALIDATION" }
                return id
            }
        } catch (e: java.io.IOException) { error("TELEGRAM_WRITE_FAILED") }
    }

    /**
     * Reads a picture back. Telegram is asked for the path every time because
     * file paths expire; the bytes themselves are cached on the device.
     */
    override fun fetchMedia(fileId: String): ByteArray? {
        if (!fileId.matches(Regex("[A-Za-z0-9_-]{8,256}"))) return null
        val meta = runCatching { call("getFile", JSONObject().put("file_id", fileId)) }.getOrNull() ?: return null
        val path = meta.optString("file_path")
        if (!path.matches(Regex("[A-Za-z0-9_./-]+")) || path.startsWith("/") || path.split('/').any { it == ".." }) return null
        val size = meta.optLong("file_size", 0)
        if (size <= 0 || size > MAX_MEDIA_BYTES) return null
        return try {
            client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build().newCall(Request.Builder().url("https://api.telegram.org/file/bot$token/$path").build()).execute().use { response ->
                if (!response.isSuccessful) null else {
                    val bytes = readBounded(response.body!!.byteStream(), MAX_MEDIA_BYTES)
                    bytes.takeIf { it.size == size.toInt() }
                }
            }
        } catch (e: java.io.IOException) { null }
    }

    override fun pin(messageId: Long) {
        require(call("pinChatMessage", JSONObject().put("chat_id", chatId).put("message_id", messageId).put("disable_notification", true)).optBoolean("value")) { "TELEGRAM_WRITE_FAILED" }
    }
}
