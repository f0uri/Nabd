package app.nabd.nativeapp.ui

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.*
import java.time.Duration
import java.time.Instant

internal data class Destination(val id: String, val title: String, val icon: ImageVector)
internal val destinations = listOf(Destination("home", "الرئيسية", Icons.Outlined.Home), Destination("reels", "ريلز", Icons.Outlined.Movie), Destination("friends", "الأصدقاء", Icons.Outlined.People), Destination("menu", "القائمة", Icons.Outlined.GridView), Destination("me", "حسابي", Icons.Outlined.Person))

/** Vector reaction set. No emoji glyphs anywhere in the interface. */
internal val reactionIcons = mapOf(
    "love" to Icons.Outlined.Favorite,
    "like" to Icons.Outlined.ThumbUp,
    "haha" to Icons.Outlined.Mood,
    "wow" to Icons.Outlined.AutoAwesome,
    "sad" to Icons.Outlined.MoodBad,
    "angry" to Icons.Outlined.LocalFireDepartment)

private val ecgPoints = listOf(0f to 12f, 10f to 12f, 14f to 4f, 18f to 20f, 22f to 2f, 26f to 22f, 30f to 9f, 33f to 15f, 36f to 12f, 60f to 12f,
    70f to 12f, 74f to 4f, 78f to 20f, 82f to 2f, 86f to 22f, 90f to 9f, 93f to 15f, 96f to 12f, 120f to 12f)

private fun DrawPathPoints(width: Float, height: Float): Path = Path().also { path ->
    ecgPoints.forEachIndexed { index, (x, y) ->
        val px = x * width / 120f
        val py = y * height / 24f
        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
}

/** Moving ECG wave drawn as a vector path, blue, next to the word نبض. */
@Composable internal fun EcgWave(color: Color = Accent, width: Dp = 46.dp, height: Dp = 20.dp) {
    val transition = rememberInfiniteTransition(label = "nabd-ecg")
    val sweep by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "nabd-ecg-sweep")
    Canvas(Modifier.width(width).height(height)) {
        val path = DrawPathPoints(size.width, size.height)
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, color = color.copy(alpha = .28f), style = stroke)
        val window = size.width * .45f
        val left = -window + sweep * (size.width + window)
        clipRect(left = left, right = left + window) { drawPath(path, color = color, style = stroke) }
    }
}

/** The beating dot that follows the wave. */
@Composable internal fun PulseDot() {
    val transition = rememberInfiniteTransition(label = "nabd-beat")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)), label = "nabd-beat-value")
    val beat = (kotlin.math.sin(phase * 2.0 * Math.PI).toFloat() + 1f) / 2f
    Box(Modifier.size(6.dp).graphicsLayer { scaleX = 1f + .45f * beat; scaleY = 1f + .45f * beat }
        .shadow(8.dp, CircleShape, spotColor = AccentLight, ambientColor = AccentLight)
        .clip(CircleShape).background(AccentLight))
}

@Composable internal fun HomeHeader(busy: Boolean, error: String?, searching: Boolean, search: String,
    onSearch: (String) -> Unit, toggleSearch: () -> Unit, compose: () -> Unit, chats: () -> Unit, refresh: () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 10.dp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("نبض", color = Accent, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                        EcgWave()
                        PulseDot()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HeaderCircle(Icons.Outlined.Add, "إنشاء منشور", true, !busy, compose)
                        HeaderCircle(Icons.Outlined.Search, "بحث", false, true, toggleSearch)
                        HeaderCircle(Icons.Outlined.ChatBubbleOutline, "الرسائل", false, true, chats)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent)
            }
        }
        if (searching) OutlinedTextField(search, onSearch, placeholder = { Text("ابحث عن منشور أو @صديق...") }, singleLine = true, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("home-search"), leadingIcon = { Icon(Icons.Outlined.Search, null) })
        if (error != null) Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(AccentLight.copy(alpha = .1f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = AccentLight, fontSize = 12.sp)
            TextButton(onClick = refresh, enabled = !busy) { Text("إعادة المحاولة") }
        }
    }
}
@Composable private fun HeaderCircle(icon: ImageVector, label: String, filled: Boolean, enabled: Boolean, click: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(if (filled) Accent else Raised)
        .clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (filled) Color.White else TextMain, modifier = Modifier.size(18.dp))
    }
}

