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
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
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
    var storyOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
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
    var profileId by rememberSaveable { mutableStateOf<String?>(null) }
    var adminOpen by rememberSaveable { mutableStateOf(false) }
    var boostId by rememberSaveable { mutableStateOf<String?>(null) }
    var credentials by remember { mutableStateOf(false) }
    var logoutConfirm by remember { mutableStateOf(false) }
    val snapshot = state.snapshot
    val invite: () -> Unit = { scope.launch {
        val code = snapshot?.me?.inviteCode.orEmpty()
        try { context.startActivity(inviteIntent(context, code)) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { android.widget.Toast.makeText(context, "تعذّر فتح الدعوة", android.widget.Toast.LENGTH_SHORT).show() }
    } }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, state.signedIn) {
        if (!state.signedIn) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(3000); vm.poll() } }
    }
    BackHandler(threadId != null || chatList || searching || profileId != null || adminOpen || storyOwner != null || tab != "home") {
        when { storyOwner != null -> storyOwner = null; threadId != null -> threadId = null; chatList -> chatList = false; profileId != null -> profileId = null; adminOpen -> adminOpen = false; searching -> { searching = false; search = "" }; else -> tab = "home" }
    }
    Scaffold(containerColor = Canvas,
        topBar = { HomeHeader(state.busy, state.error, searching, search, { search=it }, { searching=!searching }, { composer=true }, { chatList=!chatList; threadId=null }, vm::refresh,
            alerts = snapshot?.notifications?.count { !it.read } ?: 0, onAlerts = { tab = "alerts" }) },
        bottomBar = { HomeDock(tab) { tab=it; chatList=false; threadId=null } }
        ) { padding ->
        if (snapshot == null) ShimmerFeed(Modifier.padding(padding)) else {
            val thread = snapshot.threads.find { it.id == threadId }
            val storyGroups = remember(snapshot.stories, state.stories) {
                val legacy = state.stories.map { item -> Story("local-${item.id}", snapshot.me, item.text, item.image, item.date) }
                if (legacy.isEmpty()) snapshot.stories else {
                    val mine = snapshot.stories.indexOfFirst { it.owner.id == snapshot.me.id }
                    if (mine < 0) snapshot.stories + StoryGroup(snapshot.me, legacy, false)
                    else snapshot.stories.toMutableList().also { list -> list[mine] = StoryGroup(snapshot.me, (list[mine].items + legacy).sortedBy { it.date }, false) }
                }
            }
            when {
                storyOwner != null -> StoryViewerScreen(storyGroups, storyOwner!!, null, snapshot.me, state.busy, state.error,
                    onClose = { storyOwner = null }, onSeen = vm::seeStory, onDelete = { vm.removeStory(it) { } }, onReply = { person, text -> vm.storyReply(person, text) })
                adminOpen && app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username) -> AdminScreen(snapshot, state.busy, onBack = { adminOpen = false },
                    onVerify = { person, verified -> vm.verify(person, verified) },
                    onRestrict = { person, banned, restricted -> vm.restrict(person, banned, restricted) },
                    onPin = { post, pinned -> vm.pin(post, pinned) },
                    onAnnounce = { text -> vm.announce(text) { android.widget.Toast.makeText(context, "وصل الإعلان", android.widget.Toast.LENGTH_SHORT).show() } })
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
                profileId != null -> {
                    val target = snapshot.people.find { it.id == profileId } ?: snapshot.posts.firstOrNull { it.author.id == profileId }?.author ?: snapshot.me
                    val own = target.id == snapshot.me.id
                    ProfileScreen(target, snapshot.posts.filter { it.author.id == target.id }, snapshot.social.followers[target.id] ?: 0, snapshot.social.following[target.id] ?: 0, own, state.busy, Modifier.padding(padding),
                        canPin = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                        onBack = { profileId = null }, onEdit = { editing = true }, onCompose = { composer = true },
                        onChat = { vm.thread(target) { id -> threadId = id } }, onFollow = { vm.followUser(target, !target.following) }, onCredentials = { credentials = true },
                        onProfile = { profileId = it.id }, onComments = { commentPost = it.id }, onLike = { vm.react(it) }, onDelete = { vm.delete(it.id) },
                        onReact = { post, kind -> vm.react(post, kind) }, onShare = share, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id })
                }
                tab == "home" -> {
                    FeedContent(snapshot, state.stories, state.busy, filter, search, Modifier.padding(padding),
                        onFilter={ filter=it }, onCompose={ composer=true }, onRefresh=vm::refresh,
                        onNewStory={ storyComposer=true }, onStory={ group -> storyOwner = group.owner.id },
                        onLike={ vm.react(it) }, onComments={ commentPost=it.id }, onDelete={ vm.delete(it.id) },
                        onReact={ post,kind -> vm.react(post,kind) }, onShare=share, onProfile={ profileId=it.id }, onPin={ post,pinned -> vm.pin(post,pinned) }, onBoost={ boostId=it.id }, onImage={ viewer=it },
                        announcement=snapshot.announcements.firstOrNull { it.id !in state.dismissed }, onDismissAnnouncement={ vm.dismissAnnouncement(it) }, seen=state.seen, refreshing=state.refreshing)
                }
                tab == "friends" -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Heading("اقتراحات المتابعة"); TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("تحديث") } }
                    items(snapshot.people.filter { it.id != snapshot.me.id && !it.following }.sortedWith(compareByDescending<Person> { app.nabd.nativeapp.data.isAdminHandle(it.username) }.thenBy { it.name }), key = { "sug-${it.id}" }) { person ->
                        NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { profileId = person.id }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(person, 48)
                                Column(Modifier.weight(1f)) { PersonName(person, size = 15); Text("@${person.username}", color = Muted, fontSize = 12.sp) }
                                IconButton(onClick = { vm.thread(person) { id -> threadId = id } }, enabled = !state.busy) { Icon(Icons.Outlined.ChatBubbleOutline, "مراسلة ${person.name}", tint = Accent) }
                                TextButton(onClick = { vm.followUser(person, true) }, enabled = !state.busy) { Text("متابعة", fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                    item { Heading("الأصدقاء") }
                    items(snapshot.people.filter { it.id != snapshot.me.id && it.friend && (search.isBlank() || it.name.contains(search, true) || it.username.contains(search, true)) }, key = { "fr-${it.id}" }) { person ->
                        NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { profileId = person.id }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(person, 48)
                                Column(Modifier.weight(1f)) { PersonName(person, size = 15); Text("@${person.username}", color = Muted, fontSize = 12.sp) }
                                IconButton(onClick = { vm.thread(person) { id -> threadId = id } }, enabled = !state.busy) { Icon(Icons.Outlined.ChatBubbleOutline, "مراسلة ${person.name}", tint = Accent) }
                                TextButton(onClick = { vm.follow(person) }, enabled = !state.busy) { Text("إلغاء", fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                }
                tab == "me" -> ProfileScreen(snapshot.me, snapshot.posts.filter { it.author.id == snapshot.me.id }, snapshot.social.followers[snapshot.me.id] ?: 0, snapshot.social.following[snapshot.me.id] ?: 0, true, state.busy, Modifier.padding(padding),
                    canPin = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                    onEdit = { editing = true }, onCompose = { composer = true }, onChat = { chatList = true }, onFollow = { }, onCredentials = { credentials = true },
                    onProfile = { profileId = it.id }, onComments = { commentPost = it.id }, onLike = { vm.react(it) }, onDelete = { vm.delete(it.id) },
                    onReact = { post, kind -> vm.react(post, kind) }, onShare = share, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id }, onInvite = invite)
                tab == "menu" -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Heading("القائمة") }
                    item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { tab = "me" }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(snapshot.me, 48)
                            Column(Modifier.weight(1f)) { PersonName(snapshot.me, size = 15); Text("@${snapshot.me.username}", color = Muted, fontSize = 12.sp) }
                            Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                    } }
                    item { MenuCard("الرسائل", Icons.Outlined.ChatBubbleOutline) { chatList = true } }
                    item { MenuCard("التنبيهات", Icons.Outlined.Notifications) { tab = "alerts" } }
                    if (app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username)) item { MenuCard("الإدارة", Icons.Outlined.AdminPanelSettings) { adminOpen = true } }
                    item { MenuCard("تحديث التطبيق", Icons.Outlined.SystemUpdate) { vm.checkUpdates() } }
                    item { MenuCard("تعديل الحساب", Icons.Outlined.ManageAccounts) { editing = true } }
                    item { MenuCard("تسجيل الخروج", Icons.Outlined.Logout) { logoutConfirm = true } }
                }
                tab == "reels" -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Hint("الريلز ما كايناش دابا.") }
                tab == "alerts" -> {
                    val notes = snapshot.notifications
                    val alerts = remember(snapshot) { snapshot.threads.mapNotNull { conversation ->
                        val last = conversation.messages.lastOrNull()
                        if (last != null && last.sender != snapshot.me.id && !last.read) conversation to last else null
                    } }
                    LaunchedEffect(notes.size, notes.count { !it.read }) { if (notes.any { !it.read }) vm.readNotifications() }
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Heading("التنبيهات") }
                        if (notes.isEmpty()) item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("ما كايناش تنبيهات دابا", color = Muted) } } }
                        items(notes, key = { it.id }) { note -> NotificationCard(note) { note.postId?.let { commentPost = it } } }
                        item { Heading("رسائل غير مقروءة") }
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
    if (composer && snapshot != null) ComposerScreen(snapshot.me, state.busy, state.error, close = { composer = false },
        publish = { text, image, ratio -> vm.post(text, image, ratio) { composer = false } }, openStory = { storyComposer = true })
    if (editing && snapshot != null) ProfileDialog(snapshot.me, state.busy, state.error, close = { editing = false }, save = { name, username, bio, avatar, remove -> vm.profile(name, username, bio, avatar, remove) { editing = false } })
    val post = snapshot?.posts?.find { it.id == commentPost }
    if (post != null && snapshot != null) CommentsSheet(post, snapshot.me, state.busy, state.error, close = { commentPost = null },
        send = { text, image, reply, done -> vm.comment(post.id, text, image, reply, done) }, like = { vm.likeComment(post.id, it) },
        delete = { vm.deleteComment(post.id, it) }, onImage = { viewer = it }, react = { id, kind -> vm.reactComment(post.id, id, kind) })
    if (storyComposer) StoryCreator(state.busy, state.error, close = { storyComposer = false }, publish = { text, image -> vm.addStory(text, image) { storyComposer = false } })
    if (credentials) CredentialsDialog(state.busy, state.error, { credentials = false }) { current, newPassword, email -> vm.credentials(current, newPassword, email) { credentials = false } }
    if (viewer != null) ImageViewer(viewer!!) { viewer = null }
    val boostTarget = snapshot?.posts?.find { it.id == boostId }
    if (boostTarget != null && snapshot != null) BoostDialog(boostTarget, snapshot.me.points, app.nabd.nativeapp.data.BOOST_COST, state.busy,
        close = { boostId = null }, confirm = { vm.boost(boostTarget); boostId = null })
    if (logoutConfirm) AlertDialog(onDismissRequest = { logoutConfirm = false }, title = { Text("تسجيل الخروج؟") }, text = { Text("حسابك ومنشوراتك غيبقاو محفوظين. غتحتاج تسجّل الدخول من جديد.") }, confirmButton = { TextButton(onClick = { logoutConfirm = false; vm.logout() }) { Text("خروج") } }, dismissButton = { TextButton(onClick = { logoutConfirm = false }) { Text("إلغاء") } })
}

@Composable private fun NotificationCard(note: Notification, click: () -> Unit) {
    val icon = when (note.kind) { "pulse" -> Icons.Outlined.Favorite; "comment" -> Icons.Outlined.ChatBubbleOutline; "reply" -> Icons.AutoMirrored.Outlined.Reply; else -> Icons.Outlined.PersonAdd }
    val tint = if (note.kind == "pulse") AccentLight else Accent
    val label = when (note.kind) { "pulse" -> "تفاعل مع منشورك"; "comment" -> "علّق على منشورك"; "reply" -> "رد على تعليقك"; else -> "صار كيتابعك" }
    NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = click) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                PersonName(note.actor, size = 14)
                Text(label, color = TextMain, fontSize = 13.sp)
                Text(relativePostDate(note.date), color = Muted, fontSize = 11.sp)
            }
            if (!note.read) Box(Modifier.size(9.dp).clip(CircleShape).background(Accent).testTag("note-unread-${note.id}"))
        }
    }
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
