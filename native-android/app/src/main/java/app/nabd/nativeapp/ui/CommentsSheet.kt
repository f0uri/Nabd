package app.nabd.nativeapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Independent comments screen: a 92% glass sheet with its own post summary,
 * nested replies, a keyboard-aware composer and real counters. Nothing here is
 * mocked: every comment comes from the shared vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CommentsSheet(post: FeedPost, me: Person, busy: Boolean, error: String?, close: () -> Unit,
    send: (String, String?, String?, () -> Unit) -> Unit, like: (String) -> Unit, delete: (String) -> Unit, onImage: (String) -> Unit = {}, react: (String, String) -> Unit = { _, _ -> }) {
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var image by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var removing by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val roots = remember(post.comments) { post.comments.filter { it.replyTo == null }.asReversed() }
    val replies = remember(post.comments) { post.comments.filter { it.replyTo != null }.groupBy { it.replyTo } }
    ModalBottomSheet(onDismissRequest = close, sheetState = state, modifier = Modifier.fillMaxHeight(.92f),
        scrimColor = WindowDim, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = WindowFill,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Outline)) }) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().glassBlur(28.dp).background(Brush.linearGradient(listOf(Color(0x3BFFFFFF), Color(0x1AF1F5FF)))))
            Column(Modifier.fillMaxSize().testTag("comments-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("التعليقات", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("${post.comments.size}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("comments-count"))
                Spacer(Modifier.width(10.dp))
                GlassCircle(Icons.Outlined.Close, "إغلاق التعليقات", close, size = 34.dp)
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
                if (roots.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(76.dp).clip(CircleShape).background(Accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ModeComment, null, tint = Accent, modifier = Modifier.size(34.dp))
                    }
                    Text("كن أول من يعلّق", Modifier.padding(top = 14.dp), color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("شارك رأيك وبدا الحوار مع باقي الأعضاء.", Modifier.padding(top = 4.dp), color = Muted, fontSize = 12.5.sp, lineHeight = 19.sp)
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(roots, key = { it.id }) { root ->
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { shown = true }
                        val shift by animateFloatAsState(targetValue = if (shown) 0f else 70f, animationSpec = spring(dampingRatio = .8f, stiffness = 320f), label = "comment-shift")
                        val fade by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(260), label = "comment-fade")
                        Box(Modifier.graphicsLayer { translationY = shift; alpha = fade }) {
                            Column(Modifier.animateContentSize()) {
                                CommentCard(root, me, busy, nested = false, canDelete = root.author.id == me.id || post.author.id == me.id || app.nabd.nativeapp.data.isAdminHandle(me.username), onImage = onImage,
                                    onLike = { like(root.id) }, onReact = { kind -> react(root.id, kind) }, onReply = { replyTo = root }, onDelete = { removing = root.id })
                                val children = replies[root.id].orEmpty()
                                if (children.isNotEmpty()) {
                                    val open = root.id in expanded
                                    val press = rememberPress()
                                    Row(Modifier.padding(start = 42.dp, top = 6.dp).clip(CircleShape).background(Accent.copy(alpha = .10f))
                                        .pressClick(press = press, onClick = { expanded = if (open) expanded - root.id else expanded + root.id })
                                        .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Accent, modifier = Modifier.size(16.dp))
                                        Text(if (open) "إخفاء الردود" else "عرض الردود (${children.size})", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                            modifier = Modifier.testTag("toggle-replies-${root.id}"))
                                    }
                                    if (open) children.forEach { child ->
                                        Row(Modifier.padding(top = 6.dp)) {
                                            Spacer(Modifier.width(26.dp))
                                            Box(Modifier.weight(1f)) { CommentCard(child, me, busy, nested = true, canDelete = child.author.id == me.id || post.author.id == me.id || app.nabd.nativeapp.data.isAdminHandle(me.username), onImage = onImage,
                                            onLike = { like(child.id) }, onReact = { kind -> react(child.id, kind) }, onReply = { replyTo = root }, onDelete = { removing = child.id }) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(CardFill).imePadding().navigationBarsPadding()) {
                replyTo?.let { parent ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = Accent, modifier = Modifier.size(15.dp))
                        Text("الرد على ${parent.author.name}", Modifier.weight(1f).padding(horizontal = 6.dp), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Raised).pressClick { replyTo = null }, contentAlignment = Alignment.Center) {
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
    }
    if (removing != null) GlassConfirm("حذف التعليق؟", "التعليق غيتحيّد من عند كاع المستخدمين.", "حذف",
        onConfirm = { val id = removing!!; removing = null; delete(id) }, close = { removing = null }, busy = busy, danger = true, confirmTag = "comment-delete-confirm")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun CommentCard(comment: Comment, me: Person, busy: Boolean, nested: Boolean, canDelete: Boolean, onImage: (String) -> Unit, onLike: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    var dock by remember { mutableStateOf(false) }
    val press = rememberPress()
    Box(Modifier.fillMaxWidth()) {
        GlassCard(modifier = Modifier.combinedClickable(interactionSource = press.source, indication = null, onClick = { }, onLongClick = { dock = true }).then(Modifier.pressScale(press)).testTag("comment-${comment.id}")) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Avatar(comment.author, if (nested) 28 else 36)
                    Column(Modifier.weight(1f)) {
                        PersonName(comment.author, size = if (nested) 13 else 14)
                        Text(relativePostDate(comment.date), color = Muted, fontSize = 10.5.sp)
                    }
                    if (canDelete) Box(Modifier.size(28.dp).clip(CircleShape).background(Raised).pressClick(enabled = !busy, onClick = onDelete)
                        .testTag("comment-delete-${comment.id}"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.DeleteOutline, "حذف التعليق", tint = Muted, modifier = Modifier.size(15.dp))
                    }
                }
                if (comment.text.isNotBlank()) Text(comment.text, color = TextMain, fontSize = 13.5.sp, lineHeight = 22.sp)
                if (comment.image != null) NabdImage(comment.image, "صورة التعليق", Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(MediaShape).background(Raised)
                    .pressClick { onImage(comment.image!!) }, ContentScale.Crop)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(CircleShape).background(if (comment.myReaction != null) reactionColor(comment.myReaction).copy(alpha = .12f) else Raised)
                        .pressClick(enabled = true, onClick = onLike)
                        .testTag("comment-like-${comment.id}").padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReactionGlyph(comment.myReaction ?: "like", size = 18.dp)
                        Text("${reactionLabel(comment.myReaction)} · ${comment.likes}",
                            color = if (comment.myReaction != null) reactionColor(comment.myReaction) else Muted,
                            fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.clip(CircleShape).background(Raised).pressClick(enabled = true, onClick = onReply)
                        .testTag("comment-reply-${comment.id}").padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = Muted, modifier = Modifier.size(16.dp))
                        Text("رد", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        ReactionDock(expanded = dock, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp)) { kind -> dock = false; onReact(kind) }
    }
}

/** Composer pinned above the keyboard: my avatar, filled field, image attach and a gradient send. */
@Composable private fun CommentComposer(text: String, change: (String) -> Unit, me: Person, busy: Boolean, image: String?,
    attach: (String) -> Unit, clearImage: () -> Unit, send: () -> Unit) {
    var preparing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val draft = remember { PhotoDraft() }
    val launcher = rememberPhotoLauncher(draft)
    LaunchedEffect(draft.image) { val value = draft.image; if (value != null) { attach(value); draft.image = null } }
    LaunchedEffect(draft.error) { problem = draft.error }
    LaunchedEffect(draft.busy) { preparing = draft.busy }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (image != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NabdImage(image, "الصورة المرفقة", Modifier.size(58.dp).clip(MediaShape), ContentScale.Crop)
            PillButton("إزالة الصورة", clearImage, enabled = !busy, brush = null, height = 38.dp, fontSize = 12)
        }
        if (problem != null) Text(problem!!, color = AccentLight, fontSize = 11.5.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(me, 34)
            NabdField(text, change, "اكتب تعليقاً…", Modifier.weight(1f), enabled = !busy, singleLine = false, maxLines = 4, tag = "comment-input")
            GlassCircle(Icons.Outlined.AddPhotoAlternate, "إرفاق صورة", { launcher.launch("image/*") }, enabled = !busy && !preparing, size = 42.dp,
                tint = Muted, modifier = Modifier.testTag("comment-attach"))
            val ready = !busy && (text.isNotBlank() || image != null)
            val press = rememberPress()
            Box(Modifier.size(44.dp).clip(CircleShape).then(if (ready) Modifier.background(NabdBlue) else Modifier.background(Raised))
                .pressClick(press = press, enabled = ready, onClick = send).testTag("comment-send"), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Send, "إرسال التعليق", tint = if (ready) Color.White else Muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
