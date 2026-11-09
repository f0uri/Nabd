package app.nabd.nativeapp.data

import app.nabd.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val id: String,
    val name: String,
    val email: String,
    val username: String,
    val avatarUrl: String?,
    val verified: Boolean,
)

/**
 * كيهضر مع خادم GraphQL ديال نبض (GraphQL Yoga).
 *
 * اللي كيميّز هاد العميل: **الخادم هو اللي كيفحقق كلشي**. التطبيق ما كيقرر
 * والو فالأمان — كيصيفط المعطيات وكيعرض الجواب.
 *
 *   googleSignIn      — كيبدّل idToken ديال Google بجلسة نبض.
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

    /** الحساب اللي كيجي من Google. الخادم كيفحقق الرمز، حنا غير كنستناو الجواب. */
    suspend fun googleSignIn(idToken: String): ServerUser = withContext(Dispatchers.IO) {
        require(endpoint.isNotBlank()) { "SERVER_NOT_CONFIGURED" }
        val data = call(
            """mutation GoogleSignIn(${"$"}idToken: String!) {
                 googleSignIn(idToken: ${"$"}idToken) { token verified user { id name username email avatarUrl verified } }
               }""",
            JSONObject().put("idToken", idToken),
        )
        val payload = data.getJSONObject("googleSignIn")
        val user = payload.getJSONObject("user")
        ServerUser(
            id = user.getString("id"),
            name = user.optString("name").ifBlank { user.optString("email").substringBefore('@') },
            email = user.getString("email"),
            username = user.optString("username"),
            avatarUrl = user.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
            verified = payload.optBoolean("verified", true),
        )
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

    /** طلب GraphQL واحد. كحوّل أخطاء الخادم لـ ServerAuthException بالرمز ديالها. */
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
