package app.nabd.nativeapp.ui

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

@Composable internal fun HomeHeader(busy: Boolean, error: String?, searching: Boolean, search: String,
    onSearch: (String) -> Unit, toggleSearch: () -> Unit, compose: () -> Unit, chats: () -> Unit, refresh: () -> Unit) {
    Column(Modifier.background(Canvas).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha=.14f)), contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.MonitorHeart, null, tint=Accent, modifier=Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f).padding(start=10.dp)) {
                Text("نبض", fontSize=28.sp, lineHeight=31.sp, fontWeight=FontWeight.Bold)
                Text("لحظات تجمعنا", fontSize=11.sp, color=Muted, lineHeight=16.sp)
            }
            HeaderAction(Icons.Outlined.Search, "بحث", toggleSearch)
            Spacer(Modifier.width(7.dp))
            HeaderAction(Icons.Outlined.ChatBubbleOutline, "الرسائل", chats)
            Spacer(Modifier.width(7.dp))
            FilledIconButton(onClick=compose, enabled=!busy, shape=RoundedCornerShape(14.dp), modifier=Modifier.size(42.dp), colors=IconButtonDefaults.filledIconButtonColors(containerColor=Accent, contentColor=Color.White)) { Icon(Icons.Outlined.Add, "إنشاء منشور", Modifier.size(23.dp)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color=Accent, trackColor=Canvas)
        if (searching) OutlinedTextField(search, onSearch, placeholder={ Text("ابحث في نبض") }, singleLine=true, shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=8.dp), leadingIcon={ Icon(Icons.Outlined.Search, null) })
        if (error != null) Row(Modifier.padding(horizontal=16.dp, vertical=6.dp).clip(RoundedCornerShape(14.dp)).background(Accent.copy(alpha=.1f)).padding(start=12.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color=MaterialTheme.colorScheme.error, fontSize=12.sp)
            TextButton(onClick=refresh, enabled=!busy) { Text("إعادة المحاولة") }
        }
    }
}
@Composable private fun HeaderAction(icon: ImageVector, label: String, click: () -> Unit) {
    IconButton(onClick=click, modifier=Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Surface)) { Icon(icon, label, Modifier.size(21.dp), tint=TextMain) }
}
@Composable internal fun HomeDock(tab: String, choose: (String) -> Unit) {
    Surface(color=Surface, shape=RoundedCornerShape(25.dp), border=BorderStroke(1.dp, Outline.copy(alpha=.7f)), shadowElevation=12.dp,
        modifier=Modifier.navigationBarsPadding().padding(horizontal=16.dp, vertical=8.dp).fillMaxWidth().testTag("home-dock")) {
        Row(Modifier.padding(horizontal=5.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
            destinations.forEach { dest ->
                val selected = tab == dest.id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (selected) Accent.copy(alpha=.13f) else Color.Transparent)
                    .clickable { choose(dest.id) }.heightIn(min=54.dp).padding(vertical=7.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Icon(dest.icon, dest.title, tint=if(selected) AccentLight else Muted, modifier=Modifier.size(22.dp))
                    Text(dest.title, color=if(selected) TextMain else Muted, fontSize=10.sp, lineHeight=13.sp, fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal, maxLines=1)
                }
            }
        }
    }
}
@Composable internal fun Avatar(person: Person, size: Int = 42) {
    val colors = listOf(Color(0xFF3D304F), Color(0xFF24473F), Color(0xFF2C3C56), Color(0xFF542C39))
    val color = colors[(person.id.hashCode() and Int.MAX_VALUE) % colors.size]
    NabdImage(person.avatar, "صورة ${person.name}", Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color, color.copy(alpha=.55f)))).border(1.dp, Color.White.copy(alpha=.08f), CircleShape), ContentScale.Crop) {
        Text(person.name.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "ن", color=Color.White, fontWeight=FontWeight.Bold, fontSize=(size*.43f).sp, lineHeight=(size*.5f).sp)
    }
}

