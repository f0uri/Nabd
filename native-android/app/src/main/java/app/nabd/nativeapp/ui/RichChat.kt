package app.nabd.nativeapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import app.nabd.nativeapp.data.Message
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.VoiceNote
import app.nabd.nativeapp.data.ReelRef
import app.nabd.nativeapp.data.Thread
import app.nabd.nativeapp.data.findReel
import app.nabd.nativeapp.data.withoutReel
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
    edit: (String, String, () -> Unit) -> Unit, delete: (String) -> Unit, react: (String, String?) -> Unit, read: () -> Unit, refresh: () -> Unit,
    sendVoice: (String, Int, List<Int>, () -> Unit) -> Unit = { _, _, _, _ -> }, onProfile: (Person) -> Unit = {}, onRetry: (Message) -> Unit = {}, muted: Boolean = false, onMute: (Boolean) -> Unit = {}, onClear: () -> Unit = {}, onBlock: () -> Unit = {},
    onDeleteForMe: (String) -> Unit = {}, onDeleteForAll: (String) -> Unit = {}) {
    var text by rememberSaveable(thread.id) { mutableStateOf("") }
    var reply by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(thread.id) { mutableStateOf<String?>(null) }
    var selected by remember(thread.id) { mutableStateOf<String?>(null) }
    var removing by remember(thread.id) { mutableStateOf<String?>(null) }
    var pending by remember(thread.id) { mutableStateOf(false) }
    var voicePlaying by remember(thread.id) { mutableStateOf<String?>(null) }
    var recording by remember(thread.id) { mutableStateOf(false) }
    var seconds by remember(thread.id) { mutableIntStateOf(0) }
    var bars by remember(thread.id) { mutableStateOf(listOf<Int>()) }
    var voiceError by remember(thread.id) { mutableStateOf<String?>(null) }
    var holding by remember(thread.id) { mutableStateOf<String?>(null) }
    var menu by remember(thread.id) { mutableStateOf(false) }
    var clearing by remember(thread.id) { mutableStateOf(false) }
    var blocking by remember(thread.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorder(context) }
    DisposableVoicePlayer()
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start()) { recording = true; seconds = 0; bars = emptyList(); voiceError = null }
        else if (!granted) voiceError = "خوّل الميكروفون من إعدادات التطبيق."
    }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        while (recording) { delay(100); recorder.sample(); bars = recorder.bars; seconds = bars.size / 10; if (seconds >= app.nabd.nativeapp.data.MAX_VOICE_SECONDS) break }
    }
    // Only one reel ever plays, and it stops with the composition that owns it.
    var reelPlaying by remember(thread.id) { mutableStateOf<String?>(null) }
    var reelFull by remember(thread.id) { mutableStateOf<ReelRef?>(null) }
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
    val days = remember(thread.messages) {
        val out = mutableMapOf<String, String>(); var last = ""
        thread.messages.forEach { m -> val key = m.date.take(10); if (key != last) { dayLabel(m.date)?.let { out[m.id] = it }; last = key } }
        out
    }
    val starts = remember(thread.messages) {
        val out = mutableSetOf<String>(); var previous: Message? = null
        thread.messages.forEach { m ->
            val before = previous
            if (before == null || before.sender != m.sender || runCatching { java.time.Duration.between(java.time.Instant.parse(before.date), java.time.Instant.parse(m.date)).toMinutes() > 5 }.getOrDefault(false)) out += m.id
            previous = m
        }
        out
    }
    // A message that never lands must not leave the typing bubble on screen.
    LaunchedEffect(pending) { if (pending) { delay(25000); pending = false } }
    val online = remember(thread.messages) { lastSeenLabel(thread, self) == "متصل الآن" }
    Column(modifier.fillMaxSize()) {
        // Header: avatar with the presence dot, verified name, refresh.
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .nabdShadow(CircleShape, elevation = 14.dp).clip(CircleShape).background(CardFill).border(1.dp, GlassEdge, CircleShape)) {
            Box(Modifier.matchParentSize().glassBlur(22.dp).background(GlassSheen))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircle(Icons.Outlined.ArrowForward, "رجوع", back, size = 36.dp)
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(Modifier.pressClick { onProfile(thread.other) }) {
                        Avatar(thread.other, 42)
                        Box(Modifier.size(13.dp).clip(CircleShape).background(if (online) Color(0xFF10B981) else Outline).border(2.dp, Color.White, CircleShape))
                    }
                }
                Column(Modifier.weight(1f).pressClick { onProfile(thread.other) }) {
                    PersonName(thread.other, size = 15)
                    Text(if (online) "متصل الآن" else lastSeenLabel(thread, self), color = if (online) Color(0xFF10B981) else Muted, fontSize = 11.sp)
                }
                GlassCircle(Icons.Outlined.Refresh, "تحديث الرسائل", { attemptedRead = null; refresh() }, enabled = !busy, size = 36.dp)
                if (muted) Icon(Icons.Outlined.NotificationsOff, null, tint = Muted, modifier = Modifier.size(18.dp))
                GlassCircle(Icons.Outlined.MoreVert, "خيارات المحادثة", { menu = true }, size = 36.dp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pending) item("pending") { TypingBubble() }
            items(thread.messages.reversed(), key = { it.id }) { m ->
                // Leaving the list stops playback. Pressing play must never dispose the
                // player, so the effect is keyed on the message only and reads the live value.
                val playingHere = rememberUpdatedState(reelPlaying)
                DisposableEffect(m.id) { onDispose { if (playingHere.value == m.id) reelPlaying = null } }
                val mine = m.sender == self
                val shape = bubbleShape(mine)
                var dragX by remember(m.id) { mutableFloatStateOf(0f) }
                val shift by animateFloatAsState(targetValue = dragX, animationSpec = tween(if (dragX == 0f) 240 else 0), label = "swipe-${m.id}")
                val drag = rememberDraggableState { delta -> dragX = (dragX + delta).coerceIn(0f, 90f) }
                val day = days[m.id]
                val start = starts.contains(m.id)
                Column(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(280))) {
                    AnimatedVisibility(visible = day != null, enter = fadeIn(animationSpec = tween(280)), exit = fadeOut(animationSpec = tween(280))) {
                        if (day != null) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(day, Modifier.clip(CircleShape).background(Raised).border(1.dp, GlassEdge, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                                color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                Row(Modifier.fillMaxWidth().then(if (start) Modifier.padding(top = 8.dp) else Modifier), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!mine) {
                            if (start) Box(Modifier.pressClick { onProfile(thread.other) }.padding(bottom = 2.dp)) { Avatar(thread.other, 28) }
                            else Spacer(Modifier.size(28.dp))
                        }
                        if (shift > 4f && !m.deleted) Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = Accent,
                            modifier = Modifier.size(18.dp).alpha((shift / 56f).coerceIn(0f, 1f)))
                        Box(Modifier.widthIn(max = 300.dp).then(if (m.pending) Modifier.alpha(.72f) else Modifier).offset { IntOffset(shift.roundToInt(), 0) }
                            .draggable(state = drag, orientation = Orientation.Horizontal, onDragStopped = {
                                if (dragX > 56f && !m.deleted) { reply = m.id; editing = null }
                                dragX = 0f
                            })
                            .pointerInput(m.id) { detectTapGestures(onLongPress = { if (!m.deleted) holding = m.id }) }) {
                        Box(Modifier.nabdShadow(shape, elevation = if (mine) 12.dp else 10.dp).clip(shape)
                            .then(if (mine) Modifier.background(NabdBlue) else Modifier.background(CardFill))
                            .then(if (mine) Modifier else Modifier.border(1.dp, GlassEdge, shape))) {
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
                                val reel = remember(m.text, m.deleted) { if (m.deleted) null else findReel(m.text) }
                                val body = if (reel != null) withoutReel(m.text, reel) else m.text
                                if (m.audio != null && !m.deleted) {
                                    VoiceBubble(m.audio, mine, m.id, playing = voicePlaying == m.id, onPlay = {
                                        if (voicePlaying == m.id) { VoicePlayer.stop(); voicePlaying = null }
                                        else { VoicePlayer.toggle(context, m.id, m.audio) { voicePlaying = null }; voicePlaying = m.id }
                                    })
                                    Spacer(Modifier.height(6.dp))
                                }
                                if ((body.isNotBlank() || m.deleted) && m.audio == null) Text(if (m.deleted) "تم حذف الرسالة" else body,
                                    color = if (mine) Color.White else TextMain, fontSize = 14.5.sp, lineHeight = 22.sp)
                                if (!m.deleted) MessageLinkPreview(text = body, mine = mine)
                                if (reel != null) {
                                    if (body.isNotBlank()) Spacer(Modifier.height(6.dp))
                                    ReelCard(reel, Modifier.width(reelCardWidth()), playing = reelPlaying == m.id,
                                        onPlay = { reelPlaying = m.id }, onStop = { reelPlaying = null }, onExpand = { reelFull = reel })
                                }
                                if (m.failed) PillButton("إعادة المحاولة", { onRetry(m) }, brush = null, height = 36.dp, fontSize = 12, icon = Icons.Outlined.Refresh)
                                if (m.reactions.isNotEmpty()) Text(m.reactions.values.groupingBy { it }.eachCount().entries.joinToString(" · ") { "${reactionLabel(it.key)} ${it.value}" },
                                    fontSize = 12.sp, color = if (mine) Color.White.copy(alpha = .85f) else Muted)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(clockTime(m.date), fontSize = 10.5.sp, color = if (mine) Color.White.copy(alpha = .8f) else Muted)
                                    if (m.pending && !m.failed) Icon(Icons.Outlined.Schedule, "جارٍ الإرسال", tint = if (mine) Color.White.copy(alpha = .85f) else Muted, modifier = Modifier.size(11.dp))
                                    if (m.failed) Text("تعذّر الإرسال", fontSize = 10.sp, color = AccentLight)
                                    if (mine && !m.pending) ReadTick(m.read)
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
            if (recording) {
                VoiceBar(seconds, bars, onCancel = { recorder.cancel(); recording = false; bars = emptyList() }, onSend = {
                    val note = recorder.stop(); recording = false
                    if (note == null) voiceError = "تعذّر التسجيل."
                    else { voiceError = null; sendVoice(note.first, note.second, note.third) { } }
                })
            } else {
            // Floating glass input capsule: attach, field, gradient send.
            Box(Modifier.fillMaxWidth().nabdShadow(CircleShape, elevation = 16.dp).clip(CircleShape).background(CardFill).border(1.dp, GlassEdge, CircleShape)) {
                Box(Modifier.matchParentSize().glassBlur(22.dp).background(GlassSheen))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassCircle(Icons.Outlined.Image, "إرفاق صورة", { if (editing == null) launcher.launch("image/*") }, enabled = !busy && !photo.busy, size = 38.dp)
                    GlassCircle(Icons.Outlined.Mic, "تسجيل صوتي", {
                        if (busy || editing != null) return@GlassCircle
                        if (recording) {
                            val note = recorder.stop(); recording = false
                            if (note == null) voiceError = "تعذّر التسجيل."
                            else if (!app.nabd.nativeapp.data.validAudio(note.first)) voiceError = "التسجيل طويل بزاف."
                            else { voiceError = null; sendVoice(note.first, note.second, note.third) { } }
                        } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            if (recorder.start()) { recording = true; seconds = 0; bars = emptyList(); voiceError = null } else voiceError = "تعذّر تشغيل الميكروفون."
                        } else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }, enabled = !busy && editing == null, size = 38.dp, tint = if (recording) AccentLight else Accent, modifier = Modifier.testTag("message-mic"))
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
            }
            if (photo.error != null) Text(photo.error!!, color = AccentLight, fontSize = 12.sp)
            if (voiceError != null) Text(voiceError!!, color = AccentLight, fontSize = 12.sp)
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
    if (menu) GlassWindow(close = { menu = false }) {
        Text("خيارات المحادثة", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
        GlassMenuItem("عرض الملف الشخصي", Icons.Outlined.Person) { menu = false; onProfile(thread.other) }
        GlassMenuItem(if (muted) "إلغاء كتم الإشعارات" else "كتم الإشعارات", Icons.Outlined.NotificationsOff) { menu = false; onMute(!muted) }
        GlassMenuItem("مسح جميع الرسائل", Icons.Outlined.DeleteSweep, danger = true) { menu = false; clearing = true }
        GlassMenuItem("حظر @${thread.other.username}", Icons.Outlined.Block, danger = true) { menu = false; blocking = true }
        PillButton("إغلاق", { menu = false }, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
    val held: Message? = messages[holding]
    if (held != null) GlassWindow(close = { holding = null }) {
        Text("خيارات الرسالة", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
        GlassMenuItem("رد", Icons.AutoMirrored.Outlined.Reply) { reply = held.id; editing = null; holding = null }
        GlassMenuItem("نسخ", Icons.Outlined.ContentCopy) { clipboard.setText(AnnotatedString(held.text)); holding = null }
        ReactionPicker(held.reactions[self], busy) { kind -> react(held.id, kind); holding = null }
        if (held.sender == self) GlassMenuItem("حذف للجميع", Icons.Outlined.DeleteOutline, danger = true) { removing = held.id; holding = null }
        GlassMenuItem("حذف لديّ", Icons.Outlined.DeleteOutline, danger = true) { onDeleteForMe(held.id); holding = null }
        PillButton("إغلاق", { holding = null }, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
    if (clearing) GlassConfirm("مسح جميع الرسائل؟", "سيتم حذف جميع رسائل هذه المحادثة لديك نهائياً ولا يمكن التراجع.", "مسح",
        onConfirm = { clearing = false; onClear() }, close = { clearing = false }, busy = busy, danger = true)
    if (blocking) GlassConfirm("حظر @${thread.other.username}؟",
        "لن يستطيع مراسلتك ولن تظهر لك منشوراته. يمكنك إلغاء الحظر من إعدادات الخصوصية في أي وقت.", "حظر",
        onConfirm = { blocking = false; onBlock() }, close = { blocking = false }, busy = busy, danger = true)
    if (reelFull != null) ReelViewer(reelFull!!) { reelFull = null }
    if (removing != null) GlassConfirm("حذف الرسالة للجميع؟", "الرسالة غتتحيد عندك وعند الطرف الآخر.", "حذف",
        onConfirm = { val id = removing!!; removing = null; delete(id) }, close = { removing = null }, busy = busy, danger = true)
}

/** "اليوم"، "أمس" أو التاريخ: the separator between two days of conversation. */
private fun dayLabel(date: String): String? {
    val instant = runCatching { java.time.Instant.parse(date) }.getOrNull() ?: return null
    val day = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (day) {
        today -> "اليوم"
        today.minusDays(1) -> "أمس"
        else -> String.format(java.util.Locale.US, "%02d/%02d/%04d", day.dayOfMonth, day.monthValue, day.year)
    }
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
