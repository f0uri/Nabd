package app.nabd.nativeapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Comment
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.inlineImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Independent Facebook-style comments screen: a 92% bottom sheet with its own
 * post summary, nested replies, a keyboard-aware composer and real counters.
 * Nothing here is mocked: every comment comes from the shared vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CommentsSheet(post: FeedPost, me: Person, busy: Boolean, error: String?, close: () -> Unit,
    send: (String, String?, String?, () -> Unit) -> Unit, like: (String) -> Unit, delete: (String) -> Unit, onImage: (String) -> Unit = {}) {
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var removing by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val roots = remember(post.comments) { post.comments.filter { it.replyTo == null }.asReversed() }
    val replies = remember(post.comments) { post.comments.filter { it.replyTo != null }.groupBy { it.replyTo } }
    ModalBottomSheet(onDismissRequest = close, sheetState = state, modifier = Modifier.fillMaxHeight(.92f),
        scrimColor = Color(0x660A1628), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), containerColor = Canvas,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Outline)) }) {
        Column(Modifier.fillMaxSize().testTag("comments-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("التعليقات", fontSize = 19.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${post.comments.size}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("comments-count"))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).background(Raised).clickable(onClick = close), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "إغلاق التعليقات", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(post.author, 38)
                    Column(Modifier.weight(1f)) {
                        PersonName(post.author, size = 14)
                        Text(post.text.ifBlank { "منشور بصورة" }, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (roots.isEmpty()) Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.ModeComment, null, tint = Outline, modifier = Modifier.size(46.dp))
                    Text("كن أول من يعلّق", Modifier.padding(top = 12.dp), color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(roots, key = { it.id }) { root ->
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { shown = true }
                        val shift by animateFloatAsState(targetValue = if (shown) 0f else 70f, animationSpec = spring(dampingRatio = .8f, stiffness = 320f), label = "comment-shift")
                        val fade by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(260), label = "comment-fade")
                        Box(Modifier.graphicsLayer { translationY = shift; alpha = fade }) {
                            Column(Modifier.animateContentSize()) {
                                CommentCard(root, me, busy, nested = false, canDelete = root.author.id == me.id || post.author.id == me.id || app.nabd.nativeapp.data.isAdminHandle(me.username), onImage = onImage,
                                    onLike = { like(root.id) }, onReply = { replyTo = root }, onDelete = { removing = root.id })
                                val children = replies[root.id].orEmpty()
                                if (children.isNotEmpty()) {
                                    val open = root.id in expanded
                                    TextButton(onClick = { expanded = if (open) expanded - root.id else expanded + root.id }, modifier = Modifier.padding(start = 42.dp, top = 4.dp)) {
                                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (open) "إخفاء الردود" else "عرض الردود (${children.size})", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                            modifier = Modifier.testTag("toggle-replies-${root.id}"))
                                    }
                                    if (open) children.forEach { child ->
                                        Row(Modifier.padding(top = 6.dp)) {
                                            Spacer(Modifier.width(26.dp))
                                            Box(Modifier.weight(1f)) { CommentCard(child, me, busy, nested = true, canDelete = child.author.id == me.id || post.author.id == me.id || app.nabd.nativeapp.data.isAdminHandle(me.username), onImage = onImage,
                                            onLike = { like(child.id) }, onReply = { replyTo = root }, onDelete = { removing = child.id }) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Surface).imePadding().navigationBarsPadding()) {
                replyTo?.let { parent ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = Accent, modifier = Modifier.size(15.dp))
                        Text("الرد على ${parent.author.name}", Modifier.weight(1f).padding(horizontal = 6.dp), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Raised).clickable { replyTo = null }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "إلغاء الرد", tint = Muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                CommentComposer(text = text, change = { text = it.take(2000) }, me = me, busy = busy, image = image,
                    attach = { image = it }, clearImage = { image = null }, send = {
                        val body = text
                        send(body, image, replyTo?.id) { text = ""; image = null; replyTo = null }
                    })
                error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = AccentLight, fontSize = 12.sp) }
            }
        }
    }
    removing?.let { id -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("حذف التعليق؟") }, text = { Text("غيتحيّد من الخادم ومن عند كاع المستخدمين.") },
        confirmButton = { TextButton(onClick = { removing = null; delete(id) }, enabled = !busy, modifier = Modifier.testTag("comment-delete-confirm")) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("إلغاء", color = Muted) } }) }
}

