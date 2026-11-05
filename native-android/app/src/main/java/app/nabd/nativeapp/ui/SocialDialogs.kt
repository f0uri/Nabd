package app.nabd.nativeapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
@Composable internal fun PhotoPicker(draft: PhotoDraft, enabled: Boolean) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            draft.busy = true; draft.error = null
            try { val value = inlineImage(context, uri); draft.image = value.first; draft.ratio = value.second }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { draft.error = "تعذّر تجهيز الصورة ضمن الحد المسموح (128KB)." }
            finally { draft.busy = false }
        }
    }
    if (draft.image != null) {
        NabdImage(draft.image, "الصورة المختارة", Modifier.fillMaxWidth().heightIn(max = 160.dp))
        TextButton(onClick = { draft.image = null }, enabled = enabled && !draft.busy) { Text("إزالة الصورة المختارة") }
    }
    TextButton(onClick = { picker.launch("image/*") }, enabled = enabled && !draft.busy, modifier = Modifier.testTag("pick-photo")) {
        Text(if (draft.busy) "جارٍ تجهيز الصورة…" else "اختيار صورة")
    }
    draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
/** Reaction picker for messages: the same six custom vectors, no emoji and no thumbs-up. */
@Composable internal fun ReactionPicker(selected: String?, busy: Boolean, choose: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactionOrder.forEach { kind ->
            FilterChip(selected = selected == kind, onClick = { choose(if (selected == kind) null else kind) }, enabled = !busy,
                leadingIcon = { ReactionGlyph(kind, size = 20.dp) }, label = { Text(reactionLabel(kind)) }, modifier = Modifier.testTag("reaction-$kind"))
        }
    }
}
@Composable internal fun ProfileDialog(person: Person, busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String, String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf(person.name) }; var username by remember { mutableStateOf(person.username) }; var bio by remember { mutableStateOf(person.bio) }
    var remove by remember { mutableStateOf(false) }; val photo = remember { PhotoDraft() }
    AlertDialog(onDismissRequest = { if (!busy && !photo.busy) close() }, title = { Text("تعديل الحساب") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!remove && photo.image == null && person.avatar != null) NabdImage(person.avatar, "صورة الحساب الحالية", Modifier.size(88.dp))
        PhotoPicker(photo, !busy)
        if (person.avatar != null) TextButton(onClick = { remove = !remove; photo.image = null }, enabled = !busy && !photo.busy) { Text(if (remove) "التراجع عن إزالة صورة الحساب" else "إزالة صورة الحساب") }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("الاسم") }, enabled = !busy)
        OutlinedTextField(username, { username = it.take(40) }, label = { Text("اسم المستخدم") }, enabled = !busy)
        OutlinedTextField(bio, { bio = it.take(160) }, label = { Text("نبذة") }, enabled = !busy)
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { save(name, username, bio, photo.image, remove && photo.image == null) }, enabled = !busy && !photo.busy, modifier = Modifier.testTag("profile-save")) { Text(if (busy) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(onClick = close, enabled = !busy && !photo.busy) { Text("إلغاء") } })
}
/** Real credential change: it rewrites the stored salt/password hash and the account email. */
@Composable internal fun CredentialsDialog(busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String?, String?) -> Unit) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("إعدادات الحساب") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(current, { current = it }, label = { Text("كلمة السر الحالية") }, singleLine = true, enabled = !busy)
        OutlinedTextField(password, { password = it }, label = { Text("كلمة سر جديدة (اختياري)") }, singleLine = true, enabled = !busy)
        OutlinedTextField(email, { email = it }, label = { Text("بريد جديد (اختياري)") }, singleLine = true, enabled = !busy)
        Text("تغيير كلمة السر كيسجّل خروج باقي الأجهزة، وهذا الجهاز كيبقى مسجّل. الطول الأدنى 8 أحرف.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        if (localError != null) Text(localError!!, color = AccentLight, fontSize = 12.sp)
        if (serviceError != null) Text(serviceError, color = AccentLight, fontSize = 12.sp)
    } }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val newPassword = password.trim().ifEmpty { null }
        val newEmail = email.trim().ifEmpty { null }
        when {
            current.isEmpty() -> localError = "دخل كلمة السر الحالية"
            newPassword != null && newPassword.length < 8 -> localError = "كلمة السر الجديدة قصيرة"
            newPassword == null && newEmail == null -> localError = "بدّل كلمة السر أو البريد"
            else -> { localError = null; save(current, newPassword, newEmail) }
        }
    }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("إلغاء") } })
}
@Composable internal fun StoryComposer(busy: Boolean, error: String?, close: () -> Unit, save: (String, String?) -> Unit) {
    var text by remember { mutableStateOf("") }; val photo = remember { PhotoDraft() }
    AlertDialog(onDismissRequest = { if (!busy && !photo.busy) close() }, title = { Text("قصة جديدة") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("تبقى قصتك ظاهرة 24 ساعة ثم تختفي تلقائياً", color = Muted)
        OutlinedTextField(text, { text = it.take(2000) }, label = { Text("نص القصة") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        PhotoPicker(photo, !busy)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { save(text, photo.image) }, enabled = !busy && !photo.busy && (text.isNotBlank() || photo.image != null)) { Text("حفظ محلياً") } }, dismissButton = { TextButton(onClick = close, enabled = !busy && !photo.busy) { Text("إلغاء") } })
}
