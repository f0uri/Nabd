package app.nabd.nativeapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import app.nabd.nativeapp.data.Message
import app.nabd.nativeapp.data.Thread
import java.time.Duration
import java.time.Instant

/** Bubble geometry of the design system: 20dp corners, 6dp on the sending side. */
private fun bubbleShape(mine: Boolean) = if (mine) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)

/** Vector receipt: one grey tick when the message arrived, two blue ticks once it was read. */
@Composable private fun ReadTick(read: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(width = 19.dp, height = 13.dp)) {
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val tint = if (read) Color(0xFF0084FF) else Color(0xFF94A3B8)
        val draw = { shift: Float ->
            val path = Path()
            path.moveTo(shift + 1f, 7f)
            path.lineTo(shift + 5f, 11f)
            path.lineTo(shift + 12f, 2.5f)
            drawPath(path, tint, style = stroke)
        }
        draw(0f)
        if (read) draw(6.5f)
    }
}

/** Three dots moving inside a white glass bubble while a real send is in flight. */
@Composable private fun TypingBubble() {
    val shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth(fraction = .7f), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.nabdShadow(shape, elevation = 10.dp).clip(shape).background(CardFill)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    val transition = rememberInfiniteTransition(label = "chat-dot-$index")
                    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(760, easing = LinearEasing, delayMillis = index * 130), RepeatMode.Restart), label = "dot-$index")
                    val lift = (kotlin.math.sin(phase * 2.0 * Math.PI).toFloat() + 1f) / 2f
                    Box(Modifier.size(7.dp).offset(y = (-4 * lift).dp).background(Muted.copy(alpha = .45f + .55f * lift), CircleShape))
                }
            }
        }
    }
}

/** Last presence of the other account, read from their real last message. */
internal fun lastSeenLabel(thread: Thread, self: String): String {
    val last = thread.messages.filter { it.sender != self && it.date.isNotBlank() }.maxByOrNull { it.date } ?: return "غير متصل الآن"
    val elapsed = runCatching { Duration.between(Instant.parse(last.date), Instant.now()).seconds.coerceAtLeast(0) }.getOrDefault(Long.MAX_VALUE)
    return when {
        elapsed < 300 -> "متصل الآن"
        elapsed < 3600 -> "آخر ظهور منذ ${elapsed / 60} دقيقة"
        elapsed < 86400 -> "آخر ظهور منذ ${elapsed / 3600} ساعة"
        else -> "آخر ظهور منذ ${elapsed / 86400} يوم"
    }
}