@Composable internal fun FeedContent(snapshot: Snapshot, stories: List<LocalStory>, busy: Boolean, filter: String, search: String, modifier: Modifier=Modifier,
    onFilter: (String) -> Unit, onCompose: () -> Unit, onRefresh: () -> Unit, onNewStory: () -> Unit, onStory: (String) -> Unit,
    onLike: (FeedPost) -> Unit, onComments: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit, onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit) {
    val posts = remember(snapshot.posts, filter, search) { snapshot.posts.filter { p ->
        (filter != "friends" || p.author.friend) && (filter != "image" || p.image != null) && (search.isBlank() || p.text.contains(search, true) || p.author.name.contains(search, true))
    } }
    LazyColumn(modifier.fillMaxSize().testTag("native-feed"), contentPadding=PaddingValues(top=8.dp, bottom=16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("compose") { Surface(color=Surface, shape=RoundedCornerShape(22.dp), border=BorderStroke(1.dp, Outline.copy(alpha=.6f)), modifier=Modifier.padding(horizontal=16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Avatar(snapshot.me, 40)
                Column(Modifier.weight(1f).clickable(enabled=!busy, onClick=onCompose).padding(vertical=4.dp)) {
                    Text("شنو جديدك اليوم؟", fontSize=14.sp, color=TextMain, fontWeight=FontWeight.Bold)
                    Text("شارك لحظة مع ناسك", fontSize=11.sp, lineHeight=18.sp, color=Muted)
                }
                IconButton(onClick=onCompose, enabled=!busy, modifier=Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha=.1f))) { Icon(Icons.Outlined.AddPhotoAlternate, "نشر صورة", tint=AccentLight, modifier=Modifier.size(21.dp)) }
            }
        } }
        item("stories") { LazyRow(contentPadding=PaddingValues(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
            item { Surface(color=Surface, shape=RoundedCornerShape(16.dp), border=BorderStroke(1.dp, Outline), modifier=Modifier.clickable(enabled=!busy, onClick=onNewStory)) {
                Row(Modifier.padding(horizontal=12.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, null, tint=AccentLight, modifier=Modifier.size(20.dp))
                    Column { Text("قصتي", fontSize=12.sp, fontWeight=FontWeight.Bold); Text("محلية · على جهازك", color=Muted, fontSize=9.sp, lineHeight=13.sp) }
                }
            } }
            items(stories) { story -> Surface(color=Surface, shape=RoundedCornerShape(16.dp), border=BorderStroke(1.dp, Outline), modifier=Modifier.clickable { onStory(story.id) }) {
                Row(Modifier.padding(10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if (story.image != null) NabdImage(story.image, "صورة قصة محلية", Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)), ContentScale.Crop)
                    Text(story.text.take(15).ifBlank { "قصة بصورة" }, fontSize=12.sp, maxLines=1)
                }
            } }
        } }
        item("filters") { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("all" to "لك", "friends" to "الأصدقاء", "image" to "الصور").forEach { (key,label) ->
                    val selected = key == filter
                    Surface(color=if(selected) Accent else Surface, shape=RoundedCornerShape(12.dp), modifier=Modifier.clickable { onFilter(key) }) {
                        Text(label, Modifier.padding(horizontal=16.dp, vertical=10.dp), fontSize=12.sp, color=if(selected) Color.White else Muted, fontWeight=FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick=onRefresh, enabled=!busy, modifier=Modifier.size(40.dp)) { Icon(Icons.Outlined.Refresh, "تحديث المنشورات", tint=Muted, modifier=Modifier.size(20.dp)) }
        } }
        if (posts.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.DynamicFeed, null, tint=Muted, modifier=Modifier.size(30.dp))
            Text("ما كايناش منشورات هنا بعد", color=Muted, fontSize=14.sp)
            TextButton(onClick=onCompose, enabled=!busy) { Text("شارك أول لحظة") }
        } }
        items(posts, key={ it.id }, contentType={ "post" }) { post -> PostCard(post, post.author.id == snapshot.me.id, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post,it) }, { onShare(post) }) }
    }
}

