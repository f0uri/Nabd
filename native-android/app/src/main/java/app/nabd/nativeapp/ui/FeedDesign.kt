package app.nabd.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.StrokeCap
import androidx.compose.ui.graphics.drawscope.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.*
import java.time.Duration
import java.time.Instant

internal data class Destination(val id: String, val title: String, val icon: ImageVector)
internal val destinations = listOf(Destination("home", "الرئيسية", Icons.Outlined.Home), Destination("reels", "ريلز", Icons.Outlined.PlayCircle), Destination("friends", "أصدقاء", Icons.Outlined.People), Destination("menu", "القائمة", Icons.Outlined.GridView), Destination("alerts", "تنبيهات", Icons.Outlined.Notifications), Destination("me", "حسابي", Icons.Outlined.Person))

/** Animated نبض wordmark with a pulse line, inspired by the NabdHome reference layout. */
@Composable internal fun PulseLogo() {
    val transition = rememberInfiniteTransition(label = "nabd-pulse")
    val sweep by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)), label = "nabd-sweep")
    val points = listOf(0f to 14f, 12f to 14f, 18f to 14f, 22f to 3f, 27f to 25f, 31f to 8f, 34f to 14f, 44f to 14f, 47f to 10f, 50f to 14f, 58f to 14f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("نبض", color = TextMain, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
        Canvas(Modifier.width(44.dp).height(28.dp)) {
            val path = Path()
            points.forEachIndexed { index, (x, y) ->
                val px = x * size.width / 60f
                val py = y * size.height / 28f
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path, color = Accent.copy(alpha = .22f), style = stroke)
            val window = size.width * .45f
            val left = -window + sweep * (size.width + window)
            clipRect(left = left, right = left + window) { drawPath(path, color = Accent, style = stroke) }
        }
    }
}

@Composable internal fun HomeHeader(busy: Boolean, error: String?, searching: Boolean, search: String,
    onSearch: (String) -> Unit, toggleSearch: () -> Unit, compose: () -> Unit, chats: () -> Unit, refresh: () -> Unit, menu: () -> Unit = {}) {
    Column(Modifier.background(Canvas).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderCircle(Icons.Outlined.Menu, "القائمة", menu)
                PulseLogo()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = compose, enabled = !busy, shape = CircleShape, modifier = Modifier.size(42.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, contentColor = Color.White)) { Icon(Icons.Outlined.Add, "إنشاء منشور", Modifier.size(22.dp)) }
                HeaderCircle(Icons.Outlined.Search, "بحث", toggleSearch)
                HeaderCircle(Icons.Outlined.ChatBubbleOutline, "الرسائل", chats)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Canvas)
        if (searching) OutlinedTextField(search, onSearch, placeholder = { Text("ابحث في نبض") }, singleLine = true, shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) })
        if (error != null) Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Accent.copy(alpha = .1f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            TextButton(onClick = refresh, enabled = !busy) { Text("إعادة المحاولة") }
        }
    }
}
@Composable private fun HeaderCircle(icon: ImageVector, label: String, click: () -> Unit) {
    IconButton(onClick = click, modifier = Modifier.size(42.dp).clip(CircleShape).background(Raised)) { Icon(icon, label, Modifier.size(21.dp), tint = TextMain) }
}

