package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import app.nabd.nativeapp.data.Message
import app.nabd.nativeapp.data.Thread
import coil.compose.AsyncImage

@Composable internal fun RichChat(thread: Thread, self: String, busy: Boolean, modifier: Modifier, back: () -> Unit,
    send: (String, String?, String?, Double, () -> Unit) -> Unit,
    edit: (String, String, () -> Unit) -> Unit, delete: (String) -> Unit, react: (String, String?) -> Unit, read: () -> Unit, refresh: () -> Unit) {
    var text by rememberSaveable(thread.id) { mutableStateOf("") }
    var reply by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var selected by remember(thread.id) { mutableStateOf<String?>(null) }
    var removing by remember(thread.id) { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    val refreshNow by rememberUpdatedState(refresh)
    LaunchedEffect(owner, thread.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(15000); refreshNow() }
        }
    }
    val photo = remember(thread.id) { PhotoDraft() }
    val unread = thread.messages.filter { it.sender != self && !it.read }.joinToString(":") { it.id }
    var attemptedRead by remember(thread.id) { mutableStateOf<String?>(null) }
    // A failed receipt must not cause an endless busy->idle network retry loop.
    LaunchedEffect(thread.id, unread, busy) {
        if (!busy && unread.isNotEmpty() && attemptedRead != unread) { attemptedRead = unread; read() }
    }
    val messages = remember(thread.messages) { thread.messages.associateBy { it.id } }
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Outlined.ArrowForward, "رجوع") }
            Text(thread.other.name, Modifier.weight(1f))
            IconButton(onClick = { attemptedRead = null; refresh() }, enabled = !busy) { Icon(Icons.Outlined.Refresh, "تحديث الرسائل") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(12.dp)) {
            items(thread.messages.reversed(), key = { it.id }) { m -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (m.sender == self) Arrangement.Start else Arrangement.End) {
                Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp)).background(if (m.sender == self) Accent else Raised).padding(12.dp)) {
                    m.replyToId?.let { id -> Text("رد: ${messages[id]?.let { if (it.deleted) "رسالة محذوفة" else it.text.ifBlank { "صورة" } } ?: "رسالة سابقة"}", maxLines = 2, fontSize = 12.sp, color = Muted) }
                    if (m.image != null && !m.deleted) AsyncImage(m.image, "صورة الرسالة", Modifier.width(220.dp).heightIn(max = 220.dp))
                    Text(if (m.deleted) "تم حذف الرسالة" else m.text)
                    if (m.edited && !m.deleted) Text("معدّلة", fontSize = 10.sp)
                    if (m.reactions.isNotEmpty()) Text(m.reactions.values.groupingBy { it }.eachCount().entries.joinToString(" · ") { "${reactionLabels[it.key]} ${it.value}" }, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.sender == self) Text(if (m.read) "مقروءة" else "مرسلة", fontSize = 10.sp, color = Color.White.copy(alpha = .8f))
                        if (!m.deleted) IconButton(onClick = { selected = m.id }, enabled = !busy, modifier = Modifier.size(32.dp).testTag("message-actions-${m.id}")) { Icon(Icons.Outlined.MoreHoriz, "خيارات الرسالة") }
                    }
                }
            } }
        }
        if (reply != null || editing != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (editing != null) "تعديل الرسالة" else "الرد على: ${messages[reply]?.text?.ifBlank { "صورة" } ?: "رسالة"}", Modifier.weight(1f), maxLines = 2)
            IconButton(onClick = { reply = null; editing = null; text = "" }) { Icon(Icons.Outlined.Close, "إلغاء الرد أو التعديل") }
        }
        Column(Modifier.imePadding().padding(8.dp)) {
            if (editing == null) PhotoPicker(photo, !busy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(5000) }, placeholder = { Text("رسالتك…") }, enabled = !busy, modifier = Modifier.weight(1f).testTag("message-input"), maxLines = 4)
                IconButton(onClick = {
                    val done = { text = ""; reply = null; editing = null; photo.image = null }
                    val id = editing
                    if (id == null) send(text, reply, photo.image, photo.ratio, done) else edit(id, text, done)
                }, enabled = !busy && !photo.busy && (text.isNotBlank() || (editing == null && photo.image != null)), modifier = Modifier.testTag("message-send")) { Icon(Icons.Outlined.Send, "إرسال", tint = AccentLight) }
            }
        }
    }
    val chosen: Message? = messages[selected]
    if (chosen != null && !chosen.deleted) AlertDialog(onDismissRequest = { selected = null }, title = { Text("خيارات الرسالة") }, text = { Column {
        TextButton(onClick = { reply = chosen.id; editing = null; selected = null }, enabled = !busy) { Text("رد") }
        if (chosen.sender == self) {
            TextButton(onClick = { editing = chosen.id; text = chosen.text; reply = null; photo.image = null; selected = null }, enabled = !busy) { Text("تعديل") }
            TextButton(onClick = { removing = chosen.id; selected = null }, enabled = !busy) { Text("حذف") }
        }
        ReactionPicker(chosen.reactions[self], busy) { kind -> react(chosen.id, kind); selected = null }
    } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("إغلاق") } })
    removing?.let { id -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("حذف الرسالة للجميع؟") }, confirmButton = { TextButton(onClick = { removing = null; delete(id) }, enabled = !busy) { Text("حذف") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("إلغاء") } }) }
}
