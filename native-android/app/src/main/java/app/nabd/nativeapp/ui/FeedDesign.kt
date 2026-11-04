package app.nabd.nativeapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
internal val destinations = listOf(Destination("home", "الرئيسية", Icons.Outlined.Home), Destination("reels", "ريلز", Icons.Outlined.Movie), Destination("friends", "الأصدقاء", Icons.Outlined.People), Destination("menu", "القائمة", Icons.Outlined.GridView), Destination("me", "حسابي", Icons.Outlined.Person))

/** The single animated element kept from the reference: a beating dot before the word نبض. */
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
        Surface(color = Glass, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.5.dp, Color.White), shadowElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        PulseDot()
                        Text("نبض", color = Accent, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        HeaderCircle(Icons.Outlined.Add, "إنشاء منشور", true, !busy, compose)
                        HeaderCircle(Icons.Outlined.Search, "بحث", false, true, toggleSearch)
                        HeaderCircle(Icons.Outlined.ChatBubbleOutline, "الرسائل", false, true, chats)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent)
            }
        }
        if (searching) OutlinedTextField(search, onSearch, placeholder = { Text("ابحث عن منشور أو @صديق...") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).testTag("home-search"), leadingIcon = { Icon(Icons.Outlined.Search, null) })
        if (error != null) Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(AccentLight.copy(alpha = .1f)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = AccentLight, fontSize = 12.sp)
            TextButton(onClick = refresh, enabled = !busy) { Text("إعادة المحاولة") }
        }
    }
}
@Composable private fun HeaderCircle(icon: ImageVector, label: String, filled: Boolean, enabled: Boolean, click: () -> Unit) {
    val background = if (filled) Accent else Color(0xD1FFFFFF)
    val tint = if (filled) Color.White else TextMain
    Box(Modifier.size(33.dp).clip(CircleShape).background(background).border(1.dp, if (filled) Accent else Color.White, CircleShape)
        .clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable internal fun HomeDock(tab: String, choose: (String) -> Unit) {
    Surface(color = Color(0xD1FFFFFF), shape = RoundedCornerShape(999.dp), border = BorderStroke(1.5.dp, Color.White), shadowElevation = 12.dp,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().testTag("home-dock")) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            destinations.forEach { dest ->
                val selected = tab == dest.id
                Row(Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Accent.copy(alpha = .14f) else Color.Transparent)
                    .clickable { choose(dest.id) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(dest.icon, dest.title, tint = if (selected) Accent else Muted, modifier = Modifier.size(18.dp))
                    Text(dest.title, color = if (selected) Accent else Muted, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable internal fun Avatar(person: Person, size: Int = 38) {
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
    Box(Modifier.size(width = 84.dp, height = 130.dp).clip(RoundedCornerShape(18.dp)).background(brush)
        .border(1.5.dp, Color.White, RoundedCornerShape(18.dp)).clickable(onClick = click).padding(7.dp), content = content)
}

@Composable internal fun FeedContent(snapshot: Snapshot, stories: List<LocalStory>, busy: Boolean, filter: String, search: String, modifier: Modifier = Modifier,
    onFilter: (String) -> Unit, onCompose: () -> Unit, onRefresh: () -> Unit, onNewStory: () -> Unit, onStory: (String) -> Unit,
    onLike: (FeedPost) -> Unit, onComments: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit, onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit) {
    val posts = remember(snapshot.posts, filter, search) { snapshot.posts.filter { p ->
        (filter != "friends" || p.author.friend) && (filter != "image" || p.image != null) && (search.isBlank() || p.text.contains(search, true) || p.author.name.contains(search, true))
    } }
    LazyColumn(modifier.fillMaxSize().testTag("native-feed"), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("composer") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(20.dp)).background(Glass)
                .border(1.5.dp, Color.White, RoundedCornerShape(20.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(snapshot.me, 38)
                Box(Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Color(0xB3FFFFFF)).border(1.dp, Color.White, CircleShape)
                    .clickable(enabled = !busy, onClick = onCompose).padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.CenterStart) {
                    Text("فيمَ تفكر؟", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(33.dp).clip(CircleShape).background(Color(0xD1FFFFFF)).border(1.dp, Color.White, CircleShape)
                    .clickable(enabled = !busy, onClick = onCompose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AddPhotoAlternate, "نشر صورة", tint = TextMain, modifier = Modifier.size(16.dp))
                }
            }
        }
        item("stories") {
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        if (story.image != null) NabdImage(story.image, "صورة قصة محلية", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), ContentScale.Crop)
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "لك", "friends" to "الأصدقاء", "image" to "الصور").forEach { (key, label) ->
                        val selected = key == filter
                        Row(Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) Accent.copy(alpha = .14f) else Color(0xB3FFFFFF))
                            .border(1.dp, Color.White, CircleShape).clickable { onFilter(key) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = if (selected) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.size(33.dp)) { Icon(Icons.Outlined.Refresh, "تحديث المنشورات", tint = Muted, modifier = Modifier.size(17.dp)) }
            }
        }
        if (posts.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.DynamicFeed, null, tint = Muted, modifier = Modifier.size(30.dp))
            Text("ما كايناش منشورات هنا بعد", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    var confirm by remember { mutableStateOf(false) }
    var dock by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).testTag("post-${post.id}")) {
        Surface(color = Glass, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.5.dp, Color.White), shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(post.author, 38)
                        Column {
                            Text(post.author.name, color = TextMain, fontWeight = FontWeight.Black, fontSize = 13.5.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(relativePostDate(post.date), color = Color(0xFF64748B), fontSize = 10.5.sp)
                                Text("·", color = Color(0xFF64748B), fontSize = 10.5.sp)
                                Icon(Icons.Outlined.Public, "منشور عام", tint = Color(0xFF64748B), modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                    if (mine) Box(Modifier.size(27.dp).clip(CircleShape).background(Color(0xD1FFFFFF)).border(1.dp, Color.White, CircleShape)
                        .clickable(enabled = !busy) { confirm = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "حذف المنشور", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
                if (post.text.isNotBlank()) Text(post.text, Modifier.fillMaxWidth().padding(top = 8.dp), color = TextMain, fontSize = 13.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                if (post.image != null) NabdImage(post.image, "صورة المنشور", Modifier.fillMaxWidth().padding(top = 9.dp).height(135.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Raised), ContentScale.Crop)
                Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${reactionEmoji[post.myReaction] ?: "❤️"} ${post.likes} تفاعل نبض", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${post.comments.size} تعليق", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = comments))
                }
                HorizontalDivider(color = Hairline)
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val reacted = post.myReaction != null
                    ActionButton(reactionEmoji[post.myReaction] ?: "❤️", reactionLabels[post.myReaction] ?: "نبضة", reacted, !busy, Modifier.weight(1f), like, { dock = true })
                    ActionButton("💬", "تعليق", false, true, Modifier.weight(1f), comments)
                    ActionButton("↗️", "مشاركة", false, !busy, Modifier.weight(1f), share)
                }
            }
        }
        ReactionDock(expanded = dock, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 46.dp)) { kind ->
            dock = false
            flying = reactionEmoji[kind]
            react(kind)
        }
        flying?.let { emoji -> FlyingEmoji(emoji) { flying = null } }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("حذف المنشور؟") }, text = { Text("لن نؤكد الحذف حتى يتأكد الحفظ في الخدمة.") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }, enabled = !busy) { Text("حذف") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ActionButton(emoji: String, label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, click: () -> Unit, longClick: (() -> Unit)? = null) {
    val background = if (selected) AccentLight.copy(alpha = .13f) else Color(0xB8FFFFFF)
    val tint = if (selected) AccentLight else Muted
    Row(modifier.clip(RoundedCornerShape(13.dp)).border(1.dp, if (selected) AccentLight.copy(alpha = .3f) else Color.White, RoundedCornerShape(13.dp))
        .background(background).combinedClickable(enabled = enabled, onClick = click, onLongClick = longClick)
        .padding(horizontal = 6.dp, vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable private fun ReactionDock(expanded: Boolean, modifier: Modifier = Modifier, pick: (String) -> Unit) {
    if (!expanded) return
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(targetValue = if (shown) 1f else .4f, animationSpec = tween(250), label = "dock-scale")
    val alpha by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(250), label = "dock-alpha")
    Surface(color = Color(0xF2FFFFFF), shape = RoundedCornerShape(999.dp), border = BorderStroke(1.5.dp, Color.White), shadowElevation = 12.dp,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            dockReactions.forEach { kind ->
                Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { pick(kind) }.padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(reactionEmoji[kind] ?: "❤️", fontSize = 22.sp)
                    Text(reactionLabels[kind] ?: kind, fontSize = 8.5.sp, fontWeight = FontWeight.Black, color = TextMain)
                }
            }
        }
    }
}

@Composable private fun FlyingEmoji(emoji: String, done: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(emoji) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = LinearEasing)); done() }
    Text(emoji, Modifier.offset(y = (-110 * progress.value).dp).alpha(1f - progress.value).graphicsLayer { scaleX = .7f + .6f * progress.value; scaleY = scaleX }, fontSize = 24.sp)
}
