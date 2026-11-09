package app.nabd.nativeapp.data

import app.nabd.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** الرموز اللي كيرجّعها الخادم: كنترجموها لرسائل عربية مفهومة. */
class ServerAuthException(val code: String) : RuntimeException(code)

/** الملف الشخصي اللي كيفيدو بيه الخادم من بعد تحقق ناجح. */
data class ServerUser(
    /**
     * علامة الجلسة: `"telegram:" + sub` ديال Google. **ماشي** JWT ديال سيرڤور —
     * ما كاين حتى سيرڤور ديال نبض. كتتخزّن مشفرة فـ ServerSession.
     */
    val token: String,
    val id: String,
    val name: String,
    val email: String,
    val username: String,
    val avatarUrl: String?,
    val verified: Boolean,
)

/**
 * كيهضر مع خادم GraphQL ديال نبض (GraphQL Yoga) — من غير `googleSignIn`.
 *
 * الدخول بـ Google **ما كيحتاج حتى سيرڤور ديال نبض**: كيفحقق idToken على
 * التليفون عند Google براسها (`oauth2.googleapis.com/tokeninfo`). بهاد الشي
 * الزر كيخدم واخا يكون `API_URL` خاوي.
 *
 *   googleSignIn      — كيفحقق idToken ديال Google على التليفون (بلا سيرڤور).
 *   sendVerification  — كيصيفط رمز التفعيل (6 أرقام).
 *   verifyEmail       — كيفحقق الرمز وكيعلّم الحساب مفعل.
 *   forgotPassword    — كيصيفط رمز استعادة كلمة المرور.
 *   verifyResetCode   — كيفحقق رمز الاستعادة بلا ما يستهلكو.
 *   resetPassword     — كيبدّل كلمة المرور.
 */