@Composable internal fun RichChat(thread: Thread, self: String, busy: Boolean, modifier: Modifier, back: () -> Unit,
    send: (String, String?, String?, Double, () -> Unit) -> Unit,
    edit: (String, String, () -> Unit) -> Unit, delete: (String) -> Unit, react: (String, String?) -> Unit, read: () -> Unit, refresh: () -> Unit) {
    var text by rememberSaveable(thread.id) { mutableStateOf("") }
    var reply by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var selected by remember(thread.id) { mutableStateOf<String?>(null) }
    var removing by remember(thread.id) { mutableStateOf<String?>(null) }
    var pending by remember(thread.id) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val refreshNow by rememberUpdatedState(refresh)
    LaunchedEffect(owner, thread.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(20000); refreshNow() }
        }
    }
    val photo = remember(thread.id) { PhotoDraft() }
    val launcher = rememberPhotoLauncher(photo)
    val unread = thread.messages.filter { it.sender != self && !it.read }.joinToString(":") { it.id }
    var attemptedRead by remember(thread.id) { mutableStateOf<String?>(null) }
    // A failed receipt must not cause an endless busy->idle network retry loop.
    LaunchedEffect(thread.id, unread, busy) {
        if (!busy && unread.isNotEmpty() && attemptedRead != unread) { attemptedRead = unread; read() }
    }
    val messages = remember(thread.messages) { thread.messages.associateBy { it.id } }
    // A message that never lands must not leave the typing bubble on screen.
    LaunchedEffect(pending) { if (pending) { delay(25000); pending = false } }
    val online = remember(thread.messages) { lastSeenLabel(thread, self) == "متصل الآن" }
    Column(modifier.fillMaxSize()) {
        // Header: avatar with the presence dot, verified name, refresh.
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .nabdShadow(CircleShape, elevation = 14.dp).clip(CircleShape).background(CardFill).border(1.dp, Color(0x66FFFFFF), CircleShape)) {
            Box(Modifier.matchParentSize().glassBlur(22.dp).background(Color(0x1AFFFFFF)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircle(Icons.Outlined.ArrowForward, "رجوع", back, size = 36.dp)
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(thread.other, 42)
                    Box(Modifier.size(13.dp).clip(CircleShape).background(if (online) Color(0xFF10B981) else Outline).border(2.dp, Color.White, CircleShape))
                }
                Column(Modifier.weight(1f)) {
                    PersonName(thread.other, size = 15)
                    Text(if (online) "متصل الآن" else lastSeenLabel(thread, self), color = if (online) Color(0xFF10B981) else Muted, fontSize = 11.sp)
                }
                GlassCircle(Icons.Outlined.Refresh, "تحديث الرسائل", { attemptedRead = null; refresh() }, enabled = !busy, size = 36.dp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pending) item("pending") { TypingBubble() }
            items(thread.messages.reversed(), key = { it.id }) { m ->
                val mine = m.sender == self
                val shape = bubbleShape(mine)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                    Box(Modifier.widthIn(max = 300.dp)) {
                        Box(Modifier.nabdShadow(shape, elevation = if (mine) 12.dp else 10.dp).clip(shape)
                            .then(if (mine) Modifier.background(NabdBlue) else Modifier.background(CardFill))
                            .then(if (mine) Modifier else Modifier.border(1.dp, Color(0x66FFFFFF), shape))) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                m.replyToId?.let { id ->
                                    val quoted = messages[id]?.let { if (it.deleted) "رسالة محذوفة" else it.text.ifBlank { "صورة" } } ?: "رسالة سابقة"
                                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (mine) Color(0x33FFFFFF) else Raised).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text("رد: $quoted", maxLines = 2, fontSize = 12.sp, lineHeight = 17.sp, color = if (mine) Color.White.copy(alpha = .9f) else Muted)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                if (m.image != null && !m.deleted) Box(Modifier.fillMaxWidth(.7f).clip(MediaShape).background(Raised)) {
                                    NabdImage(m.image, "صورة الرسالة", Modifier.fillMaxWidth().heightIn(max = 220.dp), ContentScale.Crop)
                                }
                                if (m.image != null && !m.deleted && m.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                                if (m.text.isNotBlank() || m.deleted) Text(if (m.deleted) "تم حذف الرسالة" else m.text,
                                    color = if (mine) Color.White else TextMain, fontSize = 14.5.sp, lineHeight = 22.sp)
                                if (m.reactions.isNotEmpty()) Text(m.reactions.values.groupingBy { it }.eachCount().entries.joinToString(" · ") { "${reactionLabel(it.key)} ${it.value}" },
                                    fontSize = 12.sp, color = if (mine) Color.White.copy(alpha = .85f) else Muted)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(clockTime(m.date), fontSize = 10.5.sp, color = if (mine) Color.White.copy(alpha = .8f) else Muted)
                                    if (mine) ReadTick(m.read)
                                    if (m.edited && !m.deleted) Text("معدّلة", fontSize = 10.sp, color = if (mine) Color.White.copy(alpha = .8f) else Muted)
                                    if (!m.deleted) Box(Modifier.size(26.dp).clip(CircleShape)
                                        .pressClick { selected = m.id }.testTag("message-actions-${m.id}"), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.MoreHoriz, "خيارات الرسالة", tint = if (mine) Color.White.copy(alpha = .9f) else Muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.imePadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photo.image != null) Row(Modifier.fillMaxWidth().height(96.dp).clip(MediaShape).background(Raised)) {
                Box(Modifier.weight(1f)) { NabdImage(photo.image, "الصورة المختارة", Modifier.fillMaxSize(), ContentScale.Crop) }
                Box(Modifier.fillMaxHeight().width(56.dp).background(Color(0x990F172A)).pressClick { photo.image = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "إزالة الصورة", tint = Color.White)
                }
            }
            if (reply != null || editing != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Raised).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing != null) Icons.Outlined.Edit else Icons.AutoMirrored.Outlined.Reply, null, tint = Accent, modifier = Modifier.size(17.dp))
                Text(if (editing != null) "تعديل الرسالة" else "الرد على: ${messages[reply]?.text?.ifBlank { "صورة" } ?: "رسالة"}", Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 2, fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp)
                Box(Modifier.size(26.dp).clip(CircleShape).pressClick { reply = null; editing = null; text = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "إلغاء الرد أو التعديل", tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
            // Floating glass input capsule: attach, field, gradient send.
            Box(Modifier.fillMaxWidth().nabdShadow(CircleShape, elevation = 16.dp).clip(CircleShape).background(CardFill).border(1.dp, Color(0x66FFFFFF), CircleShape)) {
                Box(Modifier.matchParentSize().glassBlur(22.dp).background(Color(0x1AFFFFFF)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassCircle(Icons.Outlined.Image, "إرفاق صورة", { if (editing == null) launcher.launch("image/*") }, enabled = !busy && !photo.busy, size = 38.dp)
                    NabdField(text, { text = it.take(5000) }, "رسالتك…", Modifier.weight(1f), enabled = !busy, singleLine = false, maxLines = 4, tag = "message-input")
                    val ready = !busy && !photo.busy && (text.isNotBlank() || (editing == null && photo.image != null))
                    val press = rememberPress()
                    Box(Modifier.size(44.dp).clip(CircleShape).then(if (ready) Modifier.background(NabdBlue) else Modifier.background(Raised))
                        .pressClick(press = press, enabled = ready, onClick = {
                            val done = { text = ""; reply = null; editing = null; photo.image = null; pending = false }
                            val id = editing
                            if (id != null) edit(id, text, done) else { pending = true; send(text, reply, photo.image, photo.ratio, done) }
                        }).testTag("message-send"), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "إرسال", tint = if (ready) Color.White else Muted, modifier = Modifier.size(21.dp))
                    }
                }
            }
            if (photo.error != null) Text(photo.error!!, color = AccentLight, fontSize = 12.sp)
        }
    }
    val chosen: Message? = messages[selected]
    if (chosen != null && !chosen.deleted) GlassWindow(close = { selected = null }) {
        Text("خيارات الرسالة", color = TextMain, fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
        GlassMenuItem("رد", Icons.AutoMirrored.Outlined.Reply) { reply = chosen.id; editing = null; selected = null }
        if (chosen.sender == self) {
            GlassMenuItem("تعديل", Icons.Outlined.Edit) { editing = chosen.id; text = chosen.text; reply = null; photo.image = null; selected = null }
            GlassMenuItem("حذف", Icons.Outlined.DeleteOutline, danger = true) { removing = chosen.id; selected = null }
        }
        ReactionPicker(chosen.reactions[self], busy) { kind -> react(chosen.id, kind); selected = null }
        PillButton("إغلاق", { selected = null }, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
    if (removing != null) GlassConfirm("حذف الرسالة للجميع؟", "الرسالة غتتحيد عندك وعند الطرف الآخر.", "حذف",
        onConfirm = { val id = removing!!; removing = null; delete(id) }, close = { removing = null }, busy = busy, danger = true)
}

/** One row of the glass message menu: pill-shaped, haptic, real callback. */
@Composable private fun GlassMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val press = rememberPress()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (danger) AccentLight.copy(alpha = .10f) else Raised)
        .pressClick(press = press, enabled = true, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = if (danger) AccentLight else Accent, modifier = Modifier.size(20.dp))
        Text(label, color = if (danger) AccentLight else TextMain, fontSize = 14.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    }
}
