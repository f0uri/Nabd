package app.nabd.nativeapp.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.nabd.nativeapp.BuildConfig
import app.nabd.nativeapp.data.ServerAuthException
import kotlinx.coroutines.CancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleText = Color(0xFF222222)

/**
 * جالب الهوية ديال Google بـ Credential Manager (البديلة العصرية ديال Google Sign-In).
 *
 * القاعدة اللي خاصها تبقى: **كل مرة** كنمحو الحالة القديمة ديال Google قبل الطلب،
 * وكنطلبو كل الحسابات ديال التليفون (ماشي غير اللي كانو داخلين قبل). بهاد الشي
 * ورقة «اختار حساب» كتظهر ديما، حتى إلا كان المستخدم داخل قبل.
 */
class GoogleAuth(private val context: Context) {
    private val manager = CredentialManager.create(context)

    /**
     * كيجيب idToken. `null` كيعني المستخدم سدّ الورقة — ماشي غلطة،
     * وما خاصنا نعرضو حتى رسالة.
     */
    suspend fun requestIdToken(): String? {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) throw ServerAuthException("GOOGLE_NOT_CONFIGURED")
        // الشرط ديال «ورقة الاختيار كتظهر ديما»: كنمحو الحالة القديمة ديال Google
        // قبل كل طلب. إلا ما نجحاتش (مثلاً Play services ما كيدعمهاش) كنكملو:
        // setFilterByAuthorizedAccounts(false) كيضمن نفس النتيجة.
        try {
            manager.clearCredentialState(ClearCredentialStateRequest())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // ما كيهمّش.
        }

        val option = GetGoogleIdOption.Builder()
            // نفس معرّف Web اللي كيفحقق بيه الخادم: بلاصة وحدة فـ build.gradle.kts.
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            // false = ورقة فيها كاع الحسابات ديال التليفون + «زيادة حساب آخر».
            .setFilterByAuthorizedAccounts(false)
            // false = ما كيدخلش أوتوماتيكي بحساب واحد بلا ما يسأل.
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            idTokenOf(manager.getCredential(context, request))
        } catch (cancelled: GetCredentialCancellationException) {
            null // المستخدم سدّ الورقة: والو ما وقع.
        }
    }

    private fun idTokenOf(result: GetCredentialResponse): String {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw ServerAuthException("GOOGLE_NO_ID_TOKEN")
    }
}

/** رسائل عربية مفهومة بلاصت الرموز التقنية. */
fun googleErrorMessage(error: Throwable?): String = when {
    error is ServerAuthException && error.code == "NO_INTERNET" -> "ما كاينش إنترنت. تحقق من الاتصال و عاود جرّب."
    error is ServerAuthException && error.code == "GOOGLE_NOT_CONFIGURED" -> "الدخول بـ Google ما مفعّلش دابا. جرّب بالبريد الإلكتروني."
    error is ServerAuthException && error.code == "SERVER_NOT_CONFIGURED" -> "الدخول بـ Google ما مفعّلش دابا. جرّب بالبريد الإلكتروني."
    error is ServerAuthException && error.code == "GOOGLE_NO_ID_TOKEN" -> "ما قدرناش نجيبو حساب Google. عاود جرّب."
    error is ServerAuthException && error.code == "NO_CREDENTIAL" -> "ما كاين حتى حساب Google فهاد التليفون. زيد واحد و عاود جرّب."
    error is NoCredentialException -> "ما كاين حتى حساب Google فهاد التليفون. من الإعدادات زيد حساب و عاود جرّب."
    error is ServerAuthException && error.code == "INVALID_GOOGLE_TOKEN" -> "ما قدرناش نتحققو من حساب Google. عاود جرّب."
    error is ServerAuthException && error.code == "GOOGLE_EMAIL_NOT_VERIFIED" -> "البريد ديال حساب Google مامفعلش. فعلو و عاود جرّب."
    error is GetCredentialException -> "ما قدرناش نحققو حساب Google. عاود جرّب."
    else -> "تعذّر الدخول بحساب Google. عاود جرّب."
}

/** حرف G ديال Google مرسوم بأربعة أقواس: متجه، ما كيحتاج حتى صورة. */
@Composable
private fun GoogleGlyph(modifier: Modifier = Modifier, glyphSize: Dp = 20.dp) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val w = this.size.width
        val bar = w * 0.17f
        val inset = bar / 2f + w * 0.02f
        val arcSize = Size(w - inset * 2f, w - inset * 2f)
        val corner = Offset(inset, inset)
        // معاملات مسماة: drawArc فيها `alpha` قبل `style`، والترتيب كيبدّل.
        val stroke = Stroke(width = bar, cap = StrokeCap.Butt)
        // الأزرق: فوق ليمن حتى 3 ديال التوقيتة (الشريط كيكمل من تما).
        drawArc(color = GoogleBlue, startAngle = 270f, sweepAngle = 90f, useCenter = false, topLeft = corner, size = arcSize, style = stroke)
        // الفتحة: 3 → 4:20. تما فين كيدخل الشريط.
        drawArc(color = GoogleGreen, startAngle = 40f, sweepAngle = 95f, useCenter = false, topLeft = corner, size = arcSize, style = stroke)
        drawArc(color = GoogleYellow, startAngle = 135f, sweepAngle = 90f, useCenter = false, topLeft = corner, size = arcSize, style = stroke)
        drawArc(color = GoogleRed, startAngle = 225f, sweepAngle = 45f, useCenter = false, topLeft = corner, size = arcSize, style = stroke)
        // الشريط الأزرق: من النص لليمن، هو اللي كيحوّل الدائرة لـ G.
        drawRect(color = GoogleBlue, topLeft = Offset(center.x, center.y - bar / 2f), size = Size(w - inset - center.x, bar))
    }
}

/**
 * زر Google: حبّة بيضاء عريضة، G ملوّن على اليمين (RTL)،
 * ودوّارة صغيرة إلا كانو كيفحققو.
 */
@Composable
fun GoogleSignInButton(loading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White, RoundedCornerShape(28.dp))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .testTag("google-signin"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // فـ RTL هادا هو اللي كيبان على اليمين.
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoogleBlue, strokeWidth = 2.5.dp)
        } else {
            GoogleGlyph()
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "المتابعة بحساب Google",
            color = if (enabled) GoogleText else GoogleText.copy(alpha = 0.45f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** فاصل «أو» بين الدخول العادي وزر Google. */
@Composable
fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("or-divider"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.18f)))
        Text("أو", color = Color.White.copy(alpha = 0.55f), fontSize = 12.5.sp)
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.18f)))
    }
}