internal fun relativePostDate(date: String): String = runCatching {
    val elapsed = Duration.between(Instant.parse(date), Instant.now()).seconds.coerceAtLeast(0)
    when { elapsed < 60 -> "الآن"; elapsed < 3600 -> "منذ ${elapsed/60} د"; elapsed < 86400 -> "منذ ${elapsed/3600} س"; elapsed < 604800 -> "منذ ${elapsed/86400} أيام"; else -> date.take(10) }
}.getOrDefault(date.take(10))

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PostCard(post: FeedPost, mine: Boolean, busy: Boolean, like: () -> Unit, comments: () -> Unit, delete: () -> Unit, react: (String?) -> Unit, share: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }; var reactions by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    Surface(color=Surface, shape=RoundedCornerShape(24.dp), border=BorderStroke(1.dp, Outline.copy(alpha=.65f)), modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("post-${post.id}")) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=14.dp, end=6.dp, top=14.dp, bottom=8.dp), verticalAlignment=Alignment.CenterVertically) {
                Avatar(post.author, 42)
                Column(Modifier.weight(1f).padding(horizontal=10.dp), verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(post.author.name, fontWeight=FontWeight.Bold, fontSize=14.sp, lineHeight=20.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(relativePostDate(post.date), color=Muted, fontSize=10.sp, lineHeight=15.sp)
                        Text("·", color=Muted); Icon(Icons.Outlined.Public, "منشور عام", tint=Muted, modifier=Modifier.size(11.dp))
                    }
                }
                Box {
                    IconButton(onClick={ menu=true }, modifier=Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreHoriz, "خيارات المنشور", tint=Muted, modifier=Modifier.size(21.dp)) }
                    DropdownMenu(expanded=menu, onDismissRequest={ menu=false }) {
                        DropdownMenuItem(text={ Text("مشاركة المنشور") }, onClick={ menu=false; share() }, leadingIcon={ Icon(Icons.Outlined.Share,null) })
                        DropdownMenuItem(text={ Text("التفاعلات") }, onClick={ menu=false; reactions=true }, leadingIcon={ Icon(Icons.Outlined.FavoriteBorder,null) }, enabled=!busy)
                        if (mine) DropdownMenuItem(text={ Text("حذف المنشور", color=AccentLight) }, onClick={ menu=false; confirm=true }, leadingIcon={ Icon(Icons.Outlined.DeleteOutline,null,tint=AccentLight) }, enabled=!busy)
                    }
                }
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp), fontSize=15.sp, lineHeight=25.sp)
            if (post.image != null) NabdImage(post.image, "صورة المنشور", Modifier.fillMaxWidth().aspectRatio(post.ratio.coerceIn(.25f,4f)).background(Raised), ContentScale.Crop)
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                if (post.likes > 0) { Icon(Icons.Outlined.Favorite, null, tint=AccentLight, modifier=Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)) }
                Text(if(post.likes==0) "كن أول من يتفاعل" else "${post.likes} تفاعل", color=Muted, fontSize=11.sp, modifier=Modifier.weight(1f))
                Text("${post.comments.size} تعليق", color=Muted, fontSize=11.sp, modifier=Modifier.clickable(onClick=comments))
            }
            HorizontalDivider(Modifier.padding(horizontal=14.dp), color=Outline.copy(alpha=.7f))
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=4.dp)) {
                PostAction(reactionLabels[post.myReaction] ?: "إعجاب", Icons.Outlined.ThumbUp, post.myReaction!=null, !busy, Modifier.weight(1f), like, { reactions=true })
                PostAction("تعليق", Icons.Outlined.ChatBubbleOutline, false, true, Modifier.weight(1f), comments)
                PostAction("مشاركة", Icons.Outlined.Share, false, !busy, Modifier.weight(1f), share)
            }
        }
    }
    if (reactions) AlertDialog(onDismissRequest={ reactions=false }, title={ Text("اختار تفاعلك") }, text={ ReactionPicker(post.myReaction,busy) { react(it); reactions=false } }, confirmButton={ TextButton(onClick={ reactions=false }) { Text("إغلاق") } })
    if (confirm) AlertDialog(onDismissRequest={ confirm=false }, title={ Text("حذف المنشور؟") }, text={ Text("لن نؤكد الحذف حتى يتأكد الحفظ في الخدمة.") }, confirmButton={ TextButton(onClick={ confirm=false; delete() },enabled=!busy) { Text("حذف") } }, dismissButton={ TextButton(onClick={ confirm=false }) { Text("إلغاء") } })
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun PostAction(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, modifier: Modifier, click: () -> Unit, longClick: (() -> Unit)? = null) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(enabled=enabled,onClick=click,onLongClick=longClick).heightIn(min=44.dp).padding(horizontal=4.dp, vertical=8.dp), horizontalArrangement=Arrangement.Center, verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,label,tint=if(selected) AccentLight else Muted,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(label,color=if(selected) AccentLight else Muted,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
