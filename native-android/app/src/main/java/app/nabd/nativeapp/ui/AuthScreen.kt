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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Native interpretation of the supplied HTML, not a WebView or external login.
 * No mascot, fake redirect or credential persistence. Submit calls the real API.
 */
@Composable fun AuthScreen(state: AppState, login: (String, String) -> Unit, register: (String, String, String) -> Unit) {
    var registration by rememberSaveable { mutableStateOf(false) }
    var identity by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Deliberately not rememberSaveable: passwords never enter instance-state bundles.
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
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
        if (registration) register(name, identity, password) else login(identity, password)
    }
    Box(Modifier.fillMaxSize().background(Canvas).testTag("native-auth")) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("نبض", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Text("مساحتك، وناس قريبين ليك", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
            Box(Modifier.widthIn(max = 440.dp).fillMaxWidth().clip(RoundedCornerShape(40.dp)).drawBehind {
                val brush = Brush.sweepGradient(listOf(Accent, Color.Transparent, AccentLight, Color.Transparent, Accent))
                rotate(if (reducedMotion) 0f else rotation) { drawRect(brush, topLeft = Offset(-size.width, -size.height), size = Size(size.width * 3, size.height * 3)) }
            }.padding(2.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(38.dp)).background(Surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (registration) "إنشاء حساب" else "تسجيل الدخول", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
                    if (registration) NativeField(name, { name = it }, "الاسم الكامل", submitted && name.trim().length !in 2..40, state.busy, "الاسم بين حرفين و40 حرف")
                    NativeField(identity, { identity = it }, if (registration) "البريد الإلكتروني" else "اسم المستخدم أو البريد الإلكتروني", submitted && identity.isBlank(), state.busy, "هذا الحقل مطلوب", keyboard = if (registration) KeyboardType.Email else KeyboardType.Text)
                    OutlinedTextField(value = password, onValueChange = { password = it.take(128) }, label = { Text("كلمة المرور") }, singleLine = true, enabled = !state.busy,
                        isError = submitted && (password.isEmpty() || (registration && password.length < 8)), shape = RoundedCornerShape(28.dp),
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { reveal = !reveal }, enabled = !state.busy) { Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "إخفاء كلمة المرور" else "إظهار كلمة المرور") } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (!state.busy) submit() }),
                        supportingText = { if (submitted && password.isEmpty()) Text("أدخل كلمة المرور") else if (registration) Text("بين 8 و128 حرف") }, modifier = Modifier.fillMaxWidth().testTag("password"))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth-error")) }
                    Button(onClick = ::submit, enabled = !state.busy, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("auth-submit")) {
                        if (state.busy) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                        Text(if (state.busy) "جارٍ التحقق…" else if (registration) "إنشاء حسابي" else "دخول", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!registration) TextButton(onClick = { recovery = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("نسيت كلمة المرور؟", color = AccentLight) }
                    TextButton(onClick = { registration = !registration; submitted = false; password = "" }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (registration) "عندي حساب · تسجيل الدخول" else "جديد هنا؟ إنشاء حساب", color = Muted) }
                }
            }
        }
    }
    if (recovery) AlertDialog(onDismissRequest = { recovery = false }, title = { Text("استعادة كلمة المرور") }, text = { Text("خدمة استعادة كلمة المرور غير متوفرة حالياً. ما غنوجهوكش لموقع خارجي وما بدّلناش كلمة مرورك.") }, confirmButton = { TextButton(onClick = { recovery = false }) { Text("فهمت") } })
}

@Composable private fun NativeField(value: String, change: (String) -> Unit, label: String, invalid: Boolean, busy: Boolean, error: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = { change(it.take(254)) }, label = { Text(label) }, singleLine = true, enabled = !busy, isError = invalid, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().testTag(if (label == "الاسم الكامل") "full-name" else "identity"), keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next), supportingText = if (invalid) ({ Text(error) }) else null)
}