@Composable internal fun HomeDock(tab: String, choose: (String) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(999.dp), shadowElevation = 10.dp,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().testTag("home-dock")) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            destinations.forEach { dest ->
                val selected = tab == dest.id
                Row(Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Accent.copy(alpha = .14f) else Color.Transparent)
                    .clickable { choose(dest.id) }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(dest.icon, dest.title, tint = if (selected) Accent else Muted, modifier = Modifier.size(19.dp))
                    Text(dest.title, color = if (selected) Accent else Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable internal fun Avatar(person: Person, size: Int = 40) {
    val colors = listOf(Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF38BDF8))), Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFFFB7185))),
        Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF7DD3FC))), Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF60A5FA))))
    val color = colors[(person.id.hashCode() and Int.MAX_VALUE) % colors.size]
    NabdImage(person.avatar, "صورة ${person.name}", Modifier.size(size.dp).clip(CircleShape).background(color).border(2.dp, Color.White, CircleShape), ContentScale.Crop) {
        Text(person.name.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "ن", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size * .38f).sp, lineHeight = (size * .45f).sp)
    }
}

private val storyGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF38BDF8))),
    Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFFFB7185))),
    Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF7DD3FC))),
    Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF60A5FA))))

@Composable private fun StoryCard(brush: Brush, click: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(width = 84.dp, height = 130.dp).clip(RoundedCornerShape(22.dp)).background(brush)
        .border(1.5.dp, Color.White, RoundedCornerShape(22.dp)).clickable(onClick = click).padding(8.dp), content = content)
}

@Composable internal fun FeedContent(snapshot: Snapshot, stories: List<LocalStory>, busy: Boolean, filter: String, search: String, modifier: Modifier = Modifier,
    onFilter: (String) -> Unit, onCompose: () -> Unit, onRefresh: () -> Unit, onNewStory: () -> Unit, onStory: (String) -> Unit,
    onLike: (FeedPost) -> Unit, onComments: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit, onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit,
    onProfile: (Person) -> Unit = {}, onPin: (FeedPost, Boolean) -> Unit = { _, _ -> }, onBoost: (FeedPost) -> Unit = {},
    announcement: app.nabd.nativeapp.data.Announcement? = null, onDismissAnnouncement: (String) -> Unit = {}) {
    val posts = remember(snapshot.posts, filter, search) { snapshot.posts.filter { p ->
        (filter != "friends" || p.author.friend) && (filter != "image" || p.image != null) && (search.isBlank() || p.text.contains(search, true) || p.author.name.contains(search, true))
    } }
    LazyColumn(modifier.fillMaxSize().testTag("native-feed"), contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("composer") {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(snapshot.me, 40)
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Raised)
                        .clickable(enabled = !busy, onClick = onCompose).padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                        Text("فيمَ تفكر؟", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Raised).clickable(enabled = !busy, onClick = onCompose), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AddPhotoAlternate, "نشر صورة", tint = TextMain, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        item("stories") {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item("new-story") {
                    StoryCard(brush = storyGradients[0], click = { if (!busy) onNewStory() }) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Add, "إنشاء ستوري", tint = Accent, modifier = Modifier.size(18.dp))
                            }
                            Text("أنشئ ستوري", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                items(stories, key = { it.id }) { story ->
                    StoryCard(brush = storyGradients[(story.id.hashCode() and Int.MAX_VALUE) % storyGradients.size], click = { onStory(story.id) }) {
                        if (story.image != null) NabdImage(story.image, "صورة قصة محلية", Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopStart).size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Text(story.text.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "ق", color = Accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        Text(story.text.take(16).ifBlank { "قصتي" }, Modifier.align(Alignment.BottomStart), color = Color.White, fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item("filters") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "لك", "friends" to "الأصدقاء", "image" to "الصور").forEach { (key, label) ->
                        val selected = key == filter
                        Row(Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Accent.copy(alpha = .14f) else Color.White)
                            .clickable { onFilter(key) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = if (selected) Accent else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Refresh, "تحديث المنشورات", tint = Muted, modifier = Modifier.size(19.dp)) }
            }
        }
        if (posts.isEmpty()) item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.DynamicFeed, null, tint = Muted, modifier = Modifier.size(30.dp))
                Text("ما كايناش منشورات هنا بعد", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCompose, enabled = !busy) { Text("شارك أول لحظة") }
            }
        } }
        if (announcement != null) item(key = "announcement-${announcement.id}") {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Campaign, "إعلان رسمي", tint = Accent, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("إعلان رسمي", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(announcement.text, color = TextMain, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Raised).clickable { onDismissAnnouncement(announcement.id) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "إخفاء الإعلان", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        items(posts, key = { it.id }, contentType = { "post" }) { post ->
            val canPin = post.author.id == snapshot.me.id || app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username)
            PostCard(post, post.author.id == snapshot.me.id, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post, it) }, { onShare(post) }, { onProfile(post.author) }, canPin, { onPin(post, it) }, post.author.id == snapshot.me.id, { onBoost(post) })
        }
    }
}