@Composable private fun CommentCard(comment: Comment, me: Person, busy: Boolean, nested: Boolean, canDelete: Boolean, onImage: (String) -> Unit, onLike: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth().testTag("comment-${comment.id}")) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Avatar(comment.author, if (nested) 28 else 34)
                Column(Modifier.weight(1f)) {
                    PersonName(comment.author, size = if (nested) 13 else 14)
                    Text(relativePostDate(comment.date), color = Muted, fontSize = 10.5.sp)
                }
                if (canDelete) Box(Modifier.size(26.dp).clip(CircleShape).background(Raised).clickable(enabled = !busy, onClick = onDelete)
                    .testTag("comment-delete-${comment.id}"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, "حذف التعليق", tint = Muted, modifier = Modifier.size(14.dp))
                }
            }
            if (comment.text.isNotBlank()) Text(comment.text, color = TextMain, fontSize = 13.sp, lineHeight = 21.sp)
            if (comment.image != null) NabdImage(comment.image, "صورة التعليق", Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(RoundedCornerShape(14.dp)).background(Raised)
                .clickable { onImage(comment.image!!) }, ContentScale.Crop)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(999.dp)).clickable(enabled = !busy, onClick = onLike)
                    .testTag("comment-like-${comment.id}").padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Favorite, null, tint = if (comment.likedByMe) AccentLight else Muted, modifier = Modifier.size(17.dp))
                    Text("نبضة · ${comment.likes}", color = if (comment.likedByMe) AccentLight else Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                }
                Row(Modifier.clip(RoundedCornerShape(999.dp)).clickable(enabled = !busy, onClick = onReply)
                    .testTag("comment-reply-${comment.id}").padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = Muted, modifier = Modifier.size(17.dp))
                    Text("رد", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** Composer pinned above the keyboard: my avatar, rounded field, image attach and a send that only works with real content. */
@Composable private fun CommentComposer(text: String, change: (String) -> Unit, me: Person, busy: Boolean, image: String?,
    attach: (String) -> Unit, clearImage: () -> Unit, send: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var preparing by remember { mutableStateOf(false) }; var problem by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            preparing = true; problem = null
            try { val value = inlineImage(context, uri); attach(value.first) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { problem = "تعذّر تجهيز الصورة ضمن الحد المسموح (128KB)." }
            finally { preparing = false }
        }
    }
    Column(Modifier.fillMaxWidth().border(1.dp, Hairline, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (image != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NabdImage(image, "الصورة المرفقة", Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), ContentScale.Crop)
            TextButton(onClick = clearImage, enabled = !busy) { Text("إزالة الصورة") }
        }
        problem?.let { Text(it, color = AccentLight, fontSize = 11.5.sp) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(me, 34)
            TextField(value = text, onValueChange = change, placeholder = { Text("اكتب تعليقاً...", color = Muted, fontSize = 13.sp) }, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp).testTag("comment-input"), maxLines = 4,
                shape = RoundedCornerShape(999.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Raised, unfocusedContainerColor = Raised,
                    disabledContainerColor = Raised, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
            Box(Modifier.size(42.dp).clip(CircleShape).background(Raised).clickable(enabled = !busy && !preparing) { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AddPhotoAlternate, "إرفاق صورة", tint = Muted, modifier = Modifier.size(20.dp).testTag("comment-attach"))
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(if (text.isNotBlank() || image != null) Accent else Outline.copy(alpha = .6f))
                .clickable(enabled = !busy && (text.isNotBlank() || image != null), onClick = send), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Send, "إرسال التعليق", tint = Color.White, modifier = Modifier.size(20.dp).testTag("comment-send"))
            }
        }
    }
}
