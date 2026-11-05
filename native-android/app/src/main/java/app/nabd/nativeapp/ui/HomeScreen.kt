package app.nabd.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
    val clipboard = LocalClipboardManager.current
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
    val invite: () -> Unit = {
        val code = snapshot?.me?.inviteCode.orEmpty()
        clipboard.setText(AnnotatedString(inviteLink(code)))
        android.widget.Toast.makeText(context, "تم نسخ رابط الدعوة", android.widget.Toast.LENGTH_SHORT).show()
    }
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
                chatList -> ChatListScreen(snapshot.threads, state.busy, Modifier.padding(padding), onRefresh = vm::refresh) { threadId = it.id }
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
                else -> AnimatedContent(targetState = tab, label = "nabd-tabs",
                    transitionSpec = { (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = .985f, animationSpec = tween(280))) togetherWith fadeOut(animationSpec = tween(280)) }) { target ->
                    when (target) {
                        "friends" -> FriendsScreen(snapshot, state.busy, search, Modifier.padding(padding), onRefresh = vm::refresh,
                            onProfile = { profileId = it.id }, onChat = { person -> vm.thread(person) { id -> threadId = id } }, onFollow = { person, follow -> vm.followUser(person, follow) })
                        "menu" -> MenuScreen(snapshot.me, state.busy, Modifier.padding(padding),
                            onMe = { tab = "me" }, onChats = { chatList = true }, onAlerts = { tab = "alerts" }, onAdmin = { adminOpen = true },
                            onUpdate = vm::checkUpdates, onEdit = { editing = true }, onLogout = { logoutConfirm = true })
                        "me" -> ProfileScreen(snapshot.me, snapshot.posts.filter { it.author.id == snapshot.me.id }, snapshot.social.followers[snapshot.me.id] ?: 0, snapshot.social.following[snapshot.me.id] ?: 0, true, state.busy, Modifier.padding(padding),
                            canPin = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                            onEdit = { editing = true }, onCompose = { composer = true }, onChat = { chatList = true }, onFollow = { }, onCredentials = { credentials = true },
                            onProfile = { profileId = it.id }, onComments = { commentPost = it.id }, onLike = { vm.react(it) }, onDelete = { vm.delete(it.id) },
                            onReact = { post, kind -> vm.react(post, kind) }, onShare = share, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id }, onInvite = invite)
                        "alerts" -> AlertsScreen(snapshot, state.busy, Modifier.padding(padding), onRead = vm::readNotifications, onOpen = { commentPost = it }, onChat = { threadId = it })
                        else -> FeedContent(snapshot, state.stories, state.busy, filter, search, Modifier.padding(padding),
                            onFilter={ filter=it }, onCompose={ composer=true }, onRefresh=vm::refresh,
                            onNewStory={ storyComposer=true }, onStory={ group -> storyOwner = group.owner.id },
                            onLike={ vm.react(it) }, onComments={ commentPost=it.id }, onDelete={ vm.delete(it.id) },
                            onReact={ post,kind -> vm.react(post,kind) }, onShare=share, onProfile={ profileId=it.id }, onPin={ post,pinned -> vm.pin(post,pinned) }, onBoost={ boostId=it.id }, onImage={ viewer=it },
                            announcement=snapshot.announcements.firstOrNull { it.id !in state.dismissed }, onDismissAnnouncement={ vm.dismissAnnouncement(it) }, seen=state.seen, refreshing=state.refreshing)
                    }
                }
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
    if (logoutConfirm) GlassConfirm("تسجيل الخروج؟", "حسابك ومنشوراتك غيبقاو محفوظين. غتحتاج تسجّل الدخول من جديد.", "خروج",
        onConfirm = { logoutConfirm = false; vm.logout() }, close = { logoutConfirm = false }, danger = true)
}

@Composable private fun Heading(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Black)
        if (action != null) Box(Modifier.padding(start = 8.dp)) { action() }
    }
}

