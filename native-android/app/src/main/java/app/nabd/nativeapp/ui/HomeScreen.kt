package app.nabd.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.AppState
import app.nabd.nativeapp.NabdViewModel
import app.nabd.nativeapp.data.*
import kotlinx.coroutines.launch

@Composable fun HomeScreen(state: AppState, vm: NabdViewModel) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var storyComposer by rememberSaveable { mutableStateOf(false) }
    var storyId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val share: (FeedPost) -> Unit = { post -> scope.launch {
        try { context.startActivity(shareIntent(context, post)) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { android.widget.Toast.makeText(context, "تعذّر فتح المشاركة", android.widget.Toast.LENGTH_SHORT).show() }
    } }
    var composer by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var chatList by rememberSaveable { mutableStateOf(false) }
    var threadId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentPost by rememberSaveable { mutableStateOf<String?>(null) }
    var logoutConfirm by remember { mutableStateOf(false) }
    val snapshot = state.snapshot
    BackHandler(threadId != null || chatList || searching || tab != "home") {
        when { threadId != null -> threadId = null; chatList -> chatList = false; searching -> { searching = false; search = "" }; else -> tab = "home" }
    }
    Scaffold(containerColor = Canvas,
        topBar = { HomeHeader(state.busy, state.error, searching, search, { search=it }, { searching=!searching }, { composer=true }, { chatList=!chatList; threadId=null }, vm::refresh) },
        bottomBar = { HomeDock(tab) { tab=it; chatList=false; threadId=null } }
        ) { padding ->
        if (snapshot == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (state.busy) CircularProgressIndicator(color = AccentLight)
            else TextButton(onClick = vm::refresh) { Text("تحميل البيانات") }
        } else {
            val thread = snapshot.threads.find { it.id == threadId }
            when {
                thread != null -> RichChat(thread, snapshot.me.id, state.busy, Modifier.padding(padding), back = { threadId = null },
                    send = { text, reply, image, ratio, done -> vm.send(thread.id, text, reply, image, ratio, done) },
                    edit = { id, text, done -> vm.editMessage(thread.id, id, text, done) }, delete = { vm.deleteMessage(thread.id, it) },
                    react = { id, kind -> vm.reactMessage(thread.id, id, kind) }, read = { vm.readThread(thread.id) }, refresh = vm::refresh)
                chatList -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Heading("الرسائل"); TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("تحديث") } }
                    if (snapshot.threads.isEmpty()) item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("ما كايناش محادثات", color = Muted) } } }
                    items(snapshot.threads, key = { it.id }) { conversation ->
                        val last = conversation.messages.lastOrNull()
                        NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { threadId = conversation.id }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(conversation.other, 48)
                                Column(Modifier.weight(1f)) {
                                    Text(conversation.other.name, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                                    if (last != null) Text(if (last.deleted) "رسالة محذوفة" else last.text.ifBlank { "صورة" }, color = Muted, fontSize = 12.sp, maxLines = 1)
                                }
                                Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                tab == "home" -> {
                    FeedContent(snapshot, state.stories, state.busy, filter, search, Modifier.padding(padding),
                        onFilter={ filter=it }, onCompose={ composer=true }, onRefresh=vm::refresh,
                        onNewStory={ storyComposer=true }, onStory={ storyId=it },
                        onLike={ vm.react(it, "love") }, onComments={ commentPost=it.id }, onDelete={ vm.delete(it.id) },
                        onReact={ post,kind -> vm.react(post,kind) }, onShare=share)
                }
                tab == "friends" -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item { Heading("الأصدقاء"); TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("تحديث") } }
                    items(snapshot.people.filter { it.id != snapshot.me.id && (search.isBlank() || it.name.contains(search, true) || it.username.contains(search, true)) }, key = { it.id }) { person ->
                        ListItem(headlineContent = { Text(person.name) }, supportingContent = { Text("@${person.username}", color = Muted) }, leadingContent = { Avatar(person) }, trailingContent = { Row {
                            IconButton(onClick = { vm.thread(person) { id -> threadId = id } }, enabled = !state.busy) { Icon(Icons.Outlined.ChatBubbleOutline, "مراسلة ${person.name}") }
                            TextButton(onClick = { vm.follow(person) }, enabled = !state.busy) { Text(if (person.friend) "إلغاء" else "إضافة") }
                        } })
                    }
                }
                tab == "me" -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item { Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF38BDF8)))))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).offset(y = (-44).dp)) {
                            Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White).border(4.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Avatar(snapshot.me, 80) }
                            Text(snapshot.me.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
                            Text(snapshot.me.bio.ifBlank { "@${snapshot.me.username}" }, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                Stat("${snapshot.posts.count { it.author.id == snapshot.me.id }}", "منشورات")
                                Stat("${snapshot.people.count { it.friend }}", "أصدقاء")
                                Stat("${snapshot.people.size}", "أشخاص")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { editing = true }, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text("تعديل الملف") }
                                OutlinedButton(onClick = { composer = true }, shape = RoundedCornerShape(18.dp)) { Text("منشور جديد") }
                            }
                        }
                    } }
                    item { Heading("منشوراتك") }
                    if (snapshot.posts.none { it.author.id == snapshot.me.id }) item { Hint("ما نشرتي والو بعد.") }
                    items(snapshot.posts.filter { it.author.id == snapshot.me.id }, key = { it.id }) { post -> PostCard(post, true, state.busy, { vm.react(post) }, { commentPost = post.id }, { vm.delete(post.id) }, { kind -> vm.react(post, kind) }, { share(post) }) }
                }
                tab == "menu" -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Heading("القائمة") }
                    item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { tab = "me" }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(snapshot.me, 48)
                            Column(Modifier.weight(1f)) { Text(snapshot.me.name, fontWeight = FontWeight.Black, fontSize = 15.sp); Text("@${snapshot.me.username}", color = Muted, fontSize = 12.sp) }
                            Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                    } }
                    item { MenuCard("الرسائل", Icons.Outlined.ChatBubbleOutline) { chatList = true } }
                    item { MenuCard("التنبيهات", Icons.Outlined.Notifications) { tab = "alerts" } }
                    item { MenuCard("تحديث التطبيق", Icons.Outlined.SystemUpdate) { vm.checkUpdates() } }
                    item { MenuCard("تعديل الحساب", Icons.Outlined.ManageAccounts) { editing = true } }
                    item { MenuCard("تسجيل الخروج", Icons.Outlined.Logout) { logoutConfirm = true } }
                }
                tab == "reels" -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Hint("الريلز ما كايناش دابا.") }
                tab == "alerts" -> {
                    val alerts = remember(snapshot) { snapshot.threads.mapNotNull { conversation ->
                        val last = conversation.messages.lastOrNull()
                        if (last != null && last.sender != snapshot.me.id && !last.read) conversation to last else null
                    } }
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Heading("التنبيهات") }
                        if (alerts.isEmpty()) item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("ما كايناش تنبيهات دابا", color = Muted) } } }
                        items(alerts, key = { it.first.id }) { (conversation, message) ->
                            NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { threadId = conversation.id }) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Avatar(conversation.other, 48)
                                    Column(Modifier.weight(1f)) {
                                        Text("رسالة جديدة من ${conversation.other.name}", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        Text(if (message.deleted) "رسالة محذوفة" else message.text.ifBlank { "صورة" }, color = Muted, fontSize = 12.sp, maxLines = 1)
                                    }
                                    Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize().padding(padding)) { }
            }
        }
    }
    if (composer) ComposerDialog(state.busy, state.error, close = { composer = false }, submit = { text, image, ratio -> vm.post(text, image, ratio) { composer = false } })
    if (editing && snapshot != null) ProfileDialog(snapshot.me, state.busy, state.error, close = { editing = false }, save = { name, username, bio, avatar, remove -> vm.profile(name, username, bio, avatar, remove) { editing = false } })
    val post = snapshot?.posts?.find { it.id == commentPost }
    if (post != null) CommentsDialog(post, snapshot!!.me.id, state.busy, state.error, close = { commentPost = null }, send = { text, done -> vm.comment(post.id, text, done) }, like = { vm.likeComment(post.id, it) }, delete = { vm.deleteComment(post.id, it) })
    if (storyComposer) StoryComposer(state.busy, state.error, { storyComposer = false }) { text, image -> vm.addStory(text, image) { storyComposer = false } }
    state.stories.find { it.id == storyId }?.let { story -> StoryViewer(story, state.busy, state.error, { storyId = null }) { vm.removeStory(story.id) { storyId = null } } }
    if (logoutConfirm) AlertDialog(onDismissRequest = { logoutConfirm = false }, title = { Text("تسجيل الخروج؟") }, text = { Text("حسابك ومنشوراتك غيبقاو محفوظين. غتحتاج تسجّل الدخول من جديد.") }, confirmButton = { TextButton(onClick = { logoutConfirm = false; vm.logout() }) { Text("خروج") } }, dismissButton = { TextButton(onClick = { logoutConfirm = false }) { Text("إلغاء") } })
}

