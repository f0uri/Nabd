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
import app.nabd.nativeapp.data.*
import coil.compose.AsyncImage
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
        AsyncImage(draft.image, "الصورة المختارة", Modifier.fillMaxWidth().heightIn(max = 160.dp))
        TextButton(onClick = { draft.image = null }, enabled = enabled && !draft.busy) { Text("إزالة الصورة المختارة") }
    }
    TextButton(onClick = { picker.launch("image/*") }, enabled = enabled && !draft.busy, modifier = Modifier.testTag("pick-photo")) {
        Text(if (draft.busy) "جارٍ تجهيز الصورة…" else "اختيار صورة")
    }
    draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
internal val reactionLabels = linkedMapOf("like" to "إعجاب", "love" to "حب", "haha" to "ضحك", "wow" to "اندهاش", "sad" to "حزن", "angry" to "غضب")
@Composable internal fun ReactionPicker(selected: String?, busy: Boolean, choose: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactionLabels.forEach { (kind, label) -> FilterChip(selected = selected == kind, onClick = { choose(if (selected == kind) null else kind) }, enabled = !busy, label = { Text(label) }, modifier = Modifier.testTag("reaction-$kind")) }
    }
}
@Composable internal fun ProfileDialog(person: Person, busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String, String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf(person.name) }; var username by remember { mutableStateOf(person.username) }; var bio by remember { mutableStateOf(person.bio) }
    var remove by remember { mutableStateOf(false) }; val photo = remember { PhotoDraft() }
    AlertDialog(onDismissRequest = { if (!busy && !photo.busy) close() }, title = { Text("تعديل الحساب") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!remove && photo.image == null && person.avatar != null) AsyncImage(person.avatar, "صورة الحساب الحالية", Modifier.size(88.dp))
        PhotoPicker(photo, !busy)
        if (person.avatar != null) TextButton(onClick = { remove = !remove; photo.image = null }, enabled = !busy && !photo.busy) { Text(if (remove) "التراجع عن إزالة صورة الحساب" else "إزالة صورة الحساب") }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("الاسم") }, enabled = !busy)
        OutlinedTextField(username, { username = it.take(40) }, label = { Text("اسم المستخدم") }, enabled = !busy)
        OutlinedTextField(bio, { bio = it.take(160) }, label = { Text("نبذة") }, enabled = !busy)
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { save(name, username, bio, photo.image, remove && photo.image == null) }, enabled = !busy && !photo.busy, modifier = Modifier.testTag("profile-save")) { Text(if (busy) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(onClick = close, enabled = !busy && !photo.busy) { Text("إلغاء") } })
}
@Composable internal fun CommentsDialog(post: FeedPost, self: String, busy: Boolean, serviceError: String?, close: () -> Unit, send: (String, () -> Unit) -> Unit, like: (String) -> Unit, delete: (String) -> Unit) {
    var text by remember { mutableStateOf("") }; var removing by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("التعليقات") }, text = { Column {
        LazyColumn(Modifier.heightIn(max = 280.dp)) { items(post.comments, key = { it.id }) { c -> Column(Modifier.padding(vertical = 6.dp)) {
            Text(c.author.name, fontWeight = FontWeight.Bold); Text(c.text)
            Row {
                TextButton(onClick = { like(c.id) }, enabled = !busy, modifier = Modifier.testTag("comment-like-${c.id}")) { Text("${if (c.likedByMe) "إلغاء الإعجاب" else "إعجاب"} · ${c.likes}") }
                if (c.author.id == self || post.author.id == self) TextButton(onClick = { removing = c.id }, enabled = !busy, modifier = Modifier.testTag("comment-delete-${c.id}")) { Text("حذف") }
            }
        } } }
        OutlinedTextField(text, { text = it.take(2000) }, label = { Text("اكتب تعليقاً") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { send(text) { text = "" } }, enabled = !busy && text.isNotBlank()) { Text("إرسال") } }, dismissButton = { TextButton(onClick = close) { Text("إغلاق") } })
    removing?.let { id -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("حذف التعليق؟") }, confirmButton = { TextButton(onClick = { removing = null; delete(id) }, enabled = !busy, modifier = Modifier.testTag("comment-delete-confirm")) { Text("حذف") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("إلغاء") } }) }
}
@Composable internal fun StoryComposer(busy: Boolean, error: String?, close: () -> Unit, save: (String, String?) -> Unit) {
    var text by remember { mutableStateOf("") }; val photo = remember { PhotoDraft() }
    AlertDialog(onDismissRequest = { if (!busy && !photo.busy) close() }, title = { Text("قصة محلية جديدة") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("محفوظة على هذا الجهاز فقط، مثل النسخة السابقة. لا تُنشر للأصدقاء.", color = Muted)
        OutlinedTextField(text, { text = it.take(2000) }, label = { Text("نص القصة") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        PhotoPicker(photo, !busy)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { save(text, photo.image) }, enabled = !busy && !photo.busy && (text.isNotBlank() || photo.image != null)) { Text("حفظ محلياً") } }, dismissButton = { TextButton(onClick = close, enabled = !busy && !photo.busy) { Text("إلغاء") } })
}
@Composable internal fun StoryViewer(story: LocalStory, busy: Boolean, error: String?, close: () -> Unit, delete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text("قصتي المحلية") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        if (story.image != null) AsyncImage(story.image, "صورة القصة", Modifier.fillMaxWidth().heightIn(max = 360.dp))
        Text(story.text); Text(story.date, color = Muted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = close) { Text("إغلاق") } }, dismissButton = { TextButton(onClick = { confirm = true }, enabled = !busy) { Text("حذف القصة") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("حذف القصة المحلية؟") }, text = { Text("ستُحذف من قصص Kotlin. نسخة التخزين القديمة تبقى بلا تغيير للحفاظ عليها أثناء الانتقال.") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }, enabled = !busy) { Text("حذف") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}
