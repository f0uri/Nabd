package app.nabd.nativeapp.data

import android.content.Context
import app.nabd.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * صورة (Pin) من Pinterest.
 *
 * **ماشي منشور ديال نبض**: كتقرا حية من API ديال Pinterest، وما كتكتبش فالنسخة،
 * وما كتتحسبش فالمنشورات، وما كتقبلش تعديل ولا تعليق ولا تعزيز.
 */
data class PinItem(
    val id: String,
    val title: String,
    val image: String?,
    val link: String,
    val boardName: String,
)

/** الرموز ديال Pinterest ديال هاد المستخدم: كتخزّن مشفرة فالتليفون. */
data class PinterestTokens(
    val accessToken: String,
    val refreshToken: String,
    /** بـ الميلي ثانية. */
    val expiresAt: Long,
    val userId: String,
    val username: String,
)

class PinterestException(val code: String) : RuntimeException(code)

/**
 * عميل Pinterest API v5 — OAuth بـ PKCE.
 *
 * القواعد اللي كنحترموها:
 *   - **ما كاين حتى سيرڤور ديال نبض**: كلشي كيدار من التليفون.
 *   - التطبيق **ما كيصاوبش** حساب Pinterest (ما كاينش API لهادشي): إلا ما كانش
 *     عند المستخدم حساب، كيصاوبو هو فصفحة Pinterest براسها (بـ «Continue with Google»
 *     إلا بغا)، ومن بعد كيأذن لينا. وعمّرنا ما كنقولو فالواجهة بلّي حنا كنصاوبو الحساب.
 *   - القراية من API الرسمي فقط (pins:read + boards:read)، وما كاين حتى scraping.
 *   - إلا فشل التجديد كتغبر المحتوى ديال Pinterest بصمت: ما كاين حتى كراش،
 *     وما كاين حتى بانير ديال الغلط فالفيد.
 */
object Pinterest {
    /** المخطط الخاص: باين فـ AndroidManifest بـ intent-filter. */
    const val REDIRECT_URI = "nabd://pinterest"
    private const val AUTH_URL = "https://www.pinterest.com/oauth/"
    private const val API = "https://api.pinterest.com/v5"
    private const val SCOPES = "boards:read,pins:read,user_accounts:read"

    /** بلا معرّف وسر ديال العميل ما كاين حتى دخول: الزر كيولّي معطّل. */
    val configured: Boolean
        get() = BuildConfig.PINTEREST_CLIENT_ID.isNotBlank() && BuildConfig.PINTEREST_CLIENT_SECRET.isNotBlank()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun randomVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** SHA-256 ديال الـ verifier، مشفّر base64url بلا padding — هادا هو PKCE. */
    private fun challengeOf(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    /**
     * كيوجد رابط الصفحة الرسمية ديال Pinterest.
     * الـ [verifier] خاصو يتخزّن فاليد ديال الطلب ويترجع مع الكود.
     */
    fun authorizeUrl(): Pair<String, String> {
        if (!configured) throw PinterestException("PINTEREST_NOT_CONFIGURED")
        val verifier = randomVerifier()
        val state = randomVerifier().take(24)
        val url = AUTH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", BuildConfig.PINTEREST_CLIENT_ID)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", SCOPES)
            .addQueryParameter("code_challenge", challengeOf(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .build()
        return Pair(url.toString(), verifier)
    }

    /** كيبدّل الكود برموز. كيتطلب الـ client_secret: هادشي معروف فتطبيقات الموبايل. */
    suspend fun exchange(code: String, verifier: String): PinterestTokens = withContext(Dispatchers.IO) {
        if (!configured) throw PinterestException("PINTEREST_NOT_CONFIGURED")
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", BuildConfig.PINTEREST_CLIENT_ID)
            .add("client_secret", BuildConfig.PINTEREST_CLIENT_SECRET)
            .add("code_verifier", verifier)
            .build()
        val json = post("$API/oauth/token", body, null)
        finish(json, json.optString("refresh_token"))
    }

    /** الوصول 30 يوم، التجديد 60: كل مرة كنجددو كنجيبو رموز جديدة. */
    suspend fun refresh(refreshToken: String): PinterestTokens = withContext(Dispatchers.IO) {
        if (!configured) throw PinterestException("PINTEREST_NOT_CONFIGURED")
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", BuildConfig.PINTEREST_CLIENT_ID)
            .add("client_secret", BuildConfig.PINTEREST_CLIENT_SECRET)
            .build()
        val json = post("$API/oauth/token", body, null)
        finish(json, json.optString("refresh_token").ifBlank { refreshToken })
    }

    /** كيفكّ الرموز وكيزيد الملف الشخصي (id واسم المستخدم) من `/v5/user_account`. */
    private suspend fun finish(json: JSONObject, refreshToken: String): PinterestTokens {
        val access = json.optString("access_token")
        if (access.isBlank()) throw PinterestException("PINTEREST_ERROR")
        if (refreshToken.isBlank()) throw PinterestException("PINTEREST_NO_REFRESH")
        val profile = get("$API/user_account", access)
        // API ديال Pinterest ما كترجّعش الإيميل: هادا سباش كنخدمو ببريد وهمي.
        return PinterestTokens(
            accessToken = access,
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 2592000L) * 1000L,
            userId = profile.optString("id"),
            username = profile.optString("username"),
        )
    }

    /** لوحات المستخدم براسو. Trial access كاتيح هادشي، وهو كافي للتصميم ديالنا. */
    suspend fun boards(accessToken: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val json = get("$API/boards?page_size=25", accessToken)
        json.optJSONArray("items")?.objects()?.map {
            Pair(it.optString("id"), it.optString("name").ifBlank { "لوحة" })
        } ?: emptyList()
    }

    /** صور لوحة وحدة، بـ التصفح (bookmark) — ما كنجيبوش كلشي دفعة وحدة. */
    suspend fun boardPins(accessToken: String, boardId: String, bookmark: String? = null): Pair<List<PinItem>, String?> =
        withContext(Dispatchers.IO) {
            val url = "$API/boards/$boardId/pins?page_size=25".toHttpUrl().newBuilder()
            if (!bookmark.isNullOrBlank()) url.addQueryParameter("bookmark", bookmark)
            val json = get(buildString { append(url.build()) }, accessToken)
            val pins = json.optJSONArray("items")?.objects()?.mapNotNull { pinOf(it) } ?: emptyList()
            Pair(pins, json.optJSONObject("paging")?.optString("bookmark")?.takeIf { it.isNotBlank() })
        }

    /**
     * `/v5/search/pins` **كيدور غير فالصور ديال الحساب اللي داخل** — ماشي فـ Pinterest كامل.
     * هادا هو السقف الرسمي ديال API، وكنبينّو للمستخدم بصريح العبارة.
     */
    suspend fun searchOwnPins(accessToken: String, query: String): List<PinItem> = withContext(Dispatchers.IO) {
        val json = get("$API/search/pins?query=${query.urlEncode()}&page_size=25", accessToken)
        json.optJSONArray("items")?.objects()?.mapNotNull { pinOf(it) } ?: emptyList()
    }

    private fun pinOf(o: JSONObject): PinItem? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        val images = o.optJSONObject("media")?.optJSONObject("images")
        val image = images?.optJSONObject("orig")?.optString("url")
            ?: images?.optJSONObject("600x")?.optString("url")
        return PinItem(
            id = id,
            title = o.optString("title").ifBlank { o.optString("description") }.ifBlank { "بدون عنوان" },
            image = image?.takeIf { it.isNotBlank() },
            link = o.optString("link").ifBlank { "https://www.pinterest.com/pin/$id/" },
            boardName = o.optJSONObject("board")?.optString("name") ?: "",
        )
    }

