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
import androidx.compose.ui.geometry.Offset
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
import app.nabd.nativeapp.data.tikTokPageUrl
import kotlinx.coroutines.launch

/** A followers/following list opened from a profile: whose list, which kind, and the real accounts behind it. */
private data class RosterState(val title: String, val personId: String?, val kind: String, val realIds: List<String>)

/**
 * @param openUrl كيحل رابط فالمتصفح (الصورة الأصلية ديال Pinterest، ولا البحث العام).
 * @param linkGoogle كيطلق ورقة Google؛ كتستعمل غير من داخل `onLink` لإعدادات الربط.
 */
@Composable fun HomeScreen(state: AppState, vm: NabdViewModel, openUrl: (String) -> Unit = {}, linkGoogle: (() -> Unit) -> Unit = { it() }) {
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
    var commentReactors by remember { mutableStateOf<Comment?>(null) }
    var roster by remember { mutableStateOf<RosterState?>(null) }
    var reelComposer by remember { mutableStateOf(false) }
    var reelToForward by remember { mutableStateOf<ReelItem?>(null) }
    var editingPost by remember { mutableStateOf<FeedPost?>(null) }
    // One floating-hearts overlay for the whole screen, above everything:
    // every tapped heart (logo or a post's نبضة) spawns its burst here,
    // at the tap's own spot, so the hearts are never clipped by the header,
    // a post card, or any parent.
    var rootHearts by remember { mutableStateOf(emptyList<HeartParticle>()) }
    val burstHearts: (Offset) -> Unit = { spawn -> rootHearts = rootHearts + heartBurst(System.nanoTime(), spawn) }
    // Where the logo heart sits, in window pixels — a plain holder, not
    // state, so the header never recomposes just to track it.
    val logoPos = remember { arrayOf(Offset.Zero) }
    val updates by vm.updates.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    // Tapping a dock tab lands directly on that tab's root from anywhere:
    // every nested screen, sheet and overlay is cleared first, so no back
    // presses are ever needed and the switch is immediate and smooth.
    val goToTab: (String) -> Unit = { next ->
        storyOwner = null; adminOpen = false; profileId = null; threadId = null; chatList = false
        commentPost = null; reactors = null; commentReactors = null; roster = null; settingsOpen = false; credentials = false
        accountsOpen = false; composer = false; editing = false; storyComposer = false; reelComposer = false
        viewer = null; highlight = null; boostId = null; searching = false; search = ""
        tab = next
    }
    // A generated post shown with my local reaction on it (likes +1, heart filled).
    val adjustFakePost: (FeedPost) -> FeedPost = { post ->
        if (!FakePeople.isFakePostId(post.id)) post else {
            val local = state.fakeReactions[post.id]
            if (local == null) post else post.copy(myReaction = local, likes = post.likes + 1,
                breakdown = post.breakdown + (local to (post.breakdown[local] ?: 0) + 1))
        }
    }
    // A generated comment shown with my local reaction on it (likes +1).
    val adjustFakeComment: (FeedPost, Comment) -> Comment = { post, comment ->
        if (!FakePeople.isFakeCommentId(comment.id)) comment else {
            val local = state.fakeCommentReactions["${post.id}:${comment.id}"]
            if (local == null) comment else comment.copy(myReaction = local, likedByMe = local == "like", likes = comment.likes + 1,
                reactions = comment.reactions + (local to (comment.reactions[local] ?: 0) + 1))
        }
    }
    val savedAccounts by vm.accounts.collectAsStateWithLifecycle()
    val invite: () -> Unit = {
        val code = snapshot?.me?.inviteCode.orEmpty()
        clipboard.setText(AnnotatedString(inviteLink(code)))
        android.widget.Toast.makeText(context, "تم نسخ رابط الدعوة", android.widget.Toast.LENGTH_SHORT).show()
    }
    // A new account meets the official account first: its profile opens
    // right after registration so the user can follow it with their own tap.
    LaunchedEffect(state.suggestOfficial, snapshot) {
        if (!state.suggestOfficial) return@LaunchedEffect
        profileId = snapshot?.people?.find { it.username == app.nabd.nativeapp.data.OFFICIAL_HANDLE }?.id
        vm.dismissOfficialSuggestion()
    }
    val lifecycle = LocalLifecycleOwner.current
    // The poll keeps running on every screen (feed, profile, followers,
    // comments): it is launched at the HomeScreen root and never stops
    // while the app is open. 2.9.5: every 3s, and the light snapshot paints
    // instantly without waiting on the network, so refresh feels instant
    // everywhere.
    LaunchedEffect(lifecycle, state.signedIn) {
        if (!state.signedIn) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(3000); vm.poll() } }
    }
    BackHandler(threadId != null || chatList || searching || profileId != null || adminOpen || storyOwner != null || settingsOpen || reactors != null || commentReactors != null || roster != null || tab != "home") {
        when { roster != null -> roster = null; commentReactors != null -> commentReactors = null; reactors != null -> reactors = null; settingsOpen -> settingsOpen = false; storyOwner != null -> storyOwner = null; profileId != null -> profileId = null; threadId != null -> threadId = null; chatList -> chatList = false; adminOpen -> adminOpen = false; searching -> { searching = false; search = "" }; else -> tab = "home" }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = Canvas,
        topBar = { if (tab != "reels") HomeHeader(state.busy, state.error, searching, search, { search=it }, { searching=!searching }, { composer=true },
            { if (chatList) chatList = false else { goToTab("home"); chatList = true } }, vm::refresh,
            alerts = snapshot?.notifications?.count { !it.read } ?: 0, onAlerts = { goToTab("alerts") },
            updateAvailable = updates.release != null, onUpdateBadge = vm::downloadUpdateNow,
            onLogoTap = { rootHearts = rootHearts + heartBurst(System.nanoTime(), logoPos[0], down = true) },
            onLogoPosition = { logoPos[0] = it }) },
        bottomBar = { HomeDock(tab, goToTab) }
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
                roster != null -> {
                    // Full-screen destination, never an overlay: while the
                    // list is open the profile behind it is not composed.
                    val list = roster!!
                    val fakePerson = list.personId?.let { FakePeople.personFromFakeId(it) }
                    val realPeople = list.realIds.mapNotNull { id -> if (id == snapshot.me.id) snapshot.me else snapshot.people.find { it.id == id } }
                    // Generated rows: followers of any boosted account (or of a
                    // generated person), and the accounts a generated person follows.
                    val (generated, generatedTotal) = when (list.kind) {
                        "followers" -> {
                            val total = if (fakePerson != null) FakePeople.followerCount(fakePerson)
                                else snapshot.social.followers[list.personId] ?: 0
                            if (list.personId == null) emptyList<Person>() to 0
                            else FakePeople.followersForPerson(list.personId, total, realPeople.size)
                        }
                        else -> if (fakePerson != null) FakePeople.followingForPerson(fakePerson) else emptyList<Person>() to 0
                    }
                    val total = realPeople.size + generatedTotal
                    val names = (realPeople + generated).map { person ->
                        if (person.fakeSeed != null) person.copy(following = state.fakeFollowing.contains(person.id)) else person
                    }
                    PeopleListScreen(list.title, names, Modifier.padding(padding), state.busy, onBack = { roster = null }, onProfile = { roster = null; profileId = it.id },
                        onFollow = { person, follow -> if (person.fakeSeed != null) vm.toggleFakeFollow(person.id) else vm.followUser(person, follow) },
                        onChat = { person -> if (person.fakeSeed == null) vm.thread(person) { id -> threadId = id } }, total = total)
                }
                storyOwner != null -> StoryViewerScreen(storyGroups, storyOwner!!, null, snapshot.me, state.busy, state.error,
                    onClose = { storyOwner = null }, onSeen = vm::seeStory, onDelete = { vm.removeStory(it) { } }, onReply = { person, text -> vm.storyReply(person, text) })
                adminOpen && app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username) -> AdminScreen(snapshot, state.busy, Modifier.padding(padding), onBack = { adminOpen = false },
                    onVerify = { person, verified -> vm.verify(person, verified) },
                    onRestrict = { person, banned, restricted -> vm.restrict(person, banned, restricted) },
                    onPin = { post, pinned -> vm.pin(post, pinned) },
                    onBoostFollowers = { person, count -> vm.boostFollowers(person, count) },
                    onBoostReactions = { postId, count -> vm.boostReactions(postId, count) },
                    onBoostUnified = { postId, kind, count -> vm.boostUnified(postId, kind, count) },
                    onAddComment = { postId, text -> vm.adminComment(postId, text) },
                    onAnnounce = { text -> vm.announce(text) { android.widget.Toast.makeText(context, "وصل الإعلان", android.widget.Toast.LENGTH_SHORT).show() } })
                profileId != null -> {
                    // A generated person is not in the snapshot: rebuild them from their id.
                    val fakeTarget = FakePeople.personFromFakeId(profileId!!)
                    val target = fakeTarget?.copy(following = state.fakeFollowing.contains(profileId!!))
                        ?: snapshot.people.find { it.id == profileId } ?: snapshot.posts.firstOrNull { it.author.id == profileId }?.author ?: snapshot.me
                    val own = target.id == snapshot.me.id
                    val fakePosts = if (fakeTarget != null) FakePeople.postsForPerson(fakeTarget, System.currentTimeMillis()).map(adjustFakePost) else emptyList()
                    val posts = if (fakeTarget != null) fakePosts else snapshot.posts.filter { it.author.id == target.id }
                    val followers = if (fakeTarget != null) FakePeople.followerCount(fakeTarget) else snapshot.social.followers[target.id] ?: 0
                    val following = if (fakeTarget != null) FakePeople.followingCount(fakeTarget) else snapshot.social.following[target.id] ?: 0
                    ProfileScreen(target, posts, followers, following, own, state.busy, Modifier.padding(padding),
                        canPin = fakeTarget == null && app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                        onBack = { profileId = null }, onEdit = { editing = true }, onCompose = { composer = true },
                        onChat = { vm.thread(target) { id -> threadId = id; profileId = null } },
                        onFollow = { if (fakeTarget != null) vm.toggleFakeFollow(target.id) else vm.followUser(target, !target.following) },
                        onCredentials = { credentials = true }, canChat = fakeTarget == null,
                        onProfile = { profileId = it.id },
                        onComments = { post -> if (FakePeople.isFakePostId(post.id)) android.widget.Toast.makeText(context, "التعليقات غير متاحة على هذا المنشور", android.widget.Toast.LENGTH_SHORT).show() else commentPost = post.id },
                        onLike = { post -> if (FakePeople.isFakePostId(post.id)) vm.toggleFakeReaction(post.id) else vm.react(post) },
                        onDelete = { vm.delete(it.id) },
                        onReact = { post, kind -> if (FakePeople.isFakePostId(post.id)) vm.toggleFakeReaction(post.id, kind ?: "like") else vm.react(post, kind) },
                        onShare = { post -> vm.sharePost(post); share(post) }, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id },
                        stories = snapshot.stories.find { it.owner.id == target.id }?.items.orEmpty(), polls = snapshot.polls, onVote = { post, option -> vm.votePoll(post.id, option) },
                        onRetryPost = { post -> vm.retryPost(post) }, onReactors = { post -> reactors = post.id },
                        onEditPost = { post -> editingPost = post },
                        canDeleteAny = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                        isOnline = if (fakeTarget != null) FakePeople.isOnline(fakeTarget, System.currentTimeMillis()) else own,
                        highlights = snapshot.highlights[target.id].orEmpty(),
                        onHighlight = { group -> group.items.firstOrNull()?.let { highlight = target.id to group.id } },
                        onCreateHighlight = { title, ids -> vm.createHighlight(title, ids) { } }, onRemoveHighlight = vm::removeHighlight,
                        onFollowers = { roster = RosterState("متابعو ${target.name}", target.id, "followers", snapshot.social.edges.filter { (_, ids) -> target.id in ids }.keys.toList()) },
                        onFollowing = { roster = RosterState("الذين يتابعهم ${target.name}", target.id, "following", snapshot.social.edges[target.id].orEmpty()) },
                        onHeartBurst = burstHearts)
                }
                thread != null -> RichChat(thread, snapshot.me.id, state.busy, Modifier.padding(padding), back = { threadId = null },
                    send = { text, reply, image, ratio, done -> vm.send(thread.id, text, reply, image, ratio, done) },
                    edit = { id, text, done -> vm.editMessage(thread.id, id, text, done) }, delete = { vm.deleteMessage(thread.id, it) },
                    react = { id, kind -> vm.reactMessage(thread.id, id, kind) }, read = { vm.readThread(thread.id) }, refresh = vm::refresh,
                    sendVoice = { uri, seconds, bars, done -> vm.sendVoice(thread.id, uri, seconds, bars, done) },
                    onProfile = { profileId = it.id }, onRetry = { message -> vm.retryMessage(thread.id, message) },
                    muted = thread.id in snapshot.muted, onMute = { vm.setMuted(thread.id, it) }, onClear = { vm.clearThread(thread.id) },
                    onBlock = { vm.setBlocked(thread.other, true) }, onDeleteForMe = { vm.deleteMessageForMe(thread.id, it) },
                    onDeleteForAll = { vm.deleteMessage(thread.id, it) },
                    friends = snapshot.people.filter { it.id != snapshot.me.id && it.friend },
                    onForwardTikTok = { id, person -> vm.thread(person) { opened -> vm.send(opened, tikTokPageUrl(id), null, null, 1.0) { } } })
                chatList -> ChatListScreen(snapshot.threads, state.busy, Modifier.padding(padding), onRefresh = vm::refresh, open = { threadId = it.id },
                    onProfile = { profileId = it.id })
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
                            onReact = { post, kind -> vm.react(post, kind) }, onShare = { post -> vm.sharePost(post); share(post) }, onPin = { post, pinned -> vm.pin(post, pinned) }, onBoost = { boostId = it.id }, onInvite = invite,
                            dark = NabdThemeState.dark, onDark = { vm.saveSettings(snapshot.settings.copy(theme = if (it) "dark" else "light")) },
                            onSettings = { settingsOpen = true }, stories = storyGroups.find { it.owner.id == snapshot.me.id }?.items.orEmpty(),
                            polls = snapshot.polls, onVote = { post, option -> vm.votePoll(post.id, option) },
                            highlights = snapshot.highlights[snapshot.me.id].orEmpty(),
                            onHighlight = { group -> group.items.firstOrNull()?.let { highlight = snapshot.me.id to group.id } },
                            onCreateHighlight = { title, ids -> vm.createHighlight(title, ids) { } },
                            onRemoveHighlight = vm::removeHighlight,
                            onFollowers = { roster = RosterState("متابعوك", snapshot.me.id, "followers", snapshot.social.edges.filter { (_, ids) -> snapshot.me.id in ids }.keys.toList()) },
                            onFollowing = { roster = RosterState("الذين تتابعهم", snapshot.me.id, "following", snapshot.social.edges[snapshot.me.id].orEmpty()) },
                            onReactors = { post -> reactors = post.id }, onRetryPost = { post -> vm.retryPost(post) },
                            onEditPost = { post -> editingPost = post },
                            canDeleteAny = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username), isOnline = true,
                            onHeartBurst = burstHearts)
                        "reels" -> ReelsScreen(snapshot.me, snapshot.reels, state.discovery, snapshot.reelPulses, snapshot.reelComments, snapshot.myReelPulses,
                            state.busy, !state.discoveryReady, Modifier.padding(top = padding.calculateTopPadding()),
                            onPulse = vm::pulseReel, onComment = { reel, text -> vm.commentReel(reel, text) },
                            onShare = { reel -> reelToForward = reel },
                            onFollow = { person -> vm.followUser(person, !person.following) }, onProfile = { profileId = it.id },
                            onPublish = { reelComposer = true }, onRefresh = { vm.loadDiscovery(true); vm.refresh() })
                        "alerts" -> AlertsScreen(snapshot, state.busy, Modifier.padding(padding), onRead = vm::readNotifications, onOpen = { commentPost = it },
                            onChat = { threadId = it }, onProfile = { profileId = it.id })
                        else -> FeedContent(snapshot, state.stories, state.busy, filter, search, Modifier.padding(padding),
                            onFilter={ filter=it }, onCompose={ composer=true }, onRefresh=vm::refresh,
                            onNewStory={ storyComposer=true }, onStory={ group -> storyOwner = group.owner.id },
                            onLike={ vm.react(it) }, onComments={ commentPost=it.id }, onDelete={ vm.delete(it.id) },
                            onReact={ post,kind -> vm.react(post,kind) }, onShare={ post -> vm.sharePost(post); share(post) }, onProfile={ profileId=it.id }, onPin={ post,pinned -> vm.pin(post,pinned) }, onBoost={ boostId=it.id }, onImage={ viewer=it },
                            polls=snapshot.polls, onVote={ post,option -> vm.votePoll(post.id, option) }, onRetryPost={ post -> vm.retryPost(post) },
                            announcement=snapshot.announcements.firstOrNull { it.id !in state.dismissed }, onDismissAnnouncement={ vm.dismissAnnouncement(it) }, seen=state.seen, refreshing=state.refreshing,
                            trending=remember(snapshot.posts) { FakePeople.trendingPosts(snapshot.posts) }, onTrending={ commentPost=it.id },
                            onHeartBurst=burstHearts,
                            onEditPost={ editingPost=it }, canDeleteAny=app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
                            pins=state.pins, pinQuery=state.pinQuery, pinterestId=state.pinterestId, pinterestStale=state.pinterestStale, savedPins=state.savedPins,
                            onPinQuery=vm::searchPins, onSubmitPinQuery={ vm.searchPins(state.pinQuery) },
                            onOpenPin={ pin -> vm.openPin(pin) { url -> openUrl(url) } },
                            onDownloadPin={ pin -> vm.downloadPin(pin) },
                            onConnectPinterest={ vm.startPinterest("link") { url -> openUrl(url) } },
                            onSearchOnPinterest={ vm.searchOnPinterest(state.pinQuery) { url -> openUrl(url) } },
                            pinsMore=state.pinsMore, pinsBusy=state.pinsBusy, onMorePins=vm::loadMorePins,
                            linkOffer=state.offerPinterest || state.offerGoogle, linkGoogle=state.offerGoogle,
                            onLink={ if (state.offerGoogle) linkGoogle(vm::dismissPinterestOffer) else vm.startPinterest("link") { url -> openUrl(url) } },
                            onDismissLink=vm::dismissPinterestOffer)
                    }
                }
            }
        }
    }
        FloatingHeartsOverlay(rootHearts, Modifier.fillMaxSize()) { particle -> rootHearts = rootHearts - particle }
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
    val editingTarget = editingPost
    if (editingTarget != null) PostEditDialog(editingTarget, state.busy, close = { editingPost = null },
        save = { text -> vm.editPost(editingTarget, text); editingPost = null })
    val post = snapshot?.posts?.find { it.id == commentPost }
    if (post != null && snapshot != null) CommentsSheet(post.copy(comments = post.comments.map { adjustFakeComment(post, it) }), snapshot.me, state.busy, state.error, close = { commentPost = null },
        send = { text, image, reply, done -> vm.comment(post.id, text, image, reply, done) },
        like = { comment -> if (FakePeople.isFakeCommentId(comment.id)) vm.toggleFakeCommentReaction(post.id, comment.id, "like") else vm.likeComment(post, comment, !comment.likedByMe) },
        delete = { vm.deleteComment(post.id, it) }, onImage = { viewer = it },
        react = { id, kind -> post.comments.find { it.id == id }?.let { if (FakePeople.isFakeCommentId(it.id)) vm.toggleFakeCommentReaction(post.id, it.id, kind) else vm.reactComment(post, it, kind) } },
        onRetry = { comment -> vm.retryComment(post, comment) }, onProfile = { commentPost = null; profileId = it.id },
        onEdit = { comment, text -> vm.editComment(post, comment, text) },
        canPin = app.nabd.nativeapp.data.isAdminHandle(snapshot.me.username),
        onPin = { comment, pinned -> vm.pinComment(post.id, comment.id, pinned) },
        onReactors = { comment -> commentReactors = post.comments.find { it.id == comment.id } ?: comment })
    val reactorComment = commentReactors
    if (reactorComment != null && snapshot != null) CommentReactorsSheet(reactorComment, snapshot.people, snapshot.me, close = { commentReactors = null },
        onProfile = { commentReactors = null; profileId = it.id },
        meReaction = if (FakePeople.isFakeCommentId(reactorComment.id)) state.fakeCommentReactions["${post?.id}:${reactorComment.id}"] else null)
    if (storyComposer) StoryCreator(state.busy, state.error, close = { storyComposer = false }, publish = { text, image, color -> vm.addStory(text, image, color) { storyComposer = false } })
    if (credentials) CredentialsDialog(state.busy, state.error, { credentials = false }) { current, newPassword, email -> vm.credentials(current, newPassword, email) { credentials = false } }
    if (viewer != null) ImageViewer(viewer!!) { viewer = null }
    val boostTarget = snapshot?.posts?.find { it.id == boostId }
    if (boostTarget != null && snapshot != null) BoostDialog(boostTarget, snapshot.me.points, app.nabd.nativeapp.data.BOOST_COST, state.busy,
        close = { boostId = null }, confirm = { vm.boost(boostTarget); boostId = null })
    val reactorPost = snapshot?.posts?.find { it.id == reactors } ?: reactors?.let { FakePeople.postById(it, System.currentTimeMillis()) }
    if (reactorPost != null && snapshot != null) ReactorsSheet(reactorPost, snapshot.people, snapshot.me, close = { reactors = null },
        onProfile = { reactors = null; profileId = it.id },
        meReaction = if (FakePeople.isFakePostId(reactorPost.id)) state.fakeReactions[reactorPost.id] else null)
    if (reelComposer) ReelComposer(state.busy, close = { reelComposer = false }) { link, caption ->
        vm.publishReel(link, caption, "", "") { reelComposer = false } }
    if (reelToForward != null && snapshot != null) ReelSendSheet(snapshot.people.filter { it.id != snapshot.me.id && it.friend }, state.busy,
        close = { reelToForward = null }, send = { person -> reelToForward?.let { reel -> vm.thread(person) { id -> vm.send(id, reel.ref, null, null, 1.0) { } } }; reelToForward = null })
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
            onContact = { settingsOpen = false; official?.let { target -> vm.thread(target) { id -> threadId = id } } },
            // 2.9.14 — Pinterest فالإعدادات: ربط، قطع، و«عاود ربط Pinterest» إلا الرمز فسد.
            pinterestLinked = state.pinterestId != null, pinterestStale = state.pinterestStale,
            onConnectPinterest = { settingsOpen = false; vm.startPinterest("link") { url -> openUrl(url) } },
            onUnlinkPinterest = { vm.unlinkPinterest() })
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
        if (friends.isEmpty()) item { EmptyState(Icons.Outlined.GroupAdd, "ما كايناش أصدقاء بعد", "تابع أي حساب وغيولي صديق مباشرة.", modifier = Modifier.padding(horizontal = 12.dp)) }
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
        items(notes, key = { it.id }) { note -> NotificationCard(note, { if (note.postId != null) onOpen(note.postId) else onProfile(note.actor) }, onProfile) }
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
