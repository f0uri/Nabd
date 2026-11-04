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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.AppState
import app.nabd.nativeapp.BuildConfig
import app.nabd.nativeapp.NabdViewModel
import app.nabd.nativeapp.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private data class Destination(val id: String, val title: String, val icon: ImageVector)
private val destinations = listOf(Destination("home", "الرئيسية", Icons.Outlined.Home), Destination("reels", "ريلز", Icons.Outlined.PlayCircle), Destination("friends", "أصدقاء", Icons.Outlined.People), Destination("menu", "القائمة", Icons.Outlined.GridView), Destination("alerts", "تنبيهات", Icons.Outlined.Notifications), Destination("me", "حسابي", Icons.Outlined.Person))

@Composable fun HomeScreen(state: AppState, vm: NabdViewModel) {
    var tab by rememberSaveable { mutableStateOf("home") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
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
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MonitorHeart, null, tint = AccentLight, modifier = Modifier.size(26.dp))
                    Text("نبض", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    IconButton(onClick = { composer = true }, enabled = !state.busy) { Icon(Icons.Outlined.AddCircle, "إنشاء منشور", tint = AccentLight) }
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Outlined.Search, "بحث") }
                    IconButton(onClick = { chatList = !chatList; threadId = null }) { Icon(Icons.Outlined.ChatBubbleOutline, "الرسائل") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentLight, trackColor = Raised)
                if (searching) OutlinedTextField(search, { search = it }, label = { Text("ابحث بالاسم أو المنشورات") }, singleLine = true, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                if (state.error != null) Row(Modifier.fillMaxWidth().background(Color(0xFF361720)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("إعادة المحاولة") }
                }
            }
        },
        bottomBar = {
            Surface(color = Raised.copy(alpha = .96f), shape = CircleShape, border = BorderStroke(1.dp, Outline), modifier = Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()) {
                Row(Modifier.padding(5.dp).heightIn(min = 64.dp)) {
                    destinations.forEach { dest ->
                        Column(Modifier.weight(1f).clip(CircleShape).background(if (tab == dest.id) Outline else Color.Transparent).clickable { tab = dest.id; chatList = false; threadId = null }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(dest.icon, dest.title, tint = TextMain, modifier = Modifier.size(25.dp))
                            Text(dest.title, color = if (tab == dest.id) TextMain else Muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }) { padding ->
        if (snapshot == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (state.busy) CircularProgressIndicator(color = AccentLight)
            else TextButton(onClick = vm::refresh) { Text("تحميل البيانات") }
        } else {
            val thread = snapshot.threads.find { it.id == threadId }
            when {
                thread != null -> ChatScreen(thread, snapshot.me.id, state.busy, Modifier.padding(padding), back = { threadId = null }, send = { text, done -> vm.send(thread.id, text, done) }, refresh = vm::refresh)
                chatList -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item { Heading("الرسائل"); TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("تحديث") } }
                    if (snapshot.threads.isEmpty()) item { Hint("ما كايناش محادثات. اختار صديق وابدأ محادثة.") }
                    items(snapshot.threads, key = { it.id }) { conversation ->
                        ListItem(headlineContent = { Text(conversation.other.name) }, supportingContent = { Text(conversation.messages.lastOrNull()?.let { if (it.deleted) "رسالة محذوفة" else it.text } ?: "بداية المحادثة", maxLines = 1) }, leadingContent = { Avatar(conversation.other) }, modifier = Modifier.clickable { threadId = conversation.id })
                    }
                }
                tab == "home" || (searching && tab == "me") -> {
                    val posts = remember(snapshot.posts, filter, search) { snapshot.posts.filter { p ->
                        (filter != "friends" || p.author.friend) && (filter != "image" || p.image != null) && filter != "video" && (search.isBlank() || p.text.contains(search, true) || p.author.name.contains(search, true))
                    } }
                    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("native-feed")) {
                        item("filters") { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "الكل", "friends" to "الأصدقاء", "image" to "الصور", "video" to "الفيديو").forEach { (key, label) -> FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TextMain, selectedLabelColor = Canvas)) }
                        } }
                        item("compose") { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(snapshot.me); TextButton(onClick = { composer = true }, modifier = Modifier.weight(1f)) { Text("فيمَ تفكر؟", color = Muted) }; IconButton(onClick = { composer = true }) { Icon(Icons.Outlined.Image, "نشر صورة", tint = AccentLight) }
                        } }
                        item("refresh") { TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("تحديث المنشورات") } }
                        if (posts.isEmpty()) item { Hint(if (filter == "video") "الخدمة الحالية كتدعم النصوص والصور فقط، ماشي الفيديو." else "ما كايناش منشورات فهاد التصفية") }
                        items(posts, key = { it.id }, contentType = { "post" }) { post -> PostCard(post, post.author.id == snapshot.me.id, state.busy, { vm.react(post) }, { commentPost = post.id }, { vm.delete(post.id) }) }
                    }
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
                    item { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(snapshot.me, 88); Heading(snapshot.me.name); Text("@${snapshot.me.username}", color = AccentLight)
                        Text(snapshot.me.bio, Modifier.padding(12.dp)); Text("${snapshot.people.count { it.friend }} أصدقاء · ${snapshot.posts.count { it.author.id == snapshot.me.id }} منشورات", color = Muted)
                        OutlinedButton(onClick = { editing = true }) { Text("تعديل الملف الشخصي") }
                    } }
                    items(snapshot.posts.filter { it.author.id == snapshot.me.id }, key = { it.id }) { post -> PostCard(post, true, state.busy, { vm.react(post) }, { commentPost = post.id }, { vm.delete(post.id) }) }
                }
                tab == "menu" -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    item { Heading("القائمة") }
                    item { ListItem(headlineContent = { Text(snapshot.me.name) }, supportingContent = { Text("@${snapshot.me.username}") }, leadingContent = { Avatar(snapshot.me) }, modifier = Modifier.clickable { tab = "me" }) }
                    item { MenuRow("الرسائل", Icons.Outlined.ChatBubbleOutline) { chatList = true } }
                    item { MenuRow("تحديث التطبيق", Icons.Outlined.SystemUpdate) { vm.checkUpdates() } }
                    item { MenuRow("تعديل الحساب", Icons.Outlined.ManageAccounts) { editing = true } }
                    item { MenuRow("تسجيل الخروج", Icons.Outlined.Logout) { logoutConfirm = true } }
                    item { Hint("نبض ${BuildConfig.VERSION_NAME}\nKotlin · Jetpack Compose\nالخدمة الحالية: Telegram Direct") }
                }
                tab == "reels" -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Hint("الريلز ما متوفراش فخدمة تلغرام الحالية. ما عرضناش فيديوهات تجريبية على أنها محتوى حقيقي.") }
                else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Hint("خدمة الإشعارات ما متوفراش حالياً فالوضع المباشر.") }
            }
        }
    }
    if (composer) ComposerDialog(state.busy, state.error, close = { composer = false }, submit = { text, image, ratio -> vm.post(text, image, ratio) { composer = false } })
    if (editing && snapshot != null) ProfileDialog(snapshot.me, state.busy, state.error, close = { editing = false }, save = { name, username, bio, avatar -> vm.profile(name, username, bio, avatar) { editing = false } })
    val post = snapshot?.posts?.find { it.id == commentPost }
    if (post != null) CommentsDialog(post, state.busy, state.error, close = { commentPost = null }, send = { text, done -> vm.comment(post.id, text, done) })
    if (logoutConfirm) AlertDialog(onDismissRequest = { logoutConfirm = false }, title = { Text("تسجيل الخروج؟") }, text = { Text("حسابك ومنشوراتك غيبقاو محفوظين. غتحتاج تسجّل الدخول من جديد.") }, confirmButton = { TextButton(onClick = { logoutConfirm = false; vm.logout() }) { Text("خروج") } }, dismissButton = { TextButton(onClick = { logoutConfirm = false }) { Text("إلغاء") } })
}

