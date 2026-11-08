package app.nabd.nativeapp.data

import org.json.JSONObject

/** Notification families the user can silence one by one. */
val NOTIFY_KINDS = listOf("pulse", "comment", "message", "follow", "story")
val THEME_CHOICES = listOf("system", "light", "dark")
val AUTOPLAY_CHOICES = listOf("always", "wifi", "never")
val VISIBILITY_CHOICES = listOf("public", "friends")
val REACH_CHOICES = listOf("everyone", "friends")

/**
 * Every preference of one account. It lives inside the shared vault, under the
 * account itself, so it travels with the account to any device that signs in.
 */
data class UserSettings(
    val theme: String = "system",
    val language: String = "ar",
    val autoplay: String = "wifi",
    val sounds: Boolean = true,
    val quiet: Boolean = false,
    val notify: Map<String, Boolean> = NOTIFY_KINDS.associateWith { true },
    val posts: String = "public",
    val stories: String = "public",
    val messages: String = "everyone",
    val activeStatus: Boolean = true,
    val loginAlerts: Boolean = true
)

private fun choice(raw: String?, allowed: List<String>, fallback: String) = if (raw != null && raw in allowed) raw else fallback
private fun flag(json: JSONObject, key: String, fallback: Boolean) = if (json.has(key)) json.optBoolean(key, fallback) else fallback

/** Reads the stored preferences, falling back to the default for anything missing or unknown. */
fun readSettings(raw: JSONObject?): UserSettings {
    if (raw == null) return UserSettings()
    val notify = raw.optJSONObject("notify")
    return UserSettings(
        theme = choice(raw.optString("theme").ifBlank { null }, THEME_CHOICES, "system"),
        language = choice(raw.optString("language").ifBlank { null }, listOf("ar", "en"), "ar"),
        autoplay = choice(raw.optString("autoplay").ifBlank { null }, AUTOPLAY_CHOICES, "wifi"),
        sounds = flag(raw, "sounds", true),
        quiet = flag(raw, "quiet", false),
        notify = NOTIFY_KINDS.associateWith { kind -> if (notify != null && notify.has(kind)) notify.optBoolean(kind, true) else true },
        posts = choice(raw.optString("posts").ifBlank { null }, VISIBILITY_CHOICES, "public"),
        stories = choice(raw.optString("stories").ifBlank { null }, VISIBILITY_CHOICES, "public"),
        messages = choice(raw.optString("messages").ifBlank { null }, REACH_CHOICES, "everyone"),
        activeStatus = flag(raw, "activeStatus", true),
        loginAlerts = flag(raw, "loginAlerts", true)
    )
}

/** Writes them back in the same shape, so the vault keeps one stable format. */
fun writeSettings(value: UserSettings): JSONObject = JSONObject()
    .put("theme", value.theme).put("language", value.language).put("autoplay", value.autoplay)
    .put("sounds", value.sounds).put("quiet", value.quiet)
    .put("notify", JSONObject().apply { value.notify.forEach { (kind, on) -> if (kind in NOTIFY_KINDS) put(kind, on) } })
    .put("posts", value.posts).put("stories", value.stories).put("messages", value.messages)
    .put("activeStatus", value.activeStatus).put("loginAlerts", value.loginAlerts)

fun themeLabel(value: String) = when (value) { "dark" -> "ليلي"; "light" -> "نهاري"; else -> "حسب النظام" }
fun autoplayLabel(value: String) = when (value) { "always" -> "دائماً"; "never" -> "أبداً"; else -> "على الواي فاي فقط" }
fun visibilityLabel(value: String) = if (value == "friends") "الأصدقاء فقط" else "عام"
fun reachLabel(value: String) = if (value == "friends") "الأصدقاء فقط" else "الجميع"
fun notifyLabel(kind: String) = when (kind) {
    "pulse" -> "التفاعلات والنبضات"
    "comment" -> "التعليقات"
    "message" -> "الرسائل"
    "follow" -> "المتابعون الجدد"
    else -> "القصص"
}