    private fun post(url: String, body: FormBody, token: String?): JSONObject {
        val builder = Request.Builder().url(url).post(body)
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (_: IOException) {
            throw PinterestException("NO_INTERNET")
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                // الرمز سالات صلاحيتو: كنرمزيوها باش المتصل يجدّد.
                if (res.code == 401) throw PinterestException("PINTEREST_UNAUTHORIZED")
                throw PinterestException("PINTEREST_ERROR")
            }
            return runCatching { JSONObject(text) }.getOrNull() ?: throw PinterestException("PINTEREST_ERROR")
        }
    }

    private fun get(url: String, token: String): JSONObject {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        val response = try {
            http.newCall(request).execute()
        } catch (_: IOException) {
            throw PinterestException("NO_INTERNET")
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 401) throw PinterestException("PINTEREST_UNAUTHORIZED")
            // حد المعدّل: كنوقفو بصمت، وما كنعطيو حتى غلطة فالفيد.
            if (res.code == 429) throw PinterestException("PINTEREST_RATE_LIMIT")
            if (!res.isSuccessful) throw PinterestException("PINTEREST_ERROR")
            return runCatching { JSONObject(text) }.getOrNull() ?: throw PinterestException("PINTEREST_ERROR")
        }
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

/**
 * الرموز ديال Pinterest مشفرة فـ Android keystore، نفس الطريقة ديال
 * ServerSession وجلسة التليجرام. كل حساب عندو رموزو (مفتاح = معرّف الحساب).
 */
class PinterestSession(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences("nabd.pinterest.$namespace", Context.MODE_PRIVATE)
    private val crypto = KeystoreAes("nabd.pinterest.$namespace")

    private fun key(accountId: String) = "tokens:$accountId"

    @Synchronized
    fun read(accountId: String): PinterestTokens? {
        val encoded = preferences.getString(key(accountId), null) ?: return null
        val json = crypto.decrypt(encoded)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return PinterestTokens(
            accessToken = json.optString("accessToken"),
            refreshToken = json.optString("refreshToken"),
            expiresAt = json.optLong("expiresAt", 0L),
            userId = json.optString("userId"),
            username = json.optString("username"),
        ).takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
    }

    @Synchronized
    fun write(accountId: String, tokens: PinterestTokens) {
        val json = JSONObject()
            .put("accessToken", tokens.accessToken)
            .put("refreshToken", tokens.refreshToken)
            .put("expiresAt", tokens.expiresAt)
            .put("userId", tokens.userId)
            .put("username", tokens.username)
        preferences.edit().putString(key(accountId), crypto.encrypt(json.toString())).commit()
    }

    /** قطع الربط: كيمحو الرموز ديال هاد الحساب فقط. */
    @Synchronized
    fun clear(accountId: String) {
        preferences.edit().remove(key(accountId)).commit()
    }
}
