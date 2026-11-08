package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.Poll
import app.nabd.nativeapp.data.Story
import app.nabd.nativeapp.data.StoryHighlight
import app.nabd.nativeapp.data.findReel
import app.nabd.nativeapp.data.isAdminHandle
import kotlin.math.ceil

private val AdminRing = Color(0xFFE11D48)

/**
 * A profile: a blurred cover, the avatar hanging over it with the ring of its
 * rank, the counters the vault really holds and three tabs — posts as a grid,
 * the reels the account published, and a short biography. Every number, every
 * cover and every name comes from the server; nothing here is invented.
 */
@Composable internal fun ProfileScreen(person: Person, posts: List<FeedPost>, followers: Int, following: Int, mine: Boolean, busy: Boolean, modifier: Modifier = Modifier,
    canPin: Boolean = false, onBack: (() -> Unit)? = null, onEdit: () -> Unit, onCompose: () -> Unit, onChat: () -> Unit, onFollow: () -> Unit, onCredentials: () -> Unit,
    onProfile: (Person) -> Unit, onComments: (FeedPost) -> Unit, onLike: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit,
    onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit, onPin: (FeedPost, Boolean) -> Unit = { _, _ -> }, onBoost: (FeedPost) -> Unit = {}, onInvite: () -> Unit = {},
    dark: Boolean = false, onDark: (Boolean) -> Unit = {}, onSettings: () -> Unit = {}, stories: List<Story> = emptyList(),
    polls: Map<String, Poll> = emptyMap(), onVote: (FeedPost, String) -> Unit = { _, _ -> },
    highlights: List<StoryHighlight> = emptyList(), onHighlight: (StoryHighlight) -> Unit = {}, onCreateHighlight: (String, List<String>) -> Unit = { _, _ -> },
    onRemoveHighlight: (String) -> Unit = {}, onFollowers: () -> Unit = {}, onFollowing: () -> Unit = {}, onReactors: (FeedPost) -> Unit = {},
    onImage: (String) -> Unit = {}, onRetryPost: (FeedPost) -> Unit = {}) {
    var tab by remember(person.id) { mutableStateOf("posts") }
    var playing by remember(person.id) { mutableStateOf<String?>(null) }
    val reels = remember(posts) { posts.mapNotNull { post -> findReel(post.text)?.let { post to it } } }
    LazyColumn(modifier.fillMaxSize().testTag("profile-screen"), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Box(Modifier.padding(horizontal = 12.dp)) { Cover(person, onBack) } }
        item { Box(Modifier.padding(horizontal = 12.dp)) { Identity(person) } }
        item { Box(Modifier.padding(horizontal = 12.dp)) { Counters(posts.size, followers, following, onFollowers, onFollowing) } }
        item { Box(Modifier.padding(horizontal = 12.dp)) { Actions(mine, person.following, busy, onEdit, onCompose, onFollow, onChat) } }
        if (highlights.isNotEmpty() || mine) item { Box(Modifier.padding(top = 2.dp)) {
            HighlightsCard(highlights, mine, busy, stories, onOpen = onHighlight, onCreate = onCreateHighlight, onRemove = onRemoveHighlight)
        } }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("المنشورات", tab == "posts", Modifier.weight(1f)) { tab = "posts" }
                FilterPill("الريلزات", tab == "reels", Modifier.weight(1f)) { tab = "reels" }
                FilterPill("نبذة", tab == "bio", Modifier.weight(1f)) { tab = "bio" }
            }
        }
        when (tab) {
            "posts" -> {
                if (posts.isEmpty()) item { EmptyState(Icons.Outlined.PhotoLibrary, if (mine) "ما نشرتي والو بعد" else "ما نشر والو بعد",
                    "أول منشور غيبان هنا مباشرة بعد النشر.", modifier = Modifier.padding(horizontal = 12.dp)) }
                else {
                    val rows = ceil(posts.size / 3.0).toInt()
                    items(rows) { index ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            (0..2).forEach { column ->
                                val post = posts.getOrNull(index * 3 + column)
                                if (post == null) Spacer(Modifier.weight(1f))
                                else Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Raised)
                                    .pressClick { onComments(post) }.testTag("profile-grid-${post.id}")) {
                                    if (post.image != null) NabdImage(post.image, "منشور", Modifier.matchParentSize(), ContentScale.Crop)
                                    else Box(Modifier.matchParentSize().padding(10.dp), contentAlignment = Alignment.Center) {
                                        Text(post.text.ifBlank { "منشور" }, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 4,
                                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    }
                                    if (post.image != null && post.text.isNotBlank()) Box(Modifier.matchParentSize().background(Color(0x330A1628)))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
            "reels" -> {
                if (reels.isEmpty()) item { EmptyState(Icons.Outlined.PhotoLibrary, "ما كايناش ريلزات", "الريلزات اللي نشرها هاد الحساب غيبانو هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
                else {
                    val rows = ceil(reels.size / 2.0).toInt()
                    items(rows) { index ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0..1).forEach { column ->
                                val entry = reels.getOrNull(index * 2 + column)
                                if (entry == null) Spacer(Modifier.weight(1f))
                                else Box(Modifier.weight(1f)) {
                                    ReelCard(entry.second, Modifier.fillMaxWidth(), playing = playing == entry.first.id,
                                        onPlay = { playing = entry.first.id }, onStop = { if (playing == entry.first.id) playing = null }, onExpand = { onComments(entry.first) })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
            else -> item { Box(Modifier.padding(horizontal = 12.dp)) { Biography(person, posts, stories, followers, mine, busy, dark, onDark, onSettings, onCredentials, onInvite) } }
        }
        if (tab != "bio") items(count = posts.size, key = { index -> posts[index].id }) { index ->
            val post = posts[index]
            PostCard(post, post.author.id == person.id && mine, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post, it) }, { onShare(post) }, { onProfile(post.author) }, canPin, { onPin(post, it) }, post.author.id == person.id && mine, { onBoost(post) },
            poll = polls[post.id], onVote = { onVote(post, it) }, onReactors = { onReactors(post) }, onRetry = { onRetryPost(post) }) }
    }
}

/** The cover: the account picture, blurred and dimmed, or the blue gradient. */
@Composable private fun Cover(person: Person, onBack: (() -> Unit)?) {
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.fillMaxWidth().height(150.dp).nabdShadow(shape, elevation = 10.dp).clip(shape).background(CardFill).border(1.dp, GlassEdge, shape)) {
        if (person.avatar != null) NabdImage(person.avatar, "غلاف الملف", Modifier.matchParentSize().glassBlur(26.dp), ContentScale.Crop)
        else Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xFF0866FF), Color(0xFF00A3FF), Color(0xFF7DD3FC)))))
        Box(Modifier.matchParentSize().glassBlur(18.dp).background(GlassSheen))
        Box(Modifier.matchParentSize().background(Accent.copy(alpha = if (person.avatar != null) .28f else .10f)))
        if (onBack != null) Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            GlassCircle(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "رجوع", onBack, size = 36.dp)
        }
    }
}