internal fun relativePostDate(date: String): String = runCatching {
    val elapsed = Duration.between(Instant.parse(date), Instant.now()).seconds.coerceAtLeast(0)
    when {
        elapsed < 60 -> "الآن"
        elapsed < 3600 -> { val minutes = elapsed / 60; if (minutes == 1L) "منذ دقيقة واحدة" else if (minutes == 2L) "منذ دقيقتين" else "منذ $minutes دقائق" }
        elapsed < 86400 -> { val hours = elapsed / 3600; if (hours == 1L) "منذ ساعة واحدة" else if (hours == 2L) "منذ ساعتين" else "منذ $hours ساعات" }
        elapsed < 604800 -> { val days = elapsed / 86400; if (days == 1L) "منذ يوم واحد" else if (days == 2L) "منذ يومين" else "منذ $days أيام" }
        else -> date.take(10)
    }
}.getOrDefault(date.take(10))

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PostCard(post: FeedPost, mine: Boolean, busy: Boolean, like: () -> Unit, comments: () -> Unit, delete: () -> Unit, react: (String?) -> Unit, share: () -> Unit,
    onAuthor: () -> Unit = {}, canPin: Boolean = false, onPin: (Boolean) -> Unit = {}, canBoost: Boolean = false, onBoost: () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("post-${post.id}")) {
        Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f).clickable(onClick = onAuthor), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(post.author, 40)
                        Column {
                            PersonName(post.author, size = 14)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(relativePostDate(post.date), color = Muted, fontSize = 11.sp)
                                Icon(Icons.Outlined.Public, "منشور عام", tint = Muted, modifier = Modifier.size(12.dp))
                                if (post.pinned) Text("مثبت", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                if (post.boosted) Text("معزَّز", color = AccentLight, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (canBoost) Box(Modifier.size(30.dp).clip(CircleShape).background(if (post.boosted) AccentLight.copy(alpha = .16f) else Raised)
                            .clickable(enabled = !busy) { onBoost() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.RocketLaunch, "تعزيز المنشور", tint = if (post.boosted) AccentLight else Muted, modifier = Modifier.size(16.dp))
                        }
                        if (canPin) Box(Modifier.size(30.dp).clip(CircleShape).background(if (post.pinned) Accent.copy(alpha = .14f) else Raised)
                            .clickable(enabled = !busy) { onPin(!post.pinned) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PushPin, if (post.pinned) "إلغاء التثبيت" else "تثبيت المنشور في الأعلى",
                                tint = if (post.pinned) Accent else Muted, modifier = Modifier.size(16.dp))
                        }
                        if (mine) Box(Modifier.size(30.dp).clip(CircleShape).background(Raised).clickable(enabled = !busy) { confirm = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "حذف المنشور", tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (post.text.isNotBlank()) Text(post.text, Modifier.fillMaxWidth().padding(top = 10.dp), color = TextMain, fontSize = 14.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                if (post.image != null) NabdImage(post.image, "صورة المنشور", Modifier.fillMaxWidth().padding(top = 10.dp).height(150.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Raised), ContentScale.Crop)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${post.likes} تفاعل", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${post.comments.size} تعليق", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = comments))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val reacted = post.myReaction != null
                    ActionButton(icon = { PulseHeartIcon(reacted) }, label = reactionLabels[post.myReaction] ?: "نبضة", selected = reacted, enabled = !busy,
                        modifier = Modifier.weight(1f), click = like, longClick = { dock = true })
                    ActionButton(icon = { ActionIcon(Icons.Outlined.ChatBubbleOutline, false) }, label = "تعليق", selected = false, enabled = true, modifier = Modifier.weight(1f), click = comments)
                    ActionButton(icon = { ActionIcon(Icons.Outlined.Share, false) }, label = "مشاركة", selected = false, enabled = !busy, modifier = Modifier.weight(1f),
                        click = { clipboard.setText(AnnotatedString("nabd://post/${post.id}")); Toast.makeText(context, "تم نسخ رابط المنشور", Toast.LENGTH_SHORT).show() },
                        longClick = share)
                }
            }
        }
        ReactionDock(expanded = dock, modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 58.dp)) { kind ->
            dock = false
            react(kind)
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("حذف المنشور؟") }, text = { Text("لن نؤكد الحذف حتى يتأكد الحفظ في الخدمة.") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }, enabled = !busy) { Text("حذف") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}