@Composable private fun Heading(text: String) { Text(text, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
@Composable private fun Hint(text: String) { Text(text, color = Muted, modifier = Modifier.padding(24.dp), lineHeight = 24.sp) }
@Composable private fun Avatar(person: Person, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Outline), contentAlignment = Alignment.Center) {
        if (person.avatar != null) AsyncImage(person.avatar, "صورة ${person.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Person, "لا توجد صورة حساب", tint = Muted, modifier = Modifier.size((size * .65f).dp))
    }
}
@Composable private fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(text) }, leadingContent = { Icon(icon, null, tint = AccentLight) }, trailingContent = { Icon(Icons.Outlined.ChevronLeft, null) }, modifier = Modifier.clickable(onClick = onClick))
}
@Composable private fun PostCard(post: FeedPost, mine: Boolean, busy: Boolean, like: () -> Unit, comments: () -> Unit, delete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.author)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(post.author.name, fontWeight = FontWeight.Bold); Text(post.date.take(10), color = Muted, fontSize = 12.sp) }
            if (mine) IconButton(onClick = { confirm = true }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "حذف المنشور") }
        }
        if (post.text.isNotBlank()) Text(post.text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 25.sp)
        if (post.image != null) AsyncImage(post.image, "صورة المنشور", Modifier.fillMaxWidth().aspectRatio(post.ratio.coerceIn(.25f, 4f)), contentScale = ContentScale.Crop)
        Text("${post.likes} تفاعل · ${post.comments.size} تعليق", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = like, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ThumbUp, null, tint = if (post.myReaction != null) AccentLight else Muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("إعجاب", color = if (post.myReaction != null) AccentLight else Muted) }
            TextButton(onClick = comments, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("تعليق") }
        }
        HorizontalDivider(color = Outline)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("حذف المنشور؟") }, text = { Text("لن نؤكد الحذف حتى يتأكد الحفظ في الخدمة.") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }) { Text("حذف") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}

