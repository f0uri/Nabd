package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.Snapshot

/**
 * Official moderation panel. It is only reachable when the signed-in handle is
 * `f0uri`; every action is a real vault write (badge, restriction, pin,
 * announcement) and nothing here invents a number.
 */
@Composable internal fun AdminScreen(snapshot: Snapshot, busy: Boolean, onBack: () -> Unit,
    onVerify: (Person, Boolean) -> Unit, onRestrict: (Person, Boolean?, Boolean?) -> Unit,
    onPin: (FeedPost, Boolean) -> Unit, onAnnounce: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    val term = query.trim().removePrefix("@").lowercase()
    val target = if (term.length < 2) null else snapshot.people.firstOrNull { it.username.lowercase() == term || it.name.lowercase().contains(term) }
    Column(Modifier.fillMaxSize().testTag("admin-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Raised).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowForward, "رجوع", tint = TextMain, modifier = Modifier.size(18.dp))
            }
            Text("الإدارة", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NabdCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("بحث عن حساب", fontWeight = FontWeight.Black, fontSize = 14.sp)
                OutlinedTextField(value = query, onValueChange = { query = it.take(60) }, label = { Text("@username") }, singleLine = true,
                    enabled = !busy, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("admin-search"))
                if (target == null) Text(if (term.length < 2) "دخّل اسم المستخدم باش يبان الحساب." else "ما لقيناش حساب بهاذا الاسم.", color = Muted, fontSize = 12.sp)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(target, 44)
                        Column(Modifier.weight(1f)) {
                            PersonName(target, size = 15)
                            Text("@${target.username} · ${target.points} نقطة · ${if (target.banned) "محظور" else if (target.restricted) "مقيّد" else "عادي"}", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onVerify(target, !target.verified) }, enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                            Text(if (target.verified) "سحب التوثيق" else "منح التوثيق", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { onRestrict(target, null, !target.restricted) }, enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                            Text(if (target.restricted) "فك التقييد" else "تقييد", fontSize = 12.sp)
                        }
                    }
                    Button(onClick = { onRestrict(target, !target.banned, null) }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = if (target.banned) Accent else SealRed, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(if (target.banned) "فك الحظر" else "حظر الحساب") }
                    val posts = snapshot.posts.filter { it.author.id == target.id }
                    if (posts.isEmpty()) Text("ما عندو حتى منشور.", color = Muted, fontSize = 12.sp)
                    else posts.take(5).forEach { post ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(post.text.ifBlank { "منشور بصورة" }, Modifier.weight(1f), color = TextMain, fontSize = 12.sp, maxLines = 1)
                            TextButton(onClick = { onPin(post, !post.pinned) }, enabled = !busy) { Text(if (post.pinned) "إلغاء التثبيت" else "تثبيت", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                        }
                    }
                }
            } }
            NabdCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إعلان رسمي لجميع المستخدمين", fontWeight = FontWeight.Black, fontSize = 14.sp)
                OutlinedTextField(value = announcement, onValueChange = { announcement = it.take(280) }, label = { Text("نص الإعلان") }, enabled = !busy,
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("admin-announcement"), maxLines = 4)
                Button(onClick = { onAnnounce(announcement); announcement = "" }, enabled = !busy && announcement.trim().isNotBlank(),
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Text("إرسال الإعلان") }
                Text("الإعلان كيتخزّن فالخادم وكيبان لكاع المستخدمين حتى يخفّيوه.", color = Muted, fontSize = 11.sp)
            } }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable internal fun BoostDialog(post: FeedPost, points: Int, cost: Int, busy: Boolean, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("تعزيز المنشور") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("غادي تصرف $cost نقطة من نقاطك (عندك $points) باش يبان المنشور فالأعلى عند كاع المستخدمين مدة 24 ساعة.")
            Text("التعزيز ما كيبدّل حتى رقم تفاعل: لا نبضات ولا تعليقات.", color = Muted, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = confirm, enabled = !busy && points >= cost) { Text("تأكيد", fontWeight = FontWeight.Black) } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء", color = Muted) } })
}