/** Heart with a pulse line drawn inside it; the only "نبضة" affordance, no thumbs-up. */
@Composable private fun PulseHeartIcon(selected: Boolean, iconSize: Dp = 22.dp) {
    Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Favorite, null, tint = if (selected) AccentLight else Muted, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize(.58f)) {
            val path = DrawPathPoints(size.width, size.height)
            drawPath(path, color = Color.White, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
@Composable private fun ActionIcon(icon: ImageVector, selected: Boolean) {
    Icon(icon, null, tint = if (selected) AccentLight else Muted, modifier = Modifier.size(20.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ActionButton(icon: @Composable () -> Unit, label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, click: () -> Unit, longClick: (() -> Unit)? = null) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) AccentLight.copy(alpha = .13f) else Raised)
        .combinedClickable(enabled = enabled, onClick = click, onLongClick = longClick)
        .padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) AccentLight else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** Floating reaction bar with six vector reaction icons, opened on a long press. */
@Composable private fun ReactionDock(expanded: Boolean, modifier: Modifier = Modifier, pick: (String) -> Unit) {
    if (!expanded) return
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(targetValue = if (shown) 1f else .4f, animationSpec = tween(220), label = "dock-scale")
    val alpha by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(220), label = "dock-alpha")
    Surface(color = Color.White, shape = RoundedCornerShape(999.dp), shadowElevation = 10.dp,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            dockReactions.forEach { kind ->
                val icon = reactionIcons[kind] ?: Icons.Outlined.Favorite
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { pick(kind) }.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, reactionLabels[kind] ?: kind, tint = Accent, modifier = Modifier.size(22.dp).testTag("dock-$kind"))
                    Text(reactionLabels[kind] ?: kind, fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextMain)
                }
            }
        }
    }
}
