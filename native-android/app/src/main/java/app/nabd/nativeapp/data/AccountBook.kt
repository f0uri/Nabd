package app.nabd.nativeapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One account a real session was issued for on this device. The token is the very
 * same session string the shared vault already knows; no password is ever stored.
 */
data class SavedAccount(val id: String, val name: String, val username: String, val avatar: String?, val token: String, val used: Long = 0)

/** How many accounts the switcher keeps, and how much avatar data each may hold. */
const val ACCOUNT_LIMIT = 6
const val ACCOUNT_AVATAR_LIMIT = 24_000

/** Pure codec, so the book keeps its guarantees without needing an Android context. */
internal fun decodeBook(raw: String): List<SavedAccount> = runCatching {
    val array = JSONArray(raw)
    val result = mutableListOf<SavedAccount>()
    for (index in 0 until array.length()) {
        val item = array.getJSONObject(index)
        val id = item.getString("id")
        val token = item.getString("token")
        if (!uid(id) || token.isBlank()) continue
        result += SavedAccount(id, item.optString("name").take(40).ifBlank { "مستخدم" }, item.optString("username"),
            item.optString("avatar").takeIf { it.isNotBlank() }, token, item.optLong("used"))
    }
    result
}.getOrDefault(emptyList())

/** Newest first, one row per account, never more than the limit. */
internal fun encodeBook(entries: List<SavedAccount>, now: Long, limit: Int = ACCOUNT_LIMIT): String {
    val kept = entries.distinctBy { it.id }
        .sortedWith(compareByDescending<SavedAccount> { if (it.used > 0) it.used else now }.thenBy { it.name })
        .take(limit)
    return JSONArray().apply {
        kept.forEach { entry -> put(JSONObject().put("id", entry.id).put("name", entry.name).put("username", entry.username)
            .put("avatar", entry.avatar?.take(ACCOUNT_AVATAR_LIMIT).orEmpty()).put("token", entry.token).put("used", entry.used)) }
    }.toString()
}

/**
 * Local, keystore-encrypted address book of the accounts that already signed in
 * on this device. It only ever stores the session the server issued — switching
 * puts that same session back, and an expired one is dropped instead of faked.
 */
class AccountBook(context: Context, private val namespace: String) {
    init { require(namespace.matches(Regex("[a-f0-9]{24}"))) }
    private val crypto = KeystoreAes("nabd.native.accounts.$namespace")
    private val preferences = context.getSharedPreferences("nabd.native.accounts.$namespace", Context.MODE_PRIVATE)

    private fun valid(value: String) = value.matches(Regex("nabd-tg:$namespace:[a-f0-9]{32}:[a-f0-9]{64}"))

    @Synchronized fun read(): List<SavedAccount> {
        val encoded = preferences.getString("book", null) ?: return emptyList()
        val raw = crypto.decrypt(encoded) ?: return emptyList()
        return decodeBook(raw).filter { valid(it.token) }
    }

    @Synchronized fun save(entry: SavedAccount) {
        val next = listOf(entry.copy(used = System.currentTimeMillis())) + read().filter { it.id != entry.id }
        write(encodeBook(next, System.currentTimeMillis()))
    }

    @Synchronized fun remove(id: String) { write(encodeBook(read().filter { it.id != id }, System.currentTimeMillis())) }

    @Synchronized fun clear() { check(preferences.edit().remove("book").commit()) { "SESSION_STORAGE_ERROR" } }

    private fun write(raw: String) {
        check(preferences.edit().putString("book", crypto.encrypt(raw)).commit()) { "SESSION_STORAGE_ERROR" }
    }
}
