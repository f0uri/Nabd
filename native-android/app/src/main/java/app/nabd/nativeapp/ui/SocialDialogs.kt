package app.nabd.nativeapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class PhotoDraft {
    var image by mutableStateOf<String?>(null)
    var ratio by mutableDoubleStateOf(1.0)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** Shared picker launcher so any screen can attach a real image. */
@Composable internal fun rememberPhotoLauncher(draft: PhotoDraft): androidx.activity.result.ActivityResultLauncher<String> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            draft.busy = true; draft.error = null
            try { val value = inlineImage(context, uri); draft.image = value.first; draft.ratio = value.second }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { draft.error = "تعذّر تجهيز الصورة ضمن الحد المسموح (128KB)." }
            finally { draft.busy = false }
        }
    }
}

@Composable internal fun PhotoPicker(draft: PhotoDraft, enabled: Boolean) {
    val launcher = rememberPhotoLauncher(draft)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (draft.image != null) {
            Box(Modifier.fillMaxWidth().heightIn(max = 150.dp).clip(MediaShape).background(Raised)) {
                NabdImage(draft.image, "الصورة المختارة", Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color(0x990F172A))
                    .pressClick(enabled = enabled && !draft.busy) { draft.image = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "إزالة الصورة", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        PillButton(if (draft.busy) "جارٍ تجهيز الصورة…" else "اختيار صورة", { launcher.launch("image/*") },
            enabled = enabled && !draft.busy, brush = null, height = 44.dp, fontSize = 13, icon = Icons.Outlined.Image, testTag = "pick-photo")
        if (draft.error != null) Text(draft.error!!, color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

/** Reaction picker: the same six custom vectors, no emoji and no thumbs-up. */
@Composable internal fun ReactionPicker(selected: String?, busy: Boolean, choose: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        reactionOrder.forEach { kind ->
            val chosen = selected == kind
            val press = rememberPress()
            Row(Modifier.clip(CircleShape).background(if (chosen) Accent.copy(alpha = .14f) else Raised)
                .border(1.dp, if (chosen) Accent else Color.Transparent, CircleShape)
                .pressClick(press = press, enabled = !busy) { choose(if (chosen) null else kind) }
                .padding(horizontal = 12.dp, vertical = 8.dp).testTag("reaction-$kind"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReactionGlyph(kind, size = 22.dp)
                Text(reactionLabel(kind), color = if (chosen) Accent else TextMain, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Glass account editor: 96dp avatar with the camera button attached to it. */
@Composable internal fun ProfileDialog(person: Person, busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String, String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf(person.name) }
    var username by remember { mutableStateOf(person.username) }
    var bio by remember { mutableStateOf(person.bio) }
    var remove by remember { mutableStateOf(false) }
    val photo = remember { PhotoDraft() }
    val launcher = rememberPhotoLauncher(photo)
    GlassWindow(close = { if (!busy && !photo.busy) close() }, dismissible = !busy && !photo.busy) {
        Text("تعديل الحساب", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Raised), contentAlignment = Alignment.Center) {
                if (!remove && photo.image == null) Avatar(person, 96) else if (photo.image != null) NabdImage(photo.image, "الصورة المختارة", Modifier.fillMaxSize(),
                    androidx.compose.ui.layout.ContentScale.Crop) else Text(person.name.firstOrNull()?.toString() ?: "ن", color = Muted, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(NabdBlue).border(2.dp, Color.White, CircleShape)
                .pressClick(enabled = !busy && !photo.busy) { remove = false; launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PhotoCamera, "تغيير صورة الحساب", tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NabdField(name, { name = it.take(40) }, "الاسم", Modifier.fillMaxWidth(), enabled = !busy, tag = "profile-name")
            NabdField(username, { username = it.take(40) }, "اسم المستخدم", Modifier.fillMaxWidth(), enabled = !busy, tag = "profile-username")
            NabdField(bio, { bio = it.take(160) }, "نبذة", Modifier.fillMaxWidth(), enabled = !busy, singleLine = false, maxLines = 3, tag = "profile-bio")
            if (person.avatar != null && photo.image == null) PillButton(if (remove) "التراجع عن إزالة صورة الحساب" else "إزالة صورة الحساب",
                { remove = !remove; photo.image = null }, modifier = Modifier.fillMaxWidth(), enabled = !busy && !photo.busy, brush = null, height = 44.dp, fontSize = 13)
            if (photo.image != null) PillButton("إزالة الصورة المختارة", { photo.image = null }, modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !photo.busy, brush = null, height = 44.dp, fontSize = 13)
            if (photo.error != null) Text(photo.error!!, color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
            if (serviceError != null) Text(serviceError, color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
        }
        PillButton(if (busy || photo.busy) "جارٍ الحفظ…" else "حفظ", { save(name, username, bio, photo.image, remove && photo.image == null) },
            modifier = Modifier.fillMaxWidth(), enabled = !busy && !photo.busy, testTag = "profile-save")
        PillButton("إلغاء", { if (!busy && !photo.busy) close() }, modifier = Modifier.fillMaxWidth(), enabled = !busy && !photo.busy, brush = null, height = 46.dp, fontSize = 14)
    }
}

/** Real credential change: it rewrites the stored salt/password hash and the account email. */
@Composable internal fun CredentialsDialog(busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String?, String?) -> Unit) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    GlassWindow(close = { if (!busy) close() }, dismissible = !busy) {
        Text("إعدادات الحساب", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NabdField(current, { current = it }, "كلمة السر الحالية", Modifier.fillMaxWidth(), enabled = !busy, tag = "credential-current",
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next))
            NabdField(password, { password = it.take(128) }, "كلمة سر جديدة (اختياري)", Modifier.fillMaxWidth(), enabled = !busy, tag = "credential-password",
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next))
            NabdField(email, { email = it }, "بريد جديد (اختياري)", Modifier.fillMaxWidth(), enabled = !busy, tag = "credential-email",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Muted, modifier = Modifier.size(17.dp))
                Text("تغيير كلمة السر كيسجّل خروج باقي الأجهزة، وهذا الجهاز كيبقى مسجّل. الطول الأدنى 8 أحرف.",
                    Modifier.weight(1f), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Text(if (reveal) "إخفاء" else "إظهار", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.pressClick { reveal = !reveal }.padding(4.dp))
            }
            if (localError != null) Text(localError!!, color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
            if (serviceError != null) Text(serviceError, color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
        }
        PillButton("حفظ", {
            val newPassword = password.trim().ifEmpty { null }
            val newEmail = email.trim().ifEmpty { null }
            when {
                current.isEmpty() -> localError = "دخل كلمة السر الحالية"
                newPassword != null && newPassword.length < 8 -> localError = "كلمة السر الجديدة قصيرة"
                newPassword == null && newEmail == null -> localError = "بدّل كلمة السر أو البريد"
                else -> { localError = null; save(current, newPassword, newEmail) }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !busy, testTag = "credentials-save")
        PillButton("إلغاء", { if (!busy) close() }, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = null, height = 46.dp, fontSize = 14)
    }
}
