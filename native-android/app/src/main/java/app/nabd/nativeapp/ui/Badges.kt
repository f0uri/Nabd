package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.Story
import app.nabd.nativeapp.data.isAdminHandle
import java.time.Duration
import java.time.Instant

/** One earned badge: a vector, a name and the real activity that unlocked it. */
internal class Badge(val id: String, val title: String, val hint: String, val icon: ImageVector, val tint: Color)

/**
 * Badges are read out of the vault only: the follow graph, the reaction counts,
 * the stories that are still up and the day the account joined. Nothing here is
 * invented, and a badge that is not earned simply never appears.
 */
internal fun earnedBadges(person: Person, posts: List<FeedPost>, stories: List<Story>, followers: Int): List<Badge> {
    val out = mutableListOf<Badge>()
    if (isAdminHandle(person.username)) out += Badge("admin", "إدارة نبض", "الحساب الرسمي اللي كيسيّر نبض.", Icons.Outlined.AdminPanelSettings, Accent)
    else if (person.verified) out += Badge("verified", "حساب موثّق", "الإدارة وثّقات هاد الحساب.", Icons.Outlined.Group, Accent)
    if (posts.any { it.likes >= 100 }) out += Badge("pulse", "نابض", "منشور واحد فيك جاب 100 نبضة ولا كتر.", Icons.Outlined.LocalFireDepartment, AccentLight)
    if (stories.size >= 10) out += Badge("story", "حكواتي", "10 قصص ولا كتر منشورة فحسابك.", Icons.Outlined.PhotoLibrary, Accent)
    if (followers >= 500) out += Badge("reach", "مؤثر", "500 متابع ولا كتر كيتبعو هاد الحساب.", Icons.Outlined.Group, Accent)
    val days = runCatching { Duration.between(Instant.parse(person.joined), Instant.now()).toDays() }.getOrNull() ?: 0
    if (days >= 30) out += Badge("veteran", "رائد", "هاد الحساب عندو كتر من 30 يوم فنبض.", Icons.Outlined.Person, Accent)
    return out
}

/** The badges of one account, as a horizontally scrollable strip of glass chips. */
@Composable internal fun BadgeCard(badges: List<Badge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    var open by remember { mutableStateOf<Badge?>(null) }
    NabdCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الشارات", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Accent.copy(alpha = .12f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(badges.size.toString(), color = Accent, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                }
            }
            Text("كل شارة كتجي من نشاط حقيقي فحسابك.", color = Muted, fontSize = 11.5.sp, lineHeight = 17.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(badges, key = { it.id }) { badge -> BadgeChip(badge) { open = badge } }
            }
        }
    }
    val chosen = open
    if (chosen != null) GlassConfirm(chosen.title, chosen.hint, "إغلاق", onConfirm = { open = null }, close = { open = null }, danger = false)
}

@Composable private fun BadgeChip(badge: Badge, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.clip(shape).background(Raised).border(1.dp, GlassEdge, shape).pressClick(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (badge.id == "verified" || badge.id == "admin") VerifiedSeal(size = 18.dp, tint = if (badge.id == "admin") SealRed else VerifiedBlue)
        else Icon(badge.icon, null, tint = badge.tint, modifier = Modifier.size(18.dp))
        Text(badge.title, color = TextMain, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}
