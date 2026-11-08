package app.nabd.nativeapp.ui

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

internal data class Destination(val id: String, val title: String, val icon: @Composable (Boolean) -> Unit)
private fun dockIcon(image: ImageVector, title: String): @Composable (Boolean) -> Unit = { selected ->
    Icon(image, title, tint = if (selected) Accent else Muted, modifier = Modifier.size(20.dp))
}
internal val destinations = listOf(
    Destination("home", "الرئيسية", dockIcon(Icons.Outlined.Home, "الرئيسية")),
    Destination("friends", "الأصدقاء", dockIcon(Icons.Outlined.People, "الأصدقاء")),
    Destination("menu", "القائمة", dockIcon(Icons.Outlined.GridView, "القائمة")),
    Destination("reels", "ريلزات", { selected -> ReelsGlyph(selected, Modifier.size(width = 26.dp, height = 18.dp)) }),
    Destination("me", "حسابي", dockIcon(Icons.Outlined.Person, "حسابي")))

private val ecgPoints = listOf(0f to 12f, 10f to 12f, 14f to 4f, 18f to 20f, 22f to 2f, 26f to 22f, 30f to 9f, 33f to 15f, 36f to 12f, 60f to 12f,
    70f to 12f, 74f to 4f, 78f to 20f, 82f to 2f, 86f to 22f, 90f to 9f, 93f to 15f, 96f to 12f, 120f to 12f)

