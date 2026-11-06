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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var accountsOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reactors by remember { mutableStateOf<String?>(null) }
    var roster by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val snapshot = state.snapshot
    val savedAccounts by vm.accounts.collectAsStateWithLifecycle()
    val invite: () -> Unit = {
        val code = snapshot?.me?.inviteCode.orEmpty()
        clipboard.setText(AnnotatedString(inviteLink(code)))
        android.widget.Toast.makeText(context, "تم نسخ رابط الدعوة", android.widget.Toast.LENGTH_SHORT).show()
    }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, state.signedIn) {
        if (!state.signedIn) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(5000); vm.poll() } }
    }
    BackHandler(threadId != null || chatList || searching || profileId != null || adminOpen || storyOwner != null || settingsOpen || reactors != null || roster != null || tab != "home") {
        when { roster != null -> roster = null; reactors != null -> reactors = null; settingsOpen -> settingsOpen = false; storyOwner != null -> storyOwner = null; threadId != null -> threadId = null; chatList -> chatList = false; profileId != null -> profileId = null; adminOpen -> adminOpen = false; searching -> { searching = false; search = "" }; else -> tab = "home" }
    }
    Scaffold(containerColor = Canvas,
        topBar = { HomeHeader(state.busy, state.error, searching, search, { search=it }, { searching=!searching }, { composer=true }, { chatList=!chatList; threadId=null }, vm::refresh,
            alerts = snapshot?.notifications?.count { !it.read } ?: 0, onAlerts = { tab = "alerts" }) },
        bottomBar = { HomeDock(tab) { tab=it; chatList=false; threadId=null } }
        ) { padding ->
        if (snapshot == null && tab == "me") ProfileShimmer(Modifier.padding(padding))
        else if (snapshot == null) ShimmerFeed(Modifier.padding(padding)) else {
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
                    react = { id, kind -> vm.reactMessage(thread.id, id, kind) }, read = { vm.readThread(thread.id) }, refresh = vm::refresh,
                    sendVoice = { uri, seconds, bars, done -> vm.sendVoice(thread.id, uri, seconds, bars, done) },
                    onProfile = { profileId = it.id }, onRetry = { message -> vm.retryMessage(thread.id, message) },
                    muted = thread.id in snapshot.muted, onMute = { vm.setMuted(thread.id, it) }, onClear = { vm.clearThread(thread.id) },
                    onBlock = { vm.setBlocked(thread.other, true) }, onDeleteForMe = { vm.deleteMessageForMe(thread.id, it) },
                    onDeleteForAll = { vm.deleteMessage(thread.id, it) })
                chatList -> ChatListScreen(snapshot.threads, state.busy, Modifier.padding(padding), onRefresh = vm::refresh, open = { threadId = it.id },
                    onProfile = { profileId = it.id })
                profileId != null -> {
                    val target = snapshot.people.find { it.id == profileId } ?: snapshot.posts.firstOrNull { it.author.id == profileId }?.author ?: snapshot.me
                    val own = target.id == snapshot.me.id
                    ProfileScreen(target, snapshot.posts.filter { it.author.id == target.id }, snapshot.social.followers[target.id] ?: 0, snapshot.social.following[target.id] ?: 0, own, state.busy, Modifier.padding(padding),
                        canPin = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                        onBack = { profileId = null }, onEdit = { editing = true }, onCompose = { composer = true },
                        onChat = { vm.thread(target) { id -> threadId = id } }, onFollow = { vm.followUser(target, !target.following) }, onCredentials = { credentials = true },
                        onProfile = { profileId = it.id }, onComments = { commentPost = it.id }, onLike = { vm.react(it) }, onDelete = { vm.delete(it.id) },
                        onReact = { post, kind -> vm.react(post, kind) }, onShare = share, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id },
                        stories = snapshot.stories.find { it.owner.id == target.id }?.items.orEmpty(), polls = snapshot.polls, onVote = { post, option -> vm.votePoll(post.id, option) },
                        onRetryPost = { post -> vm.retryPost(post) }, onReactors = { post -> reactors = post.id },
                        highlights = snapshot.highlights[target.id].orEmpty(),
                        onHighlight = { group -> group.items.firstOrNull()?.let { highlight = target.id to group.id } },
                        onCreateHighlight = { title, ids -> vm.createHighlight(title, ids) { } }, onRemoveHighlight = vm::removeHighlight,
                        onFollowers = { roster = "متابعو ${target.name}" to snapshot.social.edges.filter { (_, ids) -> target.id in ids }.keys.toList() },
                        onFollowing = { roster = "الذين يتابعهم ${target.name}" to snapshot.social.edges[target.id].orEmpty() })
                }
                else -> AnimatedContent(targetState = tab, label = "nabd-tabs",
                    transitionSpec = { (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = .985f, animationSpec = tween(280))) togetherWith fadeOut(animationSpec = tween(280)) }) { target ->
                    when (target) {
                        "friends" -> FriendsScreen(snapshot, state.busy, search, Modifier.padding(padding), onRefresh = vm::refresh,
                            onProfile = { profileId = it.id }, onChat = { person -> vm.thread(person) { id -> threadId = id } }, onFollow = { person, follow -> vm.followUser(person, follow) })
                        "menu" -> MenuScreen(snapshot.me, state.busy, Modifier.padding(padding),
                            onMe = { tab = "me" }, onChats = { chatList = true }, onAlerts = { tab = "alerts" }, onAdmin = { adminOpen = true },
                            onUpdate = vm::checkUpdates, onEdit = { editing = true }, onLogout = { accountsOpen = true },
                            settings = snapshot.settings, onSettings = { settingsOpen = true }, onSettingsChange = vm::saveSettings)
                        "me" -> ProfileScreen(snapshot.me, snapshot.posts.filter { it.author.id == snapshot.me.id }, snapshot.social.followers[snapshot.me.id] ?: 0, snapshot.social.following[snapshot.me.id] ?: 0, true, state.busy, Modifier.padding(padding),
                            canPin = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                            onEdit = { editing = true }, onCompose = { composer = true }, onChat = { chatList = true }, onFollow = { }, onCredentials = { credentials = true },
                            onProfile = { profileId = it.id }, onComments = { commentPost = it.id }, onLike = { vm.react(it) }, onDelete = { vm.delete(it.id) },
                            onReact = { post, kind -> vm.react(post, kind) }, onShare = share, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id }, onInvite = invite,
                            dark = NabdThemeState.dark, onDark = { vm.saveSettings(snapshot.settings.copy(theme = if (it) "dark" else "light")) },
                            onSettings = { settingsOpen = true }, stories = storyGroups.find { it.owner.id == snapshot.me.id }?.items.orEmpty(),
                            polls = snapshot.polls, onVote = { post, option -> vm.votePoll(post.id, option) },
                            highlights = snapshot.highlights[snapshot.me.id].orEmpty(),
                            onHighlight = { group -> group.items.firstOrNull()?.let { highlight = snapshot.me.id to group.id } },
                            onCreateHighlight = { title, ids -> vm.createHighlight(title, ids) { } },
                            onRemoveHighlight = vm::removeHighlight,
                            onFollowers = { roster = "متابعوك" to snapshot.social.edges.filter { (_, ids) -> snapshot.me.id in ids }.keys.toList() },
                            onFollowing = { roster = "الذين تتابعهم" to snapshot.social.edges[snapshot.me.id].orEmpty() },
                            onReactors = { post -> reactors = post.id }, onRetryPost = { post -> vm.retryPost(post) })
                        "alerts" -> AlertsScreen(snapshot, state.busy, Modifier.padding(padding), onRead = vm::readNotifications, onOpen = { commentPost = it },
                            onChat = { threadId = it }, onProfile = { profileId = it.id })
                        else -> FeedContent(snapshot, state.stories, state.busy, filter, search, Modifier.padding(padding),
                            onFilter={ filter=it }, onCompose={ composer=true }, onRefresh=vm::refresh,
                            onNewStory={ storyComposer=true }, onStory={ group -> storyOwner = group.owner.id },
                            onLike={ vm.react(it) }, onComments={ commentPost=it.id }, onDelete={ vm.delete(it.id) },
                            onReact={ post,kind -> vm.react(post,kind) }, onShare=share, onProfile={ profileId=it.id }, onPin={ post,pinned -> vm.pin(post,pinned) }, onBoost={ boostId=it.id }, onImage={ viewer=it },
                            polls=snapshot.polls, onVote={ post,option -> vm.votePoll(post.id, option) }, onRetryPost={ post -> vm.retryPost(post) },
                            onProfile={ author -> profileId = author.id },
                            announcement=snapshot.announcements.firstOrNull { it.id !in state.dismissed }, onDismissAnnouncement={ vm.dismissAnnouncement(it) }, seen=state.seen, refreshing=state.refreshing)
                    }
                }
            }
        }
    }
    val opened = highlight?.let { (owner, id) -> snapshot?.highlights?.get(owner)?.find { it.id == id } }
    if (opened != null && snapshot != null) {
        val owner = snapshot.people.find { it.id == highlight!!.first } ?: snapshot.me
        StoryViewerScreen(listOf(StoryGroup(owner, opened.items.map { item -> Story(item.id, owner, item.text, item.image, item.date) }, true)),
            owner.id, null, snapshot.me, state.busy, state.error, onClose = { highlight = null }, onSeen = { }, onDelete = { }, onReply = { _, _ -> })
    }
    if (composer && snapshot != null) ComposerScreen(snapshot.me, state.busy, state.error, close = { composer = false },
        publish = { text, image, ratio, privacy -> vm.post(text, image, ratio, privacy) { composer = false } }, openStory = { storyComposer = true },
        friends = snapshot.people.filter { it.id != snapshot.me.id },
        onSendReel = { person, text -> vm.sendReel(person, text); android.widget.Toast.makeText(context, "وصلات الريلز ل${person.name}", android.widget.Toast.LENGTH_SHORT).show() },
        publishPoll = { question, options, privacy -> vm.createPoll(question, options, 3, privacy) { composer = false } })
    if (editing && snapshot != null) ProfileDialog(snapshot.me, state.busy, state.error, close = { editing = false }, save = { name, username, bio, avatar, remove -> vm.profile(name, username, bio, avatar, remove) { editing = false } })
    val post = snapshot?.posts?.find { it.id == commentPost }
    if (post != null && snapshot != null) CommentsSheet(post, snapshot.me, state.busy, state.error, close = { commentPost = null },
        send = { text, image, reply, done -> vm.comment(post.id, text, image, reply, done) },
        like = { comment -> vm.likeComment(post, comment, !comment.likedByMe) },
        delete = { vm.deleteComment(post.id, it) }, onImage = { viewer = it },
        react = { id, kind -> post.comments.find { it.id == id }?.let { vm.reactComment(post, it, kind) } },
        onRetry = { comment -> vm.retryComment(post, comment) }, onProfile = { profileId = it.id })
    if (storyComposer) StoryCreator(state.busy, state.error, close = { storyComposer = false }, publish = { text, image -> vm.addStory(text, image) { storyComposer = false } })
    if (credentials) CredentialsDialog(state.busy, state.error, { credentials = false }) { current, newPassword, email -> vm.credentials(current, newPassword, email) { credentials = false } }
    if (viewer != null) ImageViewer(viewer!!) { viewer = null }
    val boostTarget = snapshot?.posts?.find { it.id == boostId }
    if (boostTarget != null && snapshot != null) BoostDialog(boostTarget, snapshot.me.points, app.nabd.nativeapp.data.BOOST_COST, state.busy,
        close = { boostId = null }, confirm = { vm.boost(boostTarget); boostId = null })
    val reactorPost = snapshot?.posts?.find { it.id == reactors }
    if (reactorPost != null && snapshot != null) ReactorsSheet(reactorPost, snapshot.people, snapshot.me, close = { reactors = null }) { profileId = it.id }
    val list = roster
    if (list != null && snapshot != null) {
        val names = list.second.mapNotNull { id -> if (id == snapshot.me.id) snapshot.me else snapshot.people.find { it.id == id } }
        PeopleListScreen(list.first, names, Modifier, state.busy, onBack = { roster = null }, onProfile = { profileId = it.id },
            onFollow = { person, follow -> vm.followUser(person, follow) }, onChat = { person -> vm.thread(person) { id -> threadId = id } })
    }
    if (settingsOpen && snapshot != null) {
        val official = snapshot.people.find { it.username == app.nabd.nativeapp.data.OFFICIAL_HANDLE }
        SettingsScreen(snapshot.me, snapshot.people, snapshot.settings, snapshot.blocked, snapshot.devices,
            snapshot.posts, snapshot.notifications, snapshot.stories.find { it.owner.id == snapshot.me.id }?.items ?: emptyList(), state.busy, state.error,
            app.nabd.nativeapp.BuildConfig.VERSION_NAME,
            onBack = { settingsOpen = false }, onSettings = vm::saveSettings, onBlock = { person, block -> vm.setBlocked(person, block) },
            onLogoutDevice = vm::logoutDevice, onLogoutAll = vm::logoutOtherDevices,
            onDeactivate = { password -> vm.deactivate(password) }, onDelete = { password -> vm.deleteAccount(password) },
            onLogout = { settingsOpen = false; vm.logout() },
            onCredentials = { current, newPassword, email -> vm.credentials(current, newPassword, email) { settingsOpen = false } },
            onProfile = { editing = true },
            onReport = { text -> official?.let { target -> vm.thread(target) { id ->
                vm.send(id, "بلاغ من الإعدادات: $text", null, null, 1.0) {
                    settingsOpen = false; android.widget.Toast.makeText(context, "وصل البلاغ", android.widget.Toast.LENGTH_SHORT).show() } } } },
            onContact = { settingsOpen = false; official?.let { target -> vm.thread(target) { id -> threadId = id } } })
    }
    if (accountsOpen && snapshot != null) AccountsSheet(snapshot.me, savedAccounts, state.busy, state.error,
        close = { accountsOpen = false }, onSwitch = { id -> accountsOpen = false; vm.switchAccount(id) },
        onForget = vm::forgetAccount, onLogout = { accountsOpen = false; vm.logout() }, onAdd = { accountsOpen = false; vm.addAccount() })
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
    onRefresh: () -> Unit, open: (app.nabd.nativeapp.data.Thread) -> Unit, onProfile: (Person) -> Unit = {}) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("الرسائل") { GlassCircle(Icons.Outlined.Refresh, "تحديث الرسائل", onRefresh, enabled = !busy, size = 38.dp, tint = Muted) } }
        if (threads.isEmpty()) item { EmptyState(Icons.Outlined.Forum, "ما كايناش محادثات", "صيفط رسالة لصديق وبدا الحوار من هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(threads, key = { it.id }) { conversation ->
            val last = conversation.messages.lastOrNull()
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { open(conversation) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.pressClick { onProfile(conversation.other) }) { Avatar(conversation.other, 52) }
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
    onOpen: (String) -> Unit, onChat: (String) -> Unit, onProfile: (Person) -> Unit = {}) {
    val notes = snapshot.notifications
    val alerts = remember(snapshot) { snapshot.threads.mapNotNull { conversation ->
        val last = conversation.messages.lastOrNull()
        if (last != null && last.sender != snapshot.me.id && !last.read) conversation to last else null
    } }
    LaunchedEffect(notes.size, notes.count { !it.read }) { if (notes.any { !it.read }) onRead() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("التنبيهات") }
        if (notes.isEmpty()) item { EmptyState(Icons.Outlined.NotificationsNone, "ما كايناش تنبيهات دابا", "منين شي حد يتفاعل مع منشوراتك غيجيوك هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
        items(notes, key = { it.id }) { note -> NotificationCard(note, { note.postId?.let(onOpen) }, onProfile) }
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
    onAlerts: () -> Unit, onAdmin: () -> Unit, onUpdate: () -> Unit, onEdit: () -> Unit, onLogout: () -> Unit,
    settings: UserSettings = UserSettings(), onSettings: () -> Unit = {}, onSettingsChange: (UserSettings) -> Unit = {}) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("القائمة") }
        item { NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = onMe) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(me, 54)
                Column(Modifier.weight(1f)) { PersonName(me, size = 16); Text("@${me.username}", color = Muted, fontSize = 12.sp, maxLines = 1) }
                Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        } }
        item {
            var open by remember { mutableStateOf(false) }
            NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = { open = !open }) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconPlate(Icons.Outlined.Settings)
                        Text("الإعدادات والخصوصية", Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = Muted, modifier = Modifier.size(22.dp))
                    }
                    if (open) Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        MenuRow("كل الإعدادات", Icons.Outlined.Tune, onSettings)
                        MenuToggle("الوضع الليلي", Icons.Outlined.ModeNight, NabdThemeState.dark, enabled = !busy) { onSettingsChange(settings.copy(theme = if (it) "dark" else "light")) }
                        MenuToggle("الوضع الهادئ", Icons.Outlined.NotificationsOff, settings.quiet, enabled = !busy) { onSettingsChange(settings.copy(quiet = it)) }
                    }
                }
            }
        }
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

