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
}

/** Same explicit, extractable-bot-token transport as the existing application.
 * No logging interceptor, public-channel fallback or automatic initialization. */
class TelegramTransport(private val token: String, private val chatId: String) : VaultTransport {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private var botVerified = false
    init {
        require(token.matches(Regex("[0-9]{5,}:[A-Za-z0-9_-]{20,}")) && chatId.matches(Regex("-?[1-9][0-9]{0,19}"))) { "TELEGRAM_CONFIG_REQUIRED" }
    }
    private fun call(method: String, payload: JSONObject = JSONObject()): JSONObject {
        try {
            val request = Request.Builder().url("https://api.telegram.org/bot$token/$method")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful)
                val json = JSONObject(response.body!!.string())
                require(json.getBoolean("ok"))
                return if (json.optJSONObject("result") != null) json.getJSONObject("result") else JSONObject().put("value", json.get("result"))
            }
        } catch (_: Exception) { error("TELEGRAM_REQUEST_FAILED") }
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
        val meta = call("getFile", JSONObject().put("file_id", fileId)); val path = meta.optString("file_path")
        require(path.matches(Regex("[A-Za-z0-9_./-]+")) && !path.startsWith('/') && path.split('/').none { it == ".." || it == "." } && meta.has("file_size") && meta.getLong("file_size") in 0..MAX_VAULT_BYTES.toLong()) { "TELEGRAM_INVALID_VAULT" }
        try {
            client.newCall(Request.Builder().url("https://api.telegram.org/file/bot$token/$path").build()).execute().use { response ->
                require(response.isSuccessful)
                val body = response.body!!
                require(body.contentLength() <= MAX_VAULT_BYTES)
                val source = body.source(); val bytes = source.readByteArray(MAX_VAULT_BYTES.toLong() + 1)
                require(bytes.size <= MAX_VAULT_BYTES)
                return JSONObject(bytes.toString(Charsets.UTF_8))
            }
        } catch (_: Exception) { error("TELEGRAM_READ_FAILED") }
    }
    override fun upload(json: String): Long {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_VAULT_BYTES) { "TELEGRAM_VAULT_FULL" }
        try {
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId).addFormDataPart("caption", "nabd-direct-telegram-v1")
                .addFormDataPart("document", "nabd-vault.json", bytes.toRequestBody("application/json".toMediaType())).build()
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$token/sendDocument").post(multipart).build()).execute().use { response ->
                require(response.isSuccessful)
                val jsonResponse = JSONObject(response.body!!.string()); require(jsonResponse.getBoolean("ok"))
                val result = jsonResponse.getJSONObject("result")
                require(result.getLong("message_id") > 0 && result.getJSONObject("document").getString("file_name") == "nabd-vault.json" && result.getJSONObject("document").getString("file_id").isNotEmpty())
                return result.getLong("message_id")
            }
        } catch (_: Exception) { error("TELEGRAM_WRITE_FAILED") }
    }
    override fun pin(messageId: Long) {
        require(call("pinChatMessage", JSONObject().put("chat_id", chatId).put("message_id", messageId).put("disable_notification", true)).optBoolean("value")) { "TELEGRAM_WRITE_FAILED" }
    }
}