class ServerAuth(private val endpoint: String = BuildConfig.API_URL) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * الدخول بحساب Google — **بلا حتى سيرڤور ديال نبض**.
     *
     * التحقق كيدار على التليفون: كنسولو نقطة Google الرسمية `tokeninfo`،
     * وهي اللي كتفحقق التوقيع والمُصدر والصلاحية. حنا غير كنقارنو الجواب:
     *   - `aud`     خاصو يكون هو Web client ID ديال نبض (بلاصة وحدة فـ build.gradle.kts).
     *   - `iss`     خاصو يكون Google.
     *   - `email_verified` خاصو يكون "true".
     *   - `exp`     خاصو يكون فالمستقبل.
     *
     * إلا شي وحدة ما صدقاتش: كنرفضو الدخول. وعمّرنا ما كنتيقو فالرمز براسو —
     * Google هي اللي كتأكد.
     */
    suspend fun googleSignIn(idToken: String): ServerUser = withContext(Dispatchers.IO) {
        val claims = googleTokenInfo(idToken)
        // المُستقبِل: إلا كان الرمز موجّه لشي جهة أخرى، ما كنقبلوهش.
        if (claims.optString("aud") != BuildConfig.GOOGLE_WEB_CLIENT_ID) throw ServerAuthException("INVALID_GOOGLE_TOKEN")
        if (!claims.optString("iss").endsWith("accounts.google.com")) throw ServerAuthException("INVALID_GOOGLE_TOKEN")
        if (claims.optString("email_verified") != "true") throw ServerAuthException("GOOGLE_EMAIL_NOT_VERIFIED")
        // exp بـ ثواني من 1970.
        val exp = claims.optString("exp").toLongOrNull() ?: throw ServerAuthException("INVALID_GOOGLE_TOKEN")
        if (exp * 1000L <= System.currentTimeMillis()) throw ServerAuthException("INVALID_GOOGLE_TOKEN")
        val sub = claims.optString("sub")
        val email = claims.optString("email")
        if (sub.isBlank() || email.isBlank()) throw ServerAuthException("INVALID_GOOGLE_TOKEN")
        ServerUser(
            token = "telegram:$sub",
            id = sub,
            name = claims.optString("name").ifBlank { email.substringBefore('@') },
            email = email,
            username = email.substringBefore('@'),
            avatarUrl = claims.optString("picture").takeIf { it.isNotBlank() },
            verified = true,
        )
    }

    /**
     * كيجيب الـ claims ديال idToken من `https://oauth2.googleapis.com/tokeninfo`.
     * الرمز الغالط ولا القديم: Google كتجاوب بـ 400 ومعاه `{"error":"invalid_token"}`.
     */
    private fun googleTokenInfo(idToken: String): JSONObject {
        val url = "https://oauth2.googleapis.com/tokeninfo".toHttpUrl().newBuilder()
            .addQueryParameter("id_token", idToken)
            .build()
        // UnknownHostException وSocketTimeoutException بجوج IOException: بلاصة وحدة كافية.
        val response = try {
            http.newCall(Request.Builder().url(url).get().build()).execute()
        } catch (_: IOException) {
            throw ServerAuthException("NO_INTERNET")
        }
        response.use { res ->
            if (!res.isSuccessful) throw ServerAuthException("INVALID_GOOGLE_TOKEN")
            val text = res.body?.string().orEmpty()
            return runCatching { JSONObject(text) }.getOrNull() ?: throw ServerAuthException("INVALID_GOOGLE_TOKEN")
        }
    }

    /**
     * كيصيفط رمز التفعيل. الجواب هو هو واخا البريد ما كاينش:
     * ما كنبيّنوش واش الحساب مسجل ولا لا.
     */
    suspend fun sendVerification(email: String): Int = withContext(Dispatchers.IO) {
        val data = call(
            """mutation SendVerification(${"$"}email: String!) { sendVerification(email: ${"$"}email) { sent retryAfter } }""",
            JSONObject().put("email", email),
        )
        data.getJSONObject("sendVerification").optInt("retryAfter", 60)
    }

    suspend fun verifyEmail(email: String, code: String) = withContext(Dispatchers.IO) {
        call(
            """mutation VerifyEmail(${"$"}email: String!, ${"$"}code: String!) { verifyEmail(email: ${"$"}email, code: ${"$"}code) }""",
            JSONObject().put("email", email).put("code", code),
        )
    }

    suspend fun forgotPassword(email: String): Int = withContext(Dispatchers.IO) {
        val data = call(
            """mutation ForgotPassword(${"$"}email: String!) { forgotPassword(email: ${"$"}email) { sent retryAfter } }""",
            JSONObject().put("email", email),
        )
        data.getJSONObject("forgotPassword").optInt("retryAfter", 60)
    }

    suspend fun verifyResetCode(email: String, code: String) = withContext(Dispatchers.IO) {
        call(
            """mutation VerifyResetCode(${"$"}email: String!, ${"$"}code: String!) { verifyResetCode(email: ${"$"}email, code: ${"$"}code) }""",
            JSONObject().put("email", email).put("code", code),
        )
    }

    suspend fun resetPassword(email: String, code: String, password: String) = withContext(Dispatchers.IO) {
        call(
            """mutation ResetPassword(${"$"}email: String!, ${"$"}code: String!, ${"$"}password: String!) {
                 resetPassword(email: ${"$"}email, code: ${"$"}code, password: ${"$"}password)
               }""",
            JSONObject().put("email", email).put("code", code).put("password", password),
        )
    }

    /** طلب GraphQL واحد: غير الطرق اللي كتحتاج سيرڤور (رموز البريد) كتوصل لهادشي. */
    private fun call(query: String, variables: JSONObject): JSONObject {
        require(endpoint.isNotBlank()) { "SERVER_NOT_CONFIGURED" }
        val body = JSONObject().put("query", query).put("variables", variables).toString()
        val request = Request.Builder().url(endpoint).post(body.toRequestBody(jsonType)).build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw ServerAuthException("NO_INTERNET")
        } catch (e: SocketTimeoutException) {
            throw ServerAuthException("NO_INTERNET")
        } catch (e: IOException) {
            throw ServerAuthException("NO_INTERNET")
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ServerAuthException("SERVER_ERROR")
            val payload = runCatching { JSONObject(text) }.getOrNull() ?: throw ServerAuthException("SERVER_ERROR")
            val errors = payload.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.optJSONObject(0) ?: throw ServerAuthException("SERVER_ERROR")
                val code = first.optJSONObject("extensions")?.optString("code")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?: first.optString("message").takeIf { it.isNotBlank() }
                    ?: "SERVER_ERROR"
                throw ServerAuthException(code)
            }
            return payload.optJSONObject("data") ?: throw ServerAuthException("SERVER_ERROR")
        }
    }
}