/** Suggestions never repeat an account that is already a friend. */
@Composable private fun FriendsScreen(snapshot: Snapshot, busy: Boolean, search: String, modifier: Modifier = Modifier,
    onRefresh: () -> Unit, onProfile: (Person) -> Unit, onChat: (Person) -> Unit, onFollow: (Person, Boolean) -> Unit) {
    val suggestions = remember(snapshot.people) {
        snapshot.people.filter { it.id != snapshot.me.id && !it.friend && !it.following }
            .sortedWith(compareByDescending<Person> { app.nabd.nativeapp.data.isAdminHandle(it.username) }.thenBy { it.name })
    }
    val friends = remember(snapshot.people, search) {
        snapshot.people.filter { it.id != snapshot.me.id && it.friend && (search.isBlank() || it.name.contains(search, true) || it.username.contains(search, true)) }
            .sortedWith(compareByDescending<Person> { app.nabd.nativeapp.data.isAdminHandle(it.username) }.thenBy { it.name })
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("اقتراحات المتابعة") { GlassCircle(Icons.Outlined.Refresh, "تحديث الاقتراحات", onRefresh, enabled = !busy, size = 38.dp, tint = Muted) } }
        if (suggestions.isEmpty()) item { EmptyState(Icons.Outlined.PersonSearch, "ما كايناش اقتراحات دابا", "كل الأعضاء اللي كتعرفهم غيبانو هنا منين يتسجّلو.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(suggestions, key = { "sug-${it.id}" }) { person -> PersonRow(person, busy, false, onProfile, onChat, onFollow, modifier = Modifier.padding(horizontal = 12.dp)) }
        item { Heading("الأصدقاء") }
        if (friends.isEmpty()) item { EmptyState(Icons.Outlined.GroupAdd, "ما كايناش أصدقاء بعد", "تابع حساباً وصافي يتبعك غيولي صديق.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(friends, key = { "fr-${it.id}" }) { person -> PersonRow(person, busy, true, onProfile, onChat, onFollow, modifier = Modifier.padding(horizontal = 12.dp)) }
    }
}

/** One person row: glass card, circular message button and the follow pill. */
@Composable private fun PersonRow(person: Person, busy: Boolean, following: Boolean, onProfile: (Person) -> Unit, onChat: (Person) -> Unit,
    onFollow: (Person, Boolean) -> Unit, modifier: Modifier = Modifier) {
    NabdCard(modifier = modifier, onClick = { onProfile(person) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(person, 54)
            Column(Modifier.weight(1f)) {
                PersonName(person, size = 15)
                Text("@${person.username}", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            GlassCircle(Icons.AutoMirrored.Outlined.Send, "مراسلة ${person.name}", { onChat(person) }, enabled = !busy, size = 40.dp, tint = Accent)
            FollowPill(following, busy) { onFollow(person, !following) }
        }
    }
}

@Composable private fun ChatListScreen(threads: List<app.nabd.nativeapp.data.Thread>, busy: Boolean, modifier: Modifier = Modifier,
    onRefresh: () -> Unit, open: (app.nabd.nativeapp.data.Thread) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("الرسائل") { GlassCircle(Icons.Outlined.Refresh, "تحديث الرسائل", onRefresh, enabled = !busy, size = 38.dp, tint = Muted) } }
        if (threads.isEmpty()) item { EmptyState(Icons.Outlined.Forum, "ما كايناش محادثات", "صيفط رسالة لصديق وبدا الحوار من هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(threads, key = { it.id }) { conversation ->
            val last = conversation.messages.lastOrNull()
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { open(conversation) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(conversation.other, 52)
                    Column(Modifier.weight(1f)) {
                        PersonName(conversation.other, size = 15)
                        Text(if (last == null) "ما كايناش رسائل بعد" else if (last.deleted) "رسالة محذوفة" else last.text.ifBlank { "صورة" },
                            color = Muted, fontSize = 12.5.sp, maxLines = 1)
                    }
                    Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable private fun AlertsScreen(snapshot: Snapshot, busy: Boolean, modifier: Modifier = Modifier, onRead: () -> Unit,
    onOpen: (String) -> Unit, onChat: (String) -> Unit) {
    val notes = snapshot.notifications
    val alerts = remember(snapshot) { snapshot.threads.mapNotNull { conversation ->
        val last = conversation.messages.lastOrNull()
        if (last != null && last.sender != snapshot.me.id && !last.read) conversation to last else null
    } }
    LaunchedEffect(notes.size, notes.count { !it.read }) { if (notes.any { !it.read }) onRead() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("التنبيهات") }
        if (notes.isEmpty()) item { EmptyState(Icons.Outlined.NotificationsNone, "ما كايناش تنبيهات دابا", "منين شي حد يتفاعل مع منشوراتك غيجيوك هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(notes, key = { it.id }) { note -> NotificationCard(note) { note.postId?.let(onOpen) } }
        item { Heading("رسائل غير مقروءة") }
        items(alerts, key = { it.first.id }) { (conversation, message) ->
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { onChat(conversation.id) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(conversation.other, 52)
                    Column(Modifier.weight(1f)) {
                        Text("رسالة جديدة من ${conversation.other.name}", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(if (message.deleted) "رسالة محذوفة" else message.text.ifBlank { "صورة" }, color = Muted, fontSize = 12.5.sp, maxLines = 1)
                    }
                    Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable private fun MenuScreen(me: Person, busy: Boolean, modifier: Modifier = Modifier, onMe: () -> Unit, onChats: () -> Unit,
    onAlerts: () -> Unit, onAdmin: () -> Unit, onUpdate: () -> Unit, onEdit: () -> Unit, onLogout: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("القائمة") }
        item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = onMe) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(me, 54)
                Column(Modifier.weight(1f)) { PersonName(me, size = 16); Text("@${me.username}", color = Muted, fontSize = 12.sp, maxLines = 1) }
                Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        } }
        item { MenuCard("الرسائل", Icons.Outlined.ChatBubbleOutline, onChats) }
        item { MenuCard("التنبيهات", Icons.Outlined.Notifications, onAlerts) }
        if (app.nabd.nativeapp.data.isAdminHandle(me.username)) item { MenuCard("الإدارة", Icons.Outlined.AdminPanelSettings, onAdmin) }
        item { MenuCard("تحديث التطبيق", Icons.Outlined.SystemUpdate, onUpdate) }
        item { MenuCard("تعديل الحساب", Icons.Outlined.ManageAccounts, onEdit) }
        item { MenuCard("تسجيل الخروج", Icons.Outlined.Logout, onLogout, tint = AccentLight) }
    }
}

@Composable private fun MenuCard(text: String, icon: ImageVector, onClick: () -> Unit, tint: Color = Accent) {
    NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconPlate(icon, tint = tint)
            Text(text, Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
            Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable private fun NotificationCard(note: app.nabd.nativeapp.data.Notification, click: () -> Unit) {
    val kind = when (note.kind) { "pulse" -> "like"; "comment" -> "comment"; "reply" -> "reply"; else -> "follow" }
    val icon = when (kind) { "comment" -> Icons.Outlined.ChatBubbleOutline; "reply" -> Icons.AutoMirrored.Outlined.Reply; "follow" -> Icons.Outlined.PersonAdd; else -> Icons.Outlined.Favorite }
    val tint = if (kind == "like") AccentLight else Accent
    val label = when (kind) { "like" -> "تفاعل مع منشورك"; "comment" -> "علّق على منشورك"; "reply" -> "رد على تعليقك"; else -> "صار كيتابعك" }
    NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = click) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                if (kind == "like") ReactionGlyph("like", size = 24.dp) else Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                PersonName(note.actor, size = 14)
                Text(label, color = TextMain, fontSize = 13.sp)
                Text(relativePostDate(note.date), color = Muted, fontSize = 11.sp)
            }
            if (!note.read) Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Accent).testTag("note-unread-${note.id}"))
        }
    }
}