@Composable private fun ComposerDialog(busy: Boolean, serviceError: String?, close: () -> Unit, submit: (String, String?, Double) -> Unit) {
    var text by remember { mutableStateOf("") }; var image by remember { mutableStateOf<String?>(null) }; var ratio by remember { mutableDoubleStateOf(1.0) }
    var imageBusy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch { imageBusy = true; try { val value = inlineImage(context, uri); image = value.first; ratio = value.second } catch (_: Exception) { error = "تعذّر تجهيز الصورة ضمن الحد المسموح (128KB)." } finally { imageBusy = false } } }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("إنشاء منشور") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(text, { text = it.take(5000) }, placeholder = { Text("شنو بغيتي تشارك؟") }, enabled = !busy, minLines = 4, modifier = Modifier.fillMaxWidth())
        if (image != null) AsyncImage(image, "الصورة المختارة", Modifier.fillMaxWidth().heightIn(max = 180.dp))
        TextButton(onClick = { picker.launch("image/*") }, enabled = !busy && !imageBusy) { Text(if (imageBusy) "جارٍ تجهيز الصورة…" else "إضافة صورة") }
        Text("منشور عام · وضع تلغرام لا يضمن خصوصية المنشورات", color = Muted, fontSize = 12.sp)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { submit(text, image, ratio) }, enabled = !busy && !imageBusy && (text.isNotBlank() || image != null)) { Text(if (busy) "جارٍ الحفظ…" else "نشر") } }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("إلغاء") } })
}
@Composable private fun ProfileDialog(person: Person, busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String, String, String?) -> Unit) {
    var name by remember { mutableStateOf(person.name) }; var username by remember { mutableStateOf(person.username) }; var bio by remember { mutableStateOf(person.bio) }
    AlertDialog(onDismissRequest = close, title = { Text("تعديل الحساب") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("الاسم") }, enabled = !busy)
        OutlinedTextField(username, { username = it.take(40) }, label = { Text("اسم المستخدم") }, enabled = !busy)
        OutlinedTextField(bio, { bio = it.take(160) }, label = { Text("نبذة") }, enabled = !busy)
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { save(name, username, bio, null) }, enabled = !busy) { Text(if (busy) "جارٍ الحفظ…" else "حفظ") } }, dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("إلغاء") } })
}
@Composable private fun CommentsDialog(post: FeedPost, busy: Boolean, serviceError: String?, close: () -> Unit, send: (String, () -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("التعليقات") }, text = { Column {
        LazyColumn(Modifier.heightIn(max = 260.dp)) { items(post.comments, key = { it.id }) { comment -> Column(Modifier.padding(vertical = 8.dp)) { Text(comment.author.name, fontWeight = FontWeight.Bold); Text(comment.text) } } }
        OutlinedTextField(text, { text = it.take(2000) }, label = { Text("اكتب تعليقاً") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        serviceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { Button(onClick = { send(text) { text = "" } }, enabled = !busy && text.isNotBlank()) { Text("إرسال") } }, dismissButton = { TextButton(onClick = close) { Text("إغلاق") } })
}
@Composable private fun ChatScreen(thread: Thread, self: String, busy: Boolean, modifier: Modifier, back: () -> Unit, send: (String, () -> Unit) -> Unit, refresh: () -> Unit) {
    var text by rememberSaveable(thread.id) { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Outlined.ArrowForward, "رجوع") }; Text(thread.other.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = refresh, enabled = !busy) { Icon(Icons.Outlined.Refresh, "تحديث الرسائل") } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(12.dp)) {
            items(thread.messages.reversed(), key = { it.id }) { message -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (message.sender == self) Arrangement.Start else Arrangement.End) {
                Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(20.dp)).background(if (message.sender == self) Accent else Raised).padding(12.dp)) {
                    if (message.image != null && !message.deleted) AsyncImage(message.image, "صورة الرسالة", Modifier.width(220.dp).heightIn(max = 220.dp))
                    Text(if (message.deleted) "تم حذف الرسالة" else message.text)
                    if (message.sender == self) Text(if (message.read) "مقروءة" else "مرسلة", fontSize = 10.sp, color = Color.White.copy(alpha = .8f))
                }
            } }
        }
        Row(Modifier.imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(5000) }, placeholder = { Text("رسالتك…") }, enabled = !busy, modifier = Modifier.weight(1f), shape = CircleShape, maxLines = 4)
            IconButton(onClick = { send(text) { text = "" } }, enabled = !busy && text.isNotBlank()) { Icon(Icons.Outlined.Send, "إرسال", tint = AccentLight) }
        }
    }
}