@Composable private fun Heading(text: String) { Text(text, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
@Composable private fun Stat(value: String, label: String) { Column { Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 12.sp) } }
@Composable private fun Hint(text: String) { Text(text, color = Muted, modifier = Modifier.padding(24.dp), lineHeight = 24.sp) }
@Composable private fun MenuCard(text: String, icon: ImageVector, onClick: () -> Unit) {
    NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Raised), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp)) }
            Text(text, Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 14.sp)
            Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}
@Composable private fun ComposerDialog(busy: Boolean, serviceError: String?, close: () -> Unit, submit: (String, String?, Double) -> Unit) {
    var text by remember { mutableStateOf("") }; var image by remember { mutableStateOf<String?>(null) }; var ratio by remember { mutableDoubleStateOf(1.0) }
    var imageBusy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch { imageBusy = true; try { val value = inlineImage(context, uri); image = value.first; ratio = value.second } catch (_: Exception) { error = "تعذّر تجهيز الصورة ضمن الحد المسموح (128KB)." } finally { imageBusy = false } } }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("إنشاء منشور") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(text, { text = it.take(5000) }, placeholder = { Text("شنو بغيتي تشارك؟") }, enabled = !busy, minLines = 4, modifier = Modifier.fillMaxWidth())
        if (image != null) NabdImage(image, "الصورة المختارة", Modifier.fillMaxWidth().heightIn(max = 180.dp))
        TextButton(onClick = { picker.launch("image/*") }, enabled = !busy && !imageBusy) { Text(if (imageBusy) "جارٍ تجهيز الصورة…" else "إضافة صورة") }
        Text("منشور عام · وضع تلغرام لا يضمن خصوصية المنشورات", color = Muted, fontSize = 12.sp)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { submit(text, image, ratio) }, enabled = !busy && !imageBusy && (text.isNotBlank() || image != null)) { Text(if (busy) "جارٍ الحفظ…" else "نشر") } }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("إلغاء") } })
}