@Composable internal fun HomeDock(tab: String, choose: (String) -> Unit) {
    Surface(color = Color(0xEF18181E), shape = RoundedCornerShape(35.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .1f)), shadowElevation = 12.dp,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().height(70.dp).testTag("home-dock")) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            destinations.forEach { dest ->
                val selected = tab == dest.id
                Column(Modifier.weight(if (selected) 1.35f else 1f).clip(RoundedCornerShape(29.dp))
                    .background(if (selected) Raised else Color.Transparent)
                    .clickable { choose(dest.id) }.fillMaxHeight().padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(dest.icon, dest.title, tint = if (selected) TextMain else Muted, modifier = Modifier.size(22.dp))
                    Text(dest.title, color = if (selected) TextMain else Muted, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable internal fun Avatar(person: Person, size: Int = 42) {
    val colors = listOf(Color(0xFF3D304F), Color(0xFF24473F), Color(0xFF2C3C56), Color(0xFF542C39))
    val color = colors[(person.id.hashCode() and Int.MAX_VALUE) % colors.size]
    NabdImage(person.avatar, "صورة ${person.name}", Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color, color.copy(alpha = .55f)))).border(1.dp, Color.White.copy(alpha = .08f), CircleShape), ContentScale.Crop) {
        Text(person.name.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "ن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * .43f).sp, lineHeight = (size * .5f).sp)
    }
}

private val storyTints = listOf(Color(0xFF2A3B4C), Color(0xFF3A2C4A), Color(0xFF24473F), Color(0xFF4A2C33))

@Composable private fun StoryCard(color: Color, click: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(width = 106.dp, height = 172.dp).clip(RoundedCornerShape(16.dp)).background(color)
        .border(1.dp, Outline.copy(alpha = .8f), RoundedCornerShape(16.dp)).clickable(onClick = click), content = content)
}

@Composable internal fun FeedContent(snapshot: Snapshot, stories: List<LocalStory>, busy: Boolean, filter: String, search: String, modifier: Modifier = Modifier,
    onFilter: (String) -> Unit, onCompose: () -> Unit, onRefresh: () -> Unit, onNewStory: () -> Unit, onStory: (String) -> Unit,
    onLike: (FeedPost) -> Unit, onComments: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit, onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit) {
    val posts = remember(snapshot.posts, filter, search) { snapshot.posts.filter { p ->
        (filter != "friends" || p.author.friend) && (filter != "image" || p.image != null) && (search.isBlank() || p.text.contains(search, true) || p.author.name.contains(search, true))
    } }
    LazyColumn(modifier.fillMaxSize().testTag("native-feed"), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("compose") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(22.dp)).background(Surface).border(1.dp, Outline.copy(alpha = .5f), RoundedCornerShape(22.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(snapshot.me, 44)
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(21.dp)).background(Raised).clickable(enabled = !busy, onClick = onCompose).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text("فيم تفكر؟", color = Muted, fontSize = 14.sp)
                }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Accent.copy(alpha = .16f)).clickable(enabled = !busy, onClick = onCompose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AddPhotoAlternate, "نشر صورة", tint = Accent, modifier = Modifier.size(21.dp))
                }
            }
        }
        item("stories") {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item("new-story") {
                    StoryCard(color = Raised, click = { if (!busy) onNewStory() }) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "إنشاء ستوري", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.height(6.dp))
                            Text("أنشئ ستوري", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(stories, key = { it.id }) { story ->
                    val tint = storyTints[(story.id.hashCode() and Int.MAX_VALUE) % storyTints.size]
                    StoryCard(color = tint, click = { onStory(story.id) }) {
                        if (story.image != null) NabdImage(story.image, "صورة قصة محلية", Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).border(3.dp, Accent, CircleShape), contentAlignment = Alignment.Center) {
                            Text(story.text.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "ق", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(story.text.take(18).ifBlank { "قصتي" }, Modifier.align(Alignment.BottomStart).padding(10.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item("filters") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "لك", "friends" to "الأصدقاء", "image" to "الصور").forEach { (key, label) ->
                        val selected = key == filter
                        Surface(color = if (selected) TextMain else Raised, shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { onFilter(key) }) {
                            Text(label, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontSize = 13.sp, color = if (selected) Canvas else TextMain, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Refresh, "تحديث المنشورات", tint = Muted, modifier = Modifier.size(20.dp)) }
            }
        }
        if (posts.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.DynamicFeed, null, tint = Muted, modifier = Modifier.size(30.dp))
            Text("ما كايناش منشورات هنا بعد", color = Muted, fontSize = 14.sp)
            TextButton(onClick = onCompose, enabled = !busy) { Text("شارك أول لحظة") }
        } }
        items(posts, key = { it.id }, contentType = { "post" }) { post -> PostCard(post, post.author.id == snapshot.me.id, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post, it) }, { onShare(post) }) }
    }
}

