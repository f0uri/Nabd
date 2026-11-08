package app.nabd.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.Snapshot

/** A post reference the admin typed: a bare id or a nabd://post/<id> link. */
private fun parseBoostPostRef(input: String, snapshot: Snapshot): FeedPost? {
    val id = input.trim().substringAfterLast('/').substringBefore('?').trim()
    if (id.isEmpty()) return null
    return snapshot.posts.find { it.id == id }
}

/**
 * Official moderation panel. It is only reachable when the signed-in handle is
 * `f0uri`; moderation actions are real vault writes (badge, restriction, pin,
 * announcement). The boost section is the one place that adds generated
 * numbers on purpose: the admin types a count and the growth is stored in
 * "growthV1", so it survives restarts and merges with the automatic growth.
 */
@Composable internal fun AdminScreen(snapshot: Snapshot, busy: Boolean, onBack: () -> Unit,
    onVerify: (Person, Boolean) -> Unit, onRestrict: (Person, Boolean?, Boolean?) -> Unit,
    onPin: (FeedPost, Boolean) -> Unit, onAnnounce: (String) -> Unit,
    onBoostFollowers: (Person, Int) -> Unit = { _, _ -> }, onBoostReactions: (String, Int) -> Unit = { _, _ -> },
    onAddComment: (String, String) -> Unit = { _, _ -> }) {
    var query by rememberSaveable { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    var followersAmount by rememberSaveable { mutableStateOf("") }
    var postRef by rememberSaveable { mutableStateOf("") }
    var reactionsAmount by rememberSaveable { mutableStateOf("") }
    var commentPostRef by rememberSaveable { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    val term = query.trim().removePrefix("@").lowercase()
    val target = if (term.length < 2) null else snapshot.people.firstOrNull { it.username.lowercase() == term || it.name.lowercase().contains(term) }
    val boostedPost = remember(postRef) { parseBoostPostRef(postRef, snapshot) }
    val commentPost = remember(commentPostRef) { parseBoostPostRef(commentPostRef, snapshot) }
    Column(Modifier.fillMaxSize().testTag("admin-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassCircle(Icons.Outlined.ArrowForward, "رجوع", onBack, size = 38.dp)
            Text("الإدارة", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }
        // 2.9.5: the search section is FIXED above the scroll — it never
        // scrolls away and never sticks mid-scroll, so it is always visible
        // and usable.
        NabdCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("بحث عن حساب", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
            NabdField(query, { query = it.take(60) }, "@username", Modifier.fillMaxWidth(), enabled = !busy, tag = "admin-search")
            if (target == null) Text(if (term.length < 2) "دخّل اسم المستخدم باش يبان الحساب." else "ما لقيناش حساب بهاذا الاسم.", color = Muted, fontSize = 12.5.sp, lineHeight = 19.sp)
        } }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (target != null) NabdCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(target, 48)
                    Column(Modifier.weight(1f)) {
                        PersonName(target, size = 15)
                        Text("@${target.username} · ${target.points} نقطة · ${if (target.banned) "محظور" else if (target.restricted) "مقيّد" else "عادي"}", color = Muted, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(if (target.verified) "سحب التوثيق" else "منح التوثيق", { onVerify(target, !target.verified) },
                        modifier = Modifier.weight(1f), enabled = !busy, brush = if (target.verified) null else NabdBlue, height = 44.dp, fontSize = 13)
                    PillButton(if (target.restricted) "فك التقييد" else "تقييد", { onRestrict(target, null, !target.restricted) },
                        modifier = Modifier.weight(1f), enabled = !busy, brush = if (target.restricted) null else NabdBlue, height = 44.dp, fontSize = 13)
                }
                PillButton(if (target.banned) "فك الحظر" else "حظر الحساب", { onRestrict(target, !target.banned, null) }, modifier = Modifier.fillMaxWidth(),
                    enabled = !busy, brush = if (target.banned) null else NabdRed, height = 46.dp, fontSize = 14)
                Text("تعزيز المتابعين", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("المتابعون الحاليون: ${snapshot.social.followers[target.id] ?: 0}", color = Muted, fontSize = 12.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NabdField(followersAmount, { followersAmount = it.filter { c -> c.isDigit() }.take(7) }, "عدد المتابعين",
                        Modifier.weight(1f), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), tag = "admin-boost-followers")
                    PillButton("أضف المتابعين", {
                        followersAmount.toIntOrNull()?.let { onBoostFollowers(target, it) }
                        followersAmount = ""
                    }, enabled = !busy && (followersAmount.toIntOrNull() ?: 0) > 0, brush = NabdBlue, height = 46.dp, fontSize = 13)
                }
                Text("المتابعون المضافون يظهروا في قائمة متابعي هاد الحساب.", color = Muted, fontSize = 11.5.sp, lineHeight = 18.sp)
                val posts = snapshot.posts.filter { it.author.id == target.id }
                if (posts.isEmpty()) Text("ما عندو حتى منشور.", color = Muted, fontSize = 12.5.sp)
                else posts.take(5).forEach { post ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(post.text.ifBlank { "منشور بصورة" }, Modifier.weight(1f), color = TextMain, fontSize = 12.5.sp, maxLines = 1)
                        PillButton(if (post.pinned) "إلغاء التثبيت" else "تثبيت", { onPin(post, !post.pinned) },
                            enabled = !busy, brush = if (post.pinned) null else NabdBlue, height = 38.dp, fontSize = 12)
                    }
                }
            } }
            NabdCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تعزيز التفاعلات", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                NabdField(postRef, { postRef = it.take(200) }, "رابط أو معرّف المنشور", Modifier.fillMaxWidth(), enabled = !busy, tag = "admin-boost-post")
                if (boostedPost == null) Text("دخّل رابط المنشور (nabd://post/...) أو معرّفه، أو اختر منشوراً من الحساب المبحوث عنه لتحت.",
                    color = Muted, fontSize = 12.5.sp, lineHeight = 19.sp)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(boostedPost.author, 40)
                        Column(Modifier.weight(1f)) {
                            Text(boostedPost.text.ifBlank { "منشور بصورة" }, color = TextMain, fontSize = 12.5.sp, maxLines = 1)
                            Text("@${boostedPost.author.username} · ${boostedPost.likes} تفاعل", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NabdField(reactionsAmount, { reactionsAmount = it.filter { c -> c.isDigit() }.take(7) }, "عدد التفاعلات",
                        Modifier.weight(1f), enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), tag = "admin-boost-reactions")
                    PillButton("أضف التفاعلات", {
                        boostedPost?.let { post -> reactionsAmount.toIntOrNull()?.let { onBoostReactions(post.id, it) } }
                        reactionsAmount = ""
                    }, enabled = !busy && boostedPost != null && (reactionsAmount.toIntOrNull() ?: 0) > 0, brush = NabdBlue, height = 46.dp, fontSize = 13)
                }
                Text("التفاعلات المضافة تُحفظ وتظهر في عداد المنشور وقائمة متفاعليه.",
                    color = Muted, fontSize = 11.5.sp, lineHeight = 18.sp)
                if (target != null) {
                    val targetPosts = snapshot.posts.filter { it.author.id == target.id }.take(5)
                    if (targetPosts.isNotEmpty()) {
                        Text("منشورات ${target.name} — اضغط على منشور لاختياره:", color = Muted, fontSize = 12.5.sp)
                        targetPosts.forEach { post ->
                            Row(Modifier.fillMaxWidth().pressClick { postRef = post.id }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(post.text.ifBlank { "منشور بصورة" }, Modifier.weight(1f), color = TextMain, fontSize = 12.5.sp, maxLines = 1)
                                Text("${post.likes}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } }
            NabdCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إضافة تعليق", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                NabdField(commentPostRef, { commentPostRef = it.take(200) }, "رابط أو معرّف المنشور", Modifier.fillMaxWidth(), enabled = !busy, tag = "admin-comment-post")
                if (commentPost == null) Text("دخّل رابط المنشور (nabd://post/...) أو معرّفه باش يبان هنا.",
                    color = Muted, fontSize = 12.5.sp, lineHeight = 19.sp)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(commentPost.author, 40)
                        Column(Modifier.weight(1f)) {
                            Text(commentPost.text.ifBlank { "منشور بصورة" }, color = TextMain, fontSize = 12.5.sp, maxLines = 1)
                            Text("@${commentPost.author.username} · ${commentPost.comments.size} تعليق", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                NabdField(commentText, { commentText = it.take(2000) }, "اكتب التعليق…", Modifier.fillMaxWidth(), enabled = !busy,
                    singleLine = false, maxLines = 4, tag = "admin-comment-text")
                PillButton("أضف التعليق", {
                    commentPost?.let { post -> onAddComment(post.id, commentText.trim()) }
                    commentText = ""
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy && commentPost != null && commentText.trim().isNotBlank(), brush = NabdBlue, height = 46.dp, fontSize = 14)
                Text("التعليق كيبان فالبلاصة فالمنشور.", color = Muted, fontSize = 11.5.sp, lineHeight = 18.sp)
            } }
            NabdCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إعلان رسمي لجميع المستخدمين", color = TextMain, fontWeight = FontWeight.Black, fontSize = 15.sp)
                NabdField(announcement, { announcement = it.take(280) }, "نص الإعلان", Modifier.fillMaxWidth(), enabled = !busy,
                    singleLine = false, maxLines = 4, tag = "admin-announcement")
                PillButton("إرسال الإعلان", { onAnnounce(announcement); announcement = "" }, modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && announcement.trim().isNotBlank(), icon = Icons.Outlined.Campaign)
                Text("الإعلان الرسمي كيبان لكاع المستخدمين حتى يخفّيوه.", color = Muted, fontSize = 11.5.sp, lineHeight = 18.sp)
            } }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Real boost: it spends real points and never touches a reaction counter. */
@Composable internal fun BoostDialog(post: FeedPost, points: Int, cost: Int, busy: Boolean, close: () -> Unit, confirm: () -> Unit) {
    GlassWindow(close = close) {
        IconPlate(Icons.Outlined.RocketLaunch, size = 62.dp, tint = AccentLight)
        Text("تعزيز المنشور", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("غادي تصرف $cost نقطة من نقاطك (عندك $points) باش يبان المنشور فالأعلى عند كاع المستخدمين مدة 24 ساعة.",
            color = Muted, fontSize = 13.5.sp, lineHeight = 21.sp)
        Text("التعزيز ما كيبدّل حتى رقم تفاعل: لا نبضات ولا تعليقات.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        PillButton("تأكيد التعزيز", confirm, modifier = Modifier.fillMaxWidth(), enabled = !busy && points >= cost)
        PillButton("إلغاء", close, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
}