private fun DrawPathPoints(width: Float, height: Float): Path = Path().also { path ->
    ecgPoints.forEachIndexed { index, (x, y) ->
        val px = x * width / 120f
        val py = y * height / 24f
        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
}

/** Moving ECG wave drawn as a vector path, blue, next to the word نبض only. */
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

/** Floating glass capsule: the wordmark in a gradient. */
@Composable internal fun HomeHeader(busy: Boolean, error: String?, searching: Boolean, search: String,
    onSearch: (String) -> Unit, toggleSearch: () -> Unit, compose: () -> Unit, chats: () -> Unit, refresh: () -> Unit,
    alerts: Int = 0, onAlerts: () -> Unit = {}) {
    Column(Modifier.statusBarsPadding()) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()
            .nabdShadow(CircleShape, elevation = 16.dp).clip(CircleShape).background(CardFill).border(1.dp, GlassEdge, CircleShape)) {
            Box(Modifier.matchParentSize().glassBlur(24.dp).background(GlassSheen))
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradientText("نبض", size = 23.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassCircle(Icons.Outlined.Add, "إنشاء منشور", compose, filled = true, enabled = !busy)
                        GlassCircle(Icons.Outlined.Search, "بحث", toggleSearch, enabled = true)
                        GlassCircle(Icons.Outlined.ChatBubbleOutline, "الرسائل", chats, enabled = true)
                        HeaderBell(alerts, true, onAlerts)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = Accent, trackColor = Color.Transparent)
            }
        }
        if (searching) NabdField(search, onSearch, "ابحث عن منشور أو @صديق…", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("home-search"), tag = "home-search")
        if (error != null) Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(CircleShape).background(AccentLight.copy(alpha = .1f)).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
            TextButton(onClick = refresh, enabled = !busy) { Text("إعادة المحاولة", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable private fun HeaderBell(count: Int, enabled: Boolean, click: () -> Unit) {
    Box(Modifier.testTag("header-bell")) {
        GlassCircle(Icons.Outlined.Notifications, "التنبيهات", click, enabled = enabled)
        if (count > 0) Box(Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(AccentLight).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (count > 9) "9+" else count.toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("bell-badge"))
        }
    }
}

/** Floating glass dock; the active tab stretches a soft blue pill behind it. */
@Composable internal fun HomeDock(tab: String, choose: (String) -> Unit) {
    Box(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
        .nabdShadow(CircleShape, elevation = 16.dp).clip(CircleShape).background(CardFill).border(1.dp, GlassEdge, CircleShape).testTag("home-dock")) {
        Box(Modifier.matchParentSize().glassBlur(24.dp).background(GlassSheen))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            destinations.forEach { dest ->
                val selected = tab == dest.id
                val press = rememberPress()
                Row(Modifier.clip(CircleShape)
                    .background(if (selected) Accent.copy(alpha = .14f) else Color.Transparent)
                    .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    .pressClick(press = press, onClick = { choose(dest.id) })
                    .padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    dest.icon(selected)
                    if (selected) Text(dest.title, color = Accent, fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FeedContent(snapshot: Snapshot, stories: List<LocalStory>, busy: Boolean, filter: String, search: String, modifier: Modifier = Modifier,
    onFilter: (String) -> Unit, onCompose: () -> Unit, onRefresh: () -> Unit, onNewStory: () -> Unit, onStory: (StoryGroup) -> Unit,
    onLike: (FeedPost) -> Unit, onComments: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit, onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit,
    onProfile: (Person) -> Unit = {}, onPin: (FeedPost, Boolean) -> Unit = { _, _ -> }, onBoost: (FeedPost) -> Unit = {}, onImage: (String) -> Unit = {},
    announcement: app.nabd.nativeapp.data.Announcement? = null, onDismissAnnouncement: (String) -> Unit = {}, seen: Set<String> = emptySet(),
    refreshing: Boolean = false, polls: Map<String, app.nabd.nativeapp.data.Poll> = emptyMap(), onVote: (FeedPost, String) -> Unit = { _, _ -> },
    onRetryPost: (FeedPost) -> Unit = {}) {
    val posts = remember(snapshot.posts, filter, search) { snapshot.posts.filter { p ->
        (filter != "friends" || p.author.friend) && (filter != "image" || p.image != null) && (search.isBlank() || p.text.contains(search, true) || p.author.name.contains(search, true))
    } }
    val rings = remember(snapshot.stories) { snapshot.stories.associateBy { it.owner.id } }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize(), indicator = { EcgIndicator(refreshing) }) {
        LazyColumn(Modifier.fillMaxSize().testTag("native-feed"), contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("stories") {
            StoriesTray(snapshot.stories, snapshot.me, seen, busy, onCreate = onNewStory, onOpen = onStory)
        }
        item("composer") {
            GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(snapshot.me, 44)
                    Box(Modifier.weight(1f).clip(CircleShape).background(Raised)
                        .pressClick(enabled = !busy, onClick = onCompose).padding(horizontal = 16.dp, vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                        Text("فيمَ تفكر؟", color = Muted, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GlassCircle(Icons.Outlined.AddPhotoAlternate, "نشر صورة", onCompose, enabled = !busy, size = 42.dp, tint = TextMain)
                }
            }
        }
        item("filters") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "لك", "friends" to "الأصدقاء", "image" to "الصور").forEach { (key, label) ->
                        FilterPill(label, key == filter) { onFilter(key) }
                    }
                }
                GlassCircle(Icons.Outlined.Refresh, "تحديث المنشورات", onRefresh, enabled = !busy, size = 38.dp, tint = Muted)
            }
        }
        if (posts.isEmpty()) item { EmptyState(Icons.Outlined.DynamicFeed, "ما كايناش منشورات هنا بعد", "شارك أول لحظة وبداها مع الناس اللي كيعنيو ليك.", modifier = Modifier.padding(horizontal = 12.dp)) {
            PillButton("شارك أول لحظة", onCompose, enabled = !busy, height = 44.dp, fontSize = 13)
        } }
        if (announcement != null) item(key = "announcement-${announcement.id}") {
            GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconPlate(Icons.Outlined.Campaign, size = 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text("إعلان رسمي", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(announcement.text, color = TextMain, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    GlassCircle(Icons.Outlined.Close, "إخفاء الإعلان", { onDismissAnnouncement(announcement.id) }, size = 30.dp, tint = Muted)
                }
            }
        }
        items(posts, key = { it.id }, contentType = { "post" }) { post ->
            val canPin = post.author.id == snapshot.me.id || app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username)
            PostCard(post, post.author.id == snapshot.me.id, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post, it) }, { onShare(post) }, { onProfile(post.author) },
                canPin, { onPin(post, it) }, post.author.id == snapshot.me.id, { onBoost(post) }, rings.containsKey(post.author.id), { rings[post.author.id]?.let(onStory) },
                onImage = { onImage(post.image!!) }, poll = polls[post.id], onVote = { onVote(post, it) }, onRetry = { onRetryPost(post) })
        }
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

/** Wall-clock time of a message, always readable and never a raw ISO string. */
internal fun clockTime(date: String): String = runCatching {
    val at = java.time.ZonedDateTime.parse(date).withZoneSameInstant(java.time.ZoneId.systemDefault())
    "%02d:%02d".format(at.hour, at.minute)
}.getOrDefault("")

/** The card of one post: glass 24dp, 46dp avatar, real counts, three equal actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PostCard(post: FeedPost, mine: Boolean, busy: Boolean, like: () -> Unit, comments: () -> Unit, delete: () -> Unit, react: (String?) -> Unit, share: () -> Unit,
    onAuthor: () -> Unit = {}, canPin: Boolean = false, onPin: (Boolean) -> Unit = {}, canBoost: Boolean = false, onBoost: () -> Unit = {},
    storyRing: Boolean = false, onAvatar: (() -> Unit)? = null, onImage: (String) -> Unit = {},
    poll: app.nabd.nativeapp.data.Poll? = null, onVote: (String) -> Unit = {}, onReactors: () -> Unit = {}, onRetry: () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val reel = remember(post.text) { findReel(post.text) }
    var reelPlaying by remember(post.id) { mutableStateOf(false) }
    var reelFull by remember(post.id) { mutableStateOf(false) }
    // A reel that scrolls out of the feed is disposed, so its player stops with it.
    // The effect is keyed on the post only: pressing play must not dispose the player.
    val playingHere = rememberUpdatedState(reelPlaying)
    DisposableEffect(post.id) { onDispose { if (playingHere.value) reelPlaying = false } }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("post-${post.id}")) {
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f).pressClick { onAuthor() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StoryAvatar(post.author, 46, storyRing, app.nabd.nativeapp.data.isAdminHandle(post.author.username), if (storyRing) onAvatar else null)
                        Column(Modifier.weight(1f)) {
                            PersonName(post.author, size = 15, weight = FontWeight.Black)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(relativePostDate(post.date), color = Muted, fontSize = 11.5.sp)
                                Icon(if (post.privacy == "friends") Icons.Outlined.Group else Icons.Outlined.Public,
                                    if (post.privacy == "friends") "للأصدقاء فقط" else "منشور عام", tint = Muted, modifier = Modifier.size(13.dp))
                                if (post.pinned) Text("مثبت", color = Accent, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                                if (post.pending) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Outlined.Schedule, "جارٍ النشر", tint = Muted, modifier = Modifier.size(12.dp))
                                    Text(if (post.failed) "تعذّر النشر" else "جارٍ النشر", color = if (post.failed) AccentLight else Muted, fontSize = 10.5.sp)
                                }
                                if (post.boosted) Text("معزَّز", color = AccentLight, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Box {
                        GlassCircle(Icons.Outlined.MoreHoriz, "خيارات المنشور", { menu = true }, size = 34.dp, tint = Muted)
                        PostMenu(menu, { menu = false }, mine, canPin, post.pinned, canBoost,
                            onPin = { onPin(it) }, onBoost = onBoost, onDelete = { confirm = true }, onShare = share,
                            onCopy = { clipboard.setText(AnnotatedString("nabd://post/${post.id}")); Toast.makeText(context, "تم نسخ رابط المنشور", Toast.LENGTH_SHORT).show() })
                    }
                }
                val body = if (reel != null) withoutReel(post.text, reel) else post.text
                if (body.isNotBlank()) Text(body, Modifier.fillMaxWidth().padding(top = 12.dp), color = TextMain, fontSize = 14.5.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                if (reel != null) Box(Modifier.padding(top = 12.dp).fillMaxWidth(0.72f)) {
                    ReelCard(reel, Modifier.fillMaxWidth(), playing = reelPlaying, onPlay = { reelPlaying = true }, onStop = { reelPlaying = false }, onExpand = { reelFull = true })
                }
                if (post.failed) PillButton("إعادة المحاولة", onRetry, modifier = Modifier.padding(top = 10.dp), brush = null, height = 38.dp, fontSize = 12, icon = Icons.Outlined.Refresh)
                if (post.image != null) NabdImage(post.image, "صورة المنشور", Modifier.fillMaxWidth().padding(top = 12.dp).height(180.dp)
                    .clip(MediaShape).background(Raised).pressClick { onImage(post.image!!) }, ContentScale.Crop)
                if (post.image == null && poll == null) PostLinkPreview(text = body)
                if (poll != null) Box(Modifier.padding(top = 12.dp)) { PollCard(poll, post.myReaction?.takeIf { it in poll.options.map { o -> o.id } }, busy, onVote) }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.pressClick(onClick = onReactors)) { ReactionSummary(post.breakdown, post.likes) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.pressClick { comments() }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("${post.comments.size}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("تعليق", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val reacted = post.myReaction != null
                    PostAction(selected = reacted, enabled = true, gradient = reacted, modifier = Modifier.weight(1f),
                        click = like, longClick = { dock = true }, content = {
                            ReactionGlyph(post.myReaction ?: "like", size = 22.dp)
                            Text(reactionLabel(post.myReaction), color = if (reacted) Color.White else Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        })
                    PostAction(selected = false, enabled = true, gradient = false, modifier = Modifier.weight(1f), click = comments, content = {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
                        Text("تعليق", color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    })
                    PostAction(selected = false, enabled = !busy, gradient = false, modifier = Modifier.weight(1f), click = share, content = {
                        Icon(Icons.Outlined.Share, null, tint = Muted, modifier = Modifier.size(20.dp))
                        Text("مشاركة", color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    })
                }
            }
        }
        ReactionDock(expanded = dock, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 76.dp)) { kind ->
            dock = false
            react(kind)
        }
    }
    if (reelFull && reel != null) ReelViewer(reel) { reelFull = false }
    if (confirm) GlassConfirm("حذف المنشور؟", "المنشور غيتحيّد من عند كاع المستخدمين.", "حذف",
        onConfirm = { confirm = false; delete() }, close = { confirm = false }, busy = busy, danger = true)
}

/** The ⋯ menu of a post: only real actions that exist for this account. */
@Composable private fun PostMenu(expanded: Boolean, close: () -> Unit, mine: Boolean, canPin: Boolean, pinned: Boolean, canBoost: Boolean,
    onPin: (Boolean) -> Unit, onBoost: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = close,
        modifier = Modifier.background(CardFill, RoundedCornerShape(20.dp)).border(1.dp, GlassEdge, RoundedCornerShape(20.dp))) {
        DropdownMenuItem(text = { Text("نسخ رابط المنشور", fontWeight = FontWeight.Bold) }, onClick = { close(); onCopy() }, leadingIcon = { Icon(Icons.Outlined.Link, null, tint = Accent) })
        DropdownMenuItem(text = { Text("مشاركة", fontWeight = FontWeight.Bold) }, onClick = { close(); onShare() }, leadingIcon = { Icon(Icons.Outlined.Share, null, tint = Accent) })
        if (canBoost) DropdownMenuItem(text = { Text("تعزيز المنشور", fontWeight = FontWeight.Bold) }, onClick = { close(); onBoost() }, leadingIcon = { Icon(Icons.Outlined.RocketLaunch, null, tint = AccentLight) })
        if (canPin) DropdownMenuItem(text = { Text(if (pinned) "إلغاء التثبيت" else "تثبيت في الأعلى", fontWeight = FontWeight.Bold) }, onClick = { close(); onPin(!pinned) },
            leadingIcon = { Icon(Icons.Outlined.PushPin, null, tint = Accent) })
        if (mine) DropdownMenuItem(text = { Text("حذف المنشور", fontWeight = FontWeight.Bold, color = AccentLight) }, onClick = { close(); onDelete() },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = AccentLight) })
    }
}