internal fun relativePostDate(date: String): String = runCatching {
    val elapsed = Duration.between(Instant.parse(date), Instant.now()).seconds.coerceAtLeast(0)
    when { elapsed < 60 -> "الآن"; elapsed < 3600 -> "منذ ${elapsed / 60} د"; elapsed < 86400 -> "منذ ${elapsed / 3600} س"; elapsed < 604800 -> "منذ ${elapsed / 86400} أيام"; else -> date.take(10) }
}.getOrDefault(date.take(10))

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PostCard(post: FeedPost, mine: Boolean, busy: Boolean, like: () -> Unit, comments: () -> Unit, delete: () -> Unit, react: (String?) -> Unit, share: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }; var reactions by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    Surface(color = Surface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Outline.copy(alpha = .65f)), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).testTag("post-${post.id}")) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.author, 44)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(post.author.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(relativePostDate(post.date), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                        Text("·", color = Muted, fontSize = 12.sp)
                        Icon(Icons.Outlined.Public, "منشور عام", tint = Muted, modifier = Modifier.size(12.dp))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreHoriz, "خيارات المنشور", tint = Muted, modifier = Modifier.size(21.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("مشاركة المنشور") }, onClick = { menu = false; share() }, leadingIcon = { Icon(Icons.Outlined.Share, null) })
                        DropdownMenuItem(text = { Text("التفاعلات") }, onClick = { menu = false; reactions = true }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null) }, enabled = !busy)
                        if (mine) DropdownMenuItem(text = { Text("حذف المنشور", color = AccentLight) }, onClick = { menu = false; confirm = true }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = AccentLight) }, enabled = !busy)
                    }
                }
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 17.sp, lineHeight = 28.sp)
            if (post.image != null) NabdImage(post.image, "صورة المنشور", Modifier.fillMaxWidth().aspectRatio(post.ratio.coerceIn(.5f, 2f)).background(Raised), ContentScale.Crop)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (post.likes > 0) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Favorite, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (post.likes == 0) "كن أول من يتفاعل" else "${post.likes} تفاعل", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${post.comments.size} تعليق", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = comments))
            }
            HorizontalDivider(color = Outline.copy(alpha = .7f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                PostAction(reactionLabels[post.myReaction] ?: "أعجبني", Icons.Outlined.ThumbUp, post.myReaction != null, !busy, Modifier.weight(1f), like, { reactions = true })
                PostAction("تعليق", Icons.Outlined.ChatBubbleOutline, false, true, Modifier.weight(1f), comments)
                PostAction("مشاركة", Icons.Outlined.Share, false, !busy, Modifier.weight(1f), share)
            }
            HorizontalDivider(color = Outline.copy(alpha = .7f))
            Row(Modifier.fillMaxWidth().clickable(onClick = comments).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Raised), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(18.dp)) }
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(Raised).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    Text("اكتب تعليقاً...", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
    if (reactions) AlertDialog(onDismissRequest = { reactions = false }, title = { Text("اختار تفاعلك") }, text = { ReactionPicker(post.myReaction, busy) { react(it); reactions = false } }, confirmButton = { TextButton(onClick = { reactions = false }) { Text("إغلاق") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("حذف المنشور؟") }, text = { Text("لن نؤكد الحذف حتى يتأكد الحفظ في الخدمة.") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }, enabled = !busy) { Text("حذف") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PostAction(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, modifier: Modifier, click: () -> Unit, longClick: (() -> Unit)? = null) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(enabled = enabled, onClick = click, onLongClick = longClick).heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) Accent else Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Accent else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