@Composable private fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit, tint: Color = Accent) {
    Row(Modifier.fillMaxWidth().pressClick(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(text, Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        Icon(Icons.Outlined.ChevronLeft, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

/** The quick switches of the menu: dark mode and the quiet mode. */
@Composable private fun MenuToggle(text: String, icon: ImageVector, value: Boolean, enabled: Boolean = true, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
        Text(text, Modifier.weight(1f), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        Switch(checked = value, onCheckedChange = onValue, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = Color.White, uncheckedTrackColor = Outline))
    }
}

@Composable private fun NotificationCard(note: app.nabd.nativeapp.data.Notification, click: () -> Unit, onProfile: (Person) -> Unit = {}) {
    val kind = when (note.kind) { "pulse" -> "like"; "comment" -> "comment"; "reply" -> "reply"; else -> "follow" }
    val icon = when (kind) { "comment" -> Icons.Outlined.ChatBubbleOutline; "reply" -> Icons.AutoMirrored.Outlined.Reply; "follow" -> Icons.Outlined.PersonAdd; else -> Icons.Outlined.Favorite }
    val tint = if (kind == "like") AccentLight else Accent
    val label = when (kind) { "like" -> "تفاعل مع منشورك"; "comment" -> "علّق على منشورك"; "reply" -> "رد على تعليقك"; else -> "صار كيتابعك" }
    NabdCard(modifier = Modifier.padding(horizontal = 12.dp), onClick = click) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                if (kind == "like") ReactionGlyph("like", size = 24.dp) else Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).pressClick { onProfile(note.actor) }) {
                PersonName(note.actor, size = 14)
                Text(label, color = TextMain, fontSize = 13.sp)
                Text(relativePostDate(note.date), color = Muted, fontSize = 11.sp)
            }
            if (!note.read) Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Accent).testTag("note-unread-${note.id}"))
        }
    }
}
