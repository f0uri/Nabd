package app.nabd.nativeapp.ui

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.AppState

/** Native sign-in and registration. No mascot, fake redirect or credential persistence.
 * Submit calls the real API through the same repository every other screen uses.
 */
@Composable fun AuthScreen(state: AppState, login: (String, String) -> Unit, register: (String, String, String, String?) -> Unit, invite: String? = null) {
    var registration by rememberSaveable { mutableStateOf(false) }
    var identity by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Deliberately not rememberSaveable: passwords never enter instance-state bundles.
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf(invite.orEmpty()) }
    var recovery by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val reducedMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val transition = rememberInfiniteTransition(label = "auth-border")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "border-angle")
    fun submit() {
        submitted = true
        if (identity.isBlank() || password.isEmpty() || (registration && (name.trim().length !in 2..40 || password.length !in 8..128))) return
        focus.clearFocus()
        if (registration) register(name, identity, password, code.trim().ifEmpty { null }) else login(identity, password)
    }
    Box(Modifier.fillMaxSize().background(Canvas).testTag("native-auth")) {
        // Two soft blue haloes behind the glass card, never a flat white sheet.
        Box(Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-60).dp).size(230.dp)
            .clip(androidx.compose.foundation.shape.CircleShape).background(Accent.copy(alpha = .10f)))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-80).dp, y = 90.dp).size(260.dp)
            .clip(androidx.compose.foundation.shape.CircleShape).background(AccentLight.copy(alpha = .07f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GradientText("نبض", size = 42.sp)
            Text("مساحتك، وناس قريبين ليك", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
            Box(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(40.dp)).drawBehind {
                val brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Accent, Color.Transparent, AccentLight, Color.Transparent, Accent))
                rotate(if (reducedMotion) 0f else rotation) { drawRect(brush, topLeft = Offset(-size.width, -size.height), size = Size(size.width * 3, size.height * 3)) }
            }.padding(2.dp)) {
                val shape = RoundedCornerShape(38.dp)
                Box(Modifier.fillMaxWidth().nabdShadow(shape, elevation = 26.dp).clip(shape).background(WindowFill).border(1.dp, Color(0x80FFFFFF), shape)) {
                    Box(Modifier.matchParentSize().glassBlur(30.dp).background(Color(0x1AFFFFFF)))
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (registration) "إنشاء حساب" else "تسجيل الدخول", color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
                        if (registration) NabdField(name, { name = it.take(40) }, "الاسم الكامل", Modifier.fillMaxWidth(), enabled = !state.busy, tag = "full-name",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next))
                        if (registration) NabdField(code, { code = it.take(32) }, "كود دعوة (اختياري)", Modifier.fillMaxWidth(), enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next))
                        NabdField(identity, { identity = it.take(254) }, if (registration) "البريد الإلكتروني" else "اسم المستخدم أو البريد الإلكتروني", Modifier.fillMaxWidth(),
                            enabled = !state.busy, tag = "identity", keyboardOptions = KeyboardOptions(keyboardType = if (registration) KeyboardType.Email else KeyboardType.Text, imeAction = ImeAction.Next))
                        NabdField(password, { password = it.take(128) }, "كلمة المرور", Modifier.fillMaxWidth(), enabled = !state.busy, tag = "password",
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            trailingIcon = { IconButton(onClick = { reveal = !reveal }, enabled = !state.busy) {
                                Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "إخفاء كلمة المرور" else "إظهار كلمة المرور", tint = Muted) } })
                        if (submitted && password.isEmpty()) Text("أدخل كلمة المرور", color = AccentLight, fontSize = 12.sp)
                        else if (submitted && identity.isBlank()) Text("هذا الحقل مطلوب", color = AccentLight, fontSize = 12.sp)
                        else if (registration && password.isNotEmpty() && password.length < 8) Text("بين 8 و128 حرف", color = Muted, fontSize = 12.sp)
                        if (state.error != null) Text(state.error!!, color = AccentLight, fontSize = 12.5.sp, lineHeight = 19.sp, modifier = Modifier.testTag("auth-error"))
                        PillButton(if (state.busy) "جارٍ التحقق…" else if (registration) "إنشاء حسابي" else "دخول", ::submit,
                            modifier = Modifier.fillMaxWidth().testTag("auth-submit"), enabled = !state.busy)
                        PillButton(if (registration) "عندي حساب · تسجيل الدخول" else "جديد هنا؟ إنشاء حساب",
                            { registration = !registration; submitted = false; password = "" }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy, brush = null, height = 46.dp, fontSize = 14)
                        if (!registration) Text("نسيت كلمة المرور؟", color = AccentLight, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.pressClick(enabled = !state.busy) { recovery = true }.padding(8.dp))
                    }
                }
            }
        }
    }
    if (recovery) GlassWindow(close = { recovery = false }) {
        IconPlate(Icons.Outlined.Lock, size = 62.dp, tint = AccentLight)
        Text("استعادة كلمة المرور", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text("خدمة استعادة كلمة المرور غير متوفرة حالياً. ما غنوجهوكش لموقع خارجي وما بدّلناش كلمة مرورك.",
            color = Muted, fontSize = 13.5.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        PillButton("فهمت", { recovery = false }, modifier = Modifier.fillMaxWidth())
    }
}