/**
 * One of the three equal action buttons of a post. The default plate is the
 * soft grey of the design system; an active نبضة fills with the red gradient.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PostAction(selected: Boolean, enabled: Boolean, gradient: Boolean, modifier: Modifier,
    click: () -> Unit, longClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val press = rememberPress()
    val pressed by press.source.collectIsPressedAsState()
    val view = LocalView.current
    Row(modifier.clip(RoundedCornerShape(16.dp))
        .then(if (gradient) Modifier.background(NabdRed) else Modifier.background(if (pressed) androidx.compose.ui.graphics.lerp(Raised, Accent, .10f) else Raised))
        .then(Modifier.pressScale(press).combinedClickable(interactionSource = press.source, indication = null, enabled = enabled, onClick = {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            click()
        }, onLongClick = longClick))
        .padding(horizontal = 8.dp, vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { content(this) }
    }
}

/** Custom pull-to-refresh indicator: a blue ECG wave instead of the default circle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BoxScope.EcgIndicator(refreshing: Boolean) {
    val transition = rememberInfiniteTransition(label = "ecg-pull")
    val sweep by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (refreshing) 900 else 2200, easing = LinearEasing), RepeatMode.Restart), label = "ecg-sweep")
    Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(width = 52.dp, height = 30.dp), contentAlignment = Alignment.Center) {
        val fraction = if (refreshing) sweep else 1f
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.height / 2f
            val path = Path().apply {
                val at = { f: Float -> size.width * f }
                moveTo(at(0f), mid)
                lineTo(at(.26f), mid)
                lineTo(at(.36f), mid)
                lineTo(at(.42f), size.height * .08f)
                lineTo(at(.50f), size.height * .92f)
                lineTo(at(.56f), mid)
                lineTo(at(.64f), mid)
                lineTo(at(.70f), size.height * .34f)
                lineTo(at(.76f), mid)
                lineTo(at(.86f), mid)
                lineTo(at(1f), mid)
            }
            clipRect(right = size.width * fraction) {
                drawPath(path, color = Accent, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