/** The identity block: the ringed avatar hanging over the cover, name, seal, bio. */
@Composable private fun Identity(person: Person) {
    Column(Modifier.fillMaxWidth().offset(y = (-46).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val ring = if (isAdminHandle(person.username)) Brush.horizontalGradient(listOf(AdminRing, AdminRing))
            else if (person.verified) NabdBlue else Brush.horizontalGradient(listOf(Outline, Outline))
        Box(Modifier.size(106.dp).border(4.dp, ring, CircleShape).padding(4.dp).background(CardFill, CircleShape), contentAlignment = Alignment.Center) {
            Avatar(person, 94)
        }
        Spacer(Modifier.height(8.dp))
        PersonName(person, size = 21)
        Text("@${person.username}", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (person.bio.isNotBlank()) Text(person.bio, Modifier.padding(top = 8.dp), color = TextMain, fontSize = 13.5.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
    }
}

/** The three real counters. Each one opens the list of accounts behind it. */
@Composable private fun Counters(posts: Int, followers: Int, following: Int, onFollowers: () -> Unit, onFollowing: () -> Unit) {
    GlassCard {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Counter("المنشورات", posts, Modifier.weight(1f), null)
            VerticalDivider()
            Counter("المتابعون", followers, Modifier.weight(1f), onFollowers)
            VerticalDivider()
            Counter("المتابَعون", following, Modifier.weight(1f), onFollowing)
        }
    }
}

@Composable private fun Counter(label: String, value: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    Column(modifier.then(if (onClick != null) Modifier.pressClick(onClick = onClick) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
        RollingNumber(value, 19)
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Follow and message for others; edit and compose for my own account. */
@Composable private fun Actions(mine: Boolean, following: Boolean, busy: Boolean, onEdit: () -> Unit, onCompose: () -> Unit, onFollow: () -> Unit, onChat: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mine) {
            PillButton("تعديل الملف الشخصي", onEdit, modifier = Modifier.weight(1f), enabled = !busy, height = 46.dp, fontSize = 14, icon = Icons.Outlined.Settings)
            PillButton("منشور جديد", onCompose, modifier = Modifier.weight(1f), enabled = !busy, brush = null, height = 46.dp, fontSize = 14)
        } else {
            PillButton(if (following) "إلغاء المتابعة" else "متابعة", onFollow, modifier = Modifier.weight(1f), enabled = !busy,
                brush = if (following) null else NabdBlue, icon = Icons.Outlined.PersonAdd)
            PillButton("إرسال رسالة", onChat, modifier = Modifier.weight(1f), enabled = !busy, brush = null, height = 46.dp, fontSize = 14, icon = Icons.Outlined.ChatBubbleOutline)
        }
    }
}

/** The biography tab: badges, the real points, and my own account switches. */
@Composable private fun Biography(person: Person, posts: List<FeedPost>, stories: List<Story>, followers: Int, mine: Boolean, busy: Boolean,
    dark: Boolean, onDark: (Boolean) -> Unit, onSettings: () -> Unit, onCredentials: () -> Unit, onInvite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BadgeCard(remember(person, posts, stories, followers) { earnedBadges(person, posts, stories, followers) })
        if (mine) GlassCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("نقاطي", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("نقاط حقيقية من النشر والدعوة وتسجيل الدخول اليومي.", color = Muted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    RollingNumber(person.points, 28, Accent)
                }
                PillButton("مشاركة رابط الدعوة", onInvite, modifier = Modifier.fillMaxWidth(), enabled = person.inviteCode.isNotBlank(),
                    height = 46.dp, fontSize = 14, icon = Icons.Outlined.Share, testTag = "invite-share")
            }
        }
        GlassCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نبذة", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(person.bio.ifBlank { "ما كتب والو فالنبذة." }, color = if (person.bio.isBlank()) Muted else TextMain, fontSize = 13.5.sp, lineHeight = 22.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconPlate(Icons.Outlined.Settings, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${posts.size} منشور · ${stories.size} قصة حيّة", color = Muted, fontSize = 12.sp)
                        Text(if (person.joined.isBlank()) "تاريخ الانضمام غير معروف" else "منضم منذ ${person.joined.take(10)}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        if (mine) GlassCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconPlate(Icons.Outlined.Lock, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("إعدادات الحساب", color = TextMain, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text("تغيير البريد وكلمة السر", color = Muted, fontSize = 12.sp)
                    }
                    PillButton("تعديل", onCredentials, enabled = !busy, brush = null, height = 40.dp, fontSize = 13)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconPlate(Icons.Outlined.ModeNight, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("الوضع الليلي", color = TextMain, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text("الوضع الليلي كيتحفظ فحسابك", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = dark, onCheckedChange = onDark,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = Color.White, uncheckedTrackColor = Outline))
                }
                Row(Modifier.fillMaxWidth().pressClick(onClick = onSettings).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconPlate(Icons.Outlined.Settings, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text("الإعدادات والخصوصية", color = TextMain, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text("البحث، الأجهزة، الخصوصية والتنبيهات", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
