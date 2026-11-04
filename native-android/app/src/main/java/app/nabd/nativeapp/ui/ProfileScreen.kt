package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
 * Profile card plus the account settings entry. Every number shown here comes
 * from the real vault snapshot; nothing is simulated or inflated.
 */
@Composable internal fun ProfileScreen(person: Person, posts: List<FeedPost>, followers: Int, following: Int, mine: Boolean, busy: Boolean, modifier: Modifier = Modifier,
    canPin: Boolean = false,
    onBack: (() -> Unit)? = null, onEdit: () -> Unit, onCompose: () -> Unit, onChat: () -> Unit, onFollow: () -> Unit, onCredentials: () -> Unit,
    onProfile: (Person) -> Unit, onComments: (FeedPost) -> Unit, onLike: (FeedPost) -> Unit, onDelete: (FeedPost) -> Unit,
    onReact: (FeedPost, String?) -> Unit, onShare: (FeedPost) -> Unit, onPin: (FeedPost, Boolean) -> Unit = { _, _ -> }) {
    LazyColumn(modifier.fillMaxSize().testTag("profile-screen"), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF38BDF8)))))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-42).dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(92.dp).clip(CircleShape).background(Color.White).border(4.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Avatar(person, 84) }
                            if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.offset(y = (-8).dp)) { Text("رجوع", color = Accent, fontWeight = FontWeight.Black) }
                        }
                        PersonName(person, Modifier.padding(top = 8.dp), size = 20)
                        Text("@${person.username}", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (person.bio.isNotBlank()) Text(person.bio, Modifier.padding(top = 6.dp), color = TextMain, fontSize = 13.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
        item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Stat("المنشورات", posts.size)
                    Stat("المتابعون", followers)
                    Stat("المتابَعون", following)
                }
            }
        }
        item {
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (mine) {
                            Button(onClick = onEdit, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White), modifier = Modifier.weight(1f)) { Text("تعديل الملف") }
                            OutlinedButton(onClick = onCompose, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) { Text("منشور جديد") }
                        } else {
                            Button(onClick = onChat, enabled = !busy, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White), modifier = Modifier.weight(1f)) { Text("مراسلة") }
                            OutlinedButton(onClick = onFollow, enabled = !busy, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) { Text(if (person.following) "إلغاء المتابعة" else "متابعة") }
                        }
                    }
                    if (mine) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Raised), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lock, null, tint = Accent, modifier = Modifier.size(20.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text("إعدادات الحساب", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text("تغيير البريد وكلمة السر", color = Muted, fontSize = 12.sp)
                            }
                            TextButton(onClick = onCredentials, enabled = !busy) { Text("تعديل", fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }
        }
        if (posts.isEmpty()) item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(if (mine) "ما نشرتي والو بعد." else "ما نشر والو بعد.", color = Muted) }
        } }
        items(posts, key = { it.id }) { post -> PostCard(post, post.author.id == person.id && mine, busy, { onLike(post) }, { onComments(post) }, { onDelete(post) }, { onReact(post, it) }, { onShare(post) }, { onProfile(post.author) }, canPin, { onPin(post, it) }) }
    }
}

@Composable private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RollingNumber(value)
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

