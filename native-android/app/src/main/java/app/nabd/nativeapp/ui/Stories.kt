package app.nabd.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.Story
import app.nabd.nativeapp.data.StoryGroup
import app.nabd.nativeapp.data.isAdminHandle
import kotlinx.coroutines.delay

private const val STORY_MS = 5000L
private val StoryRing = Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF00C6FF)))
private val StoryRingSeen = Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1)))
private val StoryRingAdmin = Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFFFB7185)))

/** Stories tray above the composer: my story first, the official account in front. */
@Composable internal fun StoriesTray(groups: List<StoryGroup>, me: Person, seen: Set<String>, busy: Boolean, onCreate: () -> Unit, onOpen: (StoryGroup) -> Unit) {
    val mine = remember(groups, me) { groups.firstOrNull { it.owner.id == me.id } }
    val others = remember(groups, me) { groups.filter { it.owner.id != me.id } }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("stories-tray")) {
        item("create") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(68.dp).clip(CircleShape).background(Raised)
                    .border(2.5.dp, if (mine == null) StoryRingSeen else StoryRing, CircleShape)
                    .clickable(enabled = !busy) { if (mine != null) onOpen(mine) else onCreate() }, contentAlignment = Alignment.Center) {
                    Avatar(me, 58)
                    Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(Accent).border(2.dp, Color.White, CircleShape)
                        .clickable(enabled = !busy) { onCreate() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, "إضافة قصة", tint = Color.White, modifier = Modifier.size(15.dp).testTag("story-create"))
                    }
                }
                Text("قصتي", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(others, key = { it.owner.id }) { group ->
            val watched = group.items.all { it.id in seen }
            val brush = if (isAdminHandle(group.owner.username)) StoryRingAdmin else if (watched) StoryRingSeen else StoryRing
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(68.dp).clip(CircleShape).border(2.5.dp, brush, CircleShape)
                    .clickable { onOpen(group) }.padding(3.dp), contentAlignment = Alignment.Center) {
                    Avatar(group.owner, 58)
                }
                PersonName(group.owner, size = 11, badge = false)
                Text("${group.items.size}", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

/** Ring around the post avatar when that account really has a live story. */
@Composable internal fun StoryAvatar(person: Person, size: Int, ring: Boolean, admin: Boolean, click: (() -> Unit)?) {
    val brush = if (admin) StoryRingAdmin else StoryRing
    Box(Modifier.size((size + 6).dp).clip(CircleShape).then(if (ring) Modifier.border(2.5.dp, brush, CircleShape) else Modifier)
        .then(if (click != null) Modifier.clickable(onClick = click) else Modifier).padding(if (ring) 3.dp else 0.dp), contentAlignment = Alignment.Center) {
        Avatar(person, size)
    }
}

/**
 * Full-screen Instagram-style viewer. Progress, gestures, header and the reply
 * bar are all driven by real story objects loaded from the shared vault.
 */
@Composable internal fun StoryViewerScreen(groups: List<StoryGroup>, startOwner: String, startStory: String?, me: Person, busy: Boolean, error: String?,
    onClose: () -> Unit, onSeen: (String) -> Unit, onDelete: (String) -> Unit, onReply: (Person, String) -> Unit) {
    var index by remember { mutableIntStateOf(groups.indexOfFirst { it.owner.id == startOwner }.coerceAtLeast(0)) }
    var page by remember { mutableIntStateOf(groups.getOrNull(index)?.items?.indexOfFirst { it.id == startStory }?.coerceAtLeast(0) ?: 0) }
    var paused by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0f) }
    var reply by remember { mutableStateOf("") }
    var elapsed by remember { mutableStateOf(0L) }
    var menu by remember { mutableStateOf(false) }
    val group = groups.getOrNull(index)
    val items = group?.items.orEmpty()
    val story = items.getOrNull(page)
    val scale by animateFloatAsState(targetValue = if (closing) .78f else 1f, animationSpec = spring(dampingRatio = .85f, stiffness = 380f), label = "story-scale")
    val enter by animateFloatAsState(targetValue = 1f, animationSpec = tween(220), label = "story-enter")
    LaunchedEffect(index, page) { elapsed = 0L }
    LaunchedEffect(closing) { if (closing) { delay(170); onClose() } }
    LaunchedEffect(story?.id) { story?.let { onSeen(it.id) } }
    LaunchedEffect(index, page, paused) {
        if (story == null) return@LaunchedEffect
        while (!paused && elapsed < STORY_MS) { delay(50); elapsed = (elapsed + 50).coerceAtMost(STORY_MS) }
        if (paused) return@LaunchedEffect
        if (page + 1 < items.size) page++ else if (index + 1 < groups.size) { index++; page = 0 } else if (!closing) closing = true
    }
    if (story == null) { LaunchedEffect(Unit) { onClose() }; return }
    Box(Modifier.fillMaxSize().background(Color(0xFF05070D)).testTag("story-viewer")
        .pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = { drag = 0f }) { _, amount ->
                val next = (drag + amount).coerceAtLeast(0f); drag = next; if (next > 190f && !closing) closing = true
            }
        }
        .graphicsLayer { scaleX = scale * (.94f + .06f * enter); scaleY = scale * (.94f + .06f * enter); alpha = enter; translationY = drag }) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onPress = { paused = true; try { tryAwaitRelease() } finally { paused = false } },
                onTap = { offset -> if (offset.x > size.width / 2f) {
                    if (page + 1 < items.size) page++ else if (index + 1 < groups.size) { index++; page = 0 } else closing = true
                } else { if (page > 0) page-- else if (index > 0) { index--; page = 0 } } })
        }) {
            if (story.image != null) NabdImage(story.image, "صورة القصة", Modifier.fillMaxSize().background(Color(0xFF0B1220)), ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF0B1220)))), contentAlignment = Alignment.Center) {
                Text(story.text, Modifier.padding(32.dp), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 34.sp)
            }
            if (story.image != null && story.text.isNotBlank()) Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(18.dp)).background(Color(0x99000000)).padding(18.dp)) {
                Text(story.text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 26.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, start = 10.dp, end = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { position, item ->
                    val value = when { position < page -> 1f; position == page -> (elapsed / STORY_MS.toFloat()).coerceIn(0f, 1f); else -> 0f }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(value).height(3.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(story.owner, 36)
                Column(Modifier.weight(1f)) {
                    PersonName(story.owner, size = 14, color = Color.White, badge = false)
                    Text(relativePostDate(story.date), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                }
                if (story.owner.id == me.id) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).pressClick(enabled = !busy) { menu = true }.testTag("story-menu"), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.MoreVert, "خيارات القصة", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                            modifier = Modifier.background(CardFill, RoundedCornerShape(20.dp))) {
                            DropdownMenuItem(text = { Text("حذف القصة") }, onClick = { menu = false; onDelete(story.id) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) })
                        }
                    }
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)).pressClick { closing = true }.testTag("story-close"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "إغلاق القصة", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()) {
            if (paused) Text("موقوف", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp), color = Color(0xCCFFFFFF), fontSize = 11.sp)
            if (story.owner.id != me.id) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("like", "fire", "wow").forEach { kind ->
                        val label = if (kind == "fire") "نار" else reactionLabel(kind)
                        Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x33FFFFFF))
                            .pressClick(enabled = !busy) { onReply(story.owner, "$label على قصتك") }
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (kind == "fire") Icon(Icons.Outlined.LocalFireDepartment, null, tint = Color(0xFFFFB020), modifier = Modifier.size(18.dp))
                            else ReactionGlyph(kind, size = 19.dp)
                            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("story-react-$label"))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = reply, onValueChange = { reply = it.take(500) }, placeholder = { Text("رد على القصة...", color = Color(0xAAFFFFFF), fontSize = 13.sp) },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("story-reply"), maxLines = 3, shape = RoundedCornerShape(999.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0x22FFFFFF), unfocusedContainerColor = Color(0x22FFFFFF),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = GlassEdge, unfocusedBorderColor = Color(0x44FFFFFF)))
                    Box(Modifier.size(50.dp).clip(CircleShape).then(if (reply.isNotBlank()) Modifier.background(NabdBlue) else Modifier.background(Color(0x33FFFFFF)))
                        .pressClick(enabled = !busy && reply.isNotBlank()) { onReply(story.owner, reply); reply = "" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Send, "إرسال الرد", tint = Color.White, modifier = Modifier.size(21.dp).testTag("story-send"))
                    }
                }
            }
            error?.let { Text(it, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp), color = Color(0xFFFFC9D1), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
