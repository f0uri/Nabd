package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person
import androidx.compose.ui.platform.testTag

/**
 * My account: a calm gradient cover, the avatar overlapping it with a white
 * ring, one glass statistics bar and the points card. Every number shown here
 * comes from the real vault snapshot; nothing is simulated or inflated.
 */
@Composable internal fun ProfileScreen(person: Person, posts: List<FeedPost>, followers: Int, following: Int, mine: Boolean, busy: Boolean, modifier: Modifier = Modifier,
    canPin: Boolean = false,
    onBack: (() -> Unit)? = null, onEdit: () -> Unit, onCompose: () -> Unit, onChat: () -> Unit, onFollow: () -> Unit, onCredentials: () -> Unit,
    onProfile: (Person) -> Unit, onComments: (FeedPost) -> Unit, onLike: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit,
    onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit, onPin: (FeedPost, Boolean) -> Unit = { _, _ -> }, onBoost: (FeedPost) -> Unit = {}, onInvite: () -> Unit = {}) {
    LazyColumn(modifier.fillMaxSize().testTag("profile-screen"), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    // Cover: a calm blue gradient with a real blur over it.
                    Box(Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF0866FF), Color(0xFF00A3FF), Color(0xFF7DD3FC))))) {
                        Box(Modifier.matchParentSize().glassBlur(18.dp).background(Color(0x260EA5E9)))
                        if (onBack != null) Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                            GlassCircle(Icons.Outlined.ArrowForward, "رجوع", onBack, size = 36.dp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-52).dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.size(100.dp).clip(CircleShape).background(Color.White).border(4.dp, Color.White, CircleShape)
                                .nabdShadow(CircleShape, elevation = 12.dp), contentAlignment = Alignment.Center) { Avatar(person, 92) }
                        }
                        Spacer(Modifier.height(10.dp))
                        PersonName(person, Modifier.padding(top = 2.dp), size = 22)
                        Text("@${person.username}", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (person.bio.isNotBlank()) Text(person.bio, Modifier.padding(top = 8.dp), color = TextMain, fontSize = 13.5.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        if (mine) item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    // The blue gradient strip that marks the points card.
                    Box(Modifier.width(6.dp).height(104.dp).clip(RoundedCornerShape(bottomStart = 24.dp, topStart = 24.dp)).background(AccentStrip))
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("نقاطي", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text("${app.nabd.nativeapp.data.POINTS_DAILY_LOGIN} نقطة كل يوم دخلت فيه، ${app.nabd.nativeapp.data.POINTS_POST} لكل منشور، ${app.nabd.nativeapp.data.POINTS_INVITE} لكل صديق جا برابط الدعوة.",
                                    color = Muted, fontSize = 11.5.sp, lineHeight = 18.sp)
                            }
                            RollingNumber(person.points, 28, Accent)
                        }
                        PillButton("مشاركة رابط الدعوة", onInvite, modifier = Modifier.fillMaxWidth(), enabled = person.inviteCode.isNotBlank(),
                            height = 46.dp, fontSize = 14, icon = Icons.Outlined.Share, testTag = "invite-share")
                    }
                }
            }
        }
        item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stat("المنشورات", posts.size, Modifier.weight(1f))
                    VerticalDivider()
                    Stat("المتابعون", followers, Modifier.weight(1f))
                    VerticalDivider()
                    Stat("المتابَعون", following, Modifier.weight(1f))
                }
            }
        }
        item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mine) {
                        PillButton("تعديل الملف", onEdit, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.ManageAccounts)
                        PillButton("منشور جديد", onCompose, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14, icon = Icons.Outlined.Add)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(if (person.following) "إلغاء المتابعة" else "متابعة", onFollow, modifier = Modifier.weight(1f),
                                enabled = !busy, brush = if (person.following) null else NabdBlue)
                            PillButton("مراسلة", onChat, modifier = Modifier.weight(1f), enabled = !busy, brush = null, icon = Icons.Outlined.ChatBubbleOutline)
                        }
                    }
                    if (mine) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconPlate(Icons.Outlined.Lock, size = 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text("إعدادات الحساب", color = TextMain, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text("تغيير البريد وكلمة السر", color = Muted, fontSize = 12.sp)
                            }
                            PillButton("تعديل", onCredentials, enabled = !busy, brush = null, height = 40.dp, fontSize = 13)
                        }
                    }
                }
            }
        }
        if (posts.isEmpty()) item { EmptyState(Icons.Outlined.PhotoLibrary, if (mine) "ما نشرتي والو بعد" else "ما نشر والو بعد",
            "أول منشور غيبان هنا مباشرة بعد النشر.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(posts, key = { it.id }) { post -> PostCard(post, post.author.id == person.id && mine, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post, it) }, { onShare(post) }, { onProfile(post.author) }, canPin, { onPin(post, it) }, post.author.id == person.id && mine, { onBoost(post) }) }
    }
}

@Composable private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RollingNumber(value, 19)
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
