package app.nabd.nativeapp

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nabd.nativeapp.data.*
import app.nabd.nativeapp.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class AppState(val starting: Boolean = true, val signedIn: Boolean = false, val busy: Boolean = false, val snapshot: Snapshot? = null, val error: String? = null, val stories: List<LocalStory> = emptyList(), val dismissed: Set<String> = emptySet(), val seen: Set<String> = emptySet(), val refreshing: Boolean = false,
    val discovery: List<TikTokClip> = emptyList(), val discoveryReady: Boolean = false, val suggestOfficial: Boolean = false,
    /** Local-only follows/reactions of generated people and posts: there is no vault account to write them to. */
    val fakeFollowing: Set<String> = emptySet(), val fakeReactions: Map<String, String> = emptyMap(),
    /** Local-only reactions on generated comments, keyed "postId:commentId". */
    val fakeCommentReactions: Map<String, String?> = emptyMap())
data class UpdateState(val release: ApkRelease? = null, val visible: Boolean = false, val phase: String = "idle", val received: Long = 0, val total: Long = 0, val file: File? = null, val error: String? = null)

class NabdViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(AppState()); val ui = state.asStateFlow()
    private val updateState = MutableStateFlow(UpdateState()); val updates = updateState.asStateFlow()
    private val namespace = digest("${BuildConfig.TELEGRAM_TOKEN.substringBefore(':')}:${BuildConfig.TELEGRAM_CHAT}").take(24)
    private val sessions = SessionStore(application, namespace)
    private val repository by lazy { NabdRepository(TelegramTransport(BuildConfig.TELEGRAM_TOKEN, BuildConfig.TELEGRAM_CHAT), namespace, sessions) }
    private val stories = LocalStories(application, namespace)
    private val book = AccountBook(application, namespace)
    private val accountState = MutableStateFlow<List<SavedAccount>>(emptyList())
    /** Accounts that already signed in on this device, newest first. Tokens never leave the view model. */
    val accounts = accountState.asStateFlow()
    private val dismissed = DismissedAnnouncements(application, namespace)
    private val views = StoryViews(application, namespace)
    private val updater = ApkUpdater(application)
    private var download: Job? = null
    private var checking: Job? = null
    private var installing: Job? = null
    init {
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { sessions.token }
            val seen = withContext(Dispatchers.IO) { dismissed.read() }
            val watched = withContext(Dispatchers.IO) { views.read() }
            state.update { it.copy(dismissed = seen, seen = watched) }
            if (token != null) {
                val cached = withContext(Dispatchers.IO) { SnapshotCache.load(getApplication()) }
                if (cached != null) { base = cached; paint() }
            }
            state.update { it.copy(starting = false, signedIn = token != null) }
            if (token != null) { refresh(); loadDiscovery() }
            else accountState.value = withContext(Dispatchers.IO) { book.read() }
            delay(4000); checkUpdates(false)
        }
    }
    /**
     * Runs one real server action. A press is never thrown away: when a write is
     * already in flight the new one waits its turn instead of being dropped, which
     * is what used to make the app look frozen for several seconds.
     */
    private fun perform(work: suspend () -> Unit) {
        viewModelScope.launch {
            var waited = 0
            while (true) {
                val previous = state.value
                if (!previous.busy && state.compareAndSet(previous, previous.copy(busy = true, error = null))) break
                if (waited >= 15000) return@launch
                delay(60); waited += 60
            }
            try { work() } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) {
                if (e.message == "TELEGRAM_SESSION_REQUIRED") {
                    withContext(Dispatchers.IO) { sessions.token = null }
                    state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList()) }
                }
                state.update { it.copy(error = message(e)) }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
    private suspend fun reload() {
        // The growth catch-up runs before the snapshot is built, so the
        // first screen after reopening already shows the full offline
        // growth instead of the pre-bump numbers.
        runCatching { repository.bumpOfficialEngagement() }
        val fresh = repository.snapshot()
        base = fresh
        runCatching { SnapshotCache.save(getApplication(), fresh) }
        state.update { it.copy(snapshot = fresh.optimistic(pending)) }
        rememberMe(fresh.me)
        try {
            val local = stories.load(fresh.me.id)
            state.update { it.copy(stories = local) }
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
            state.update { it.copy(error = "تعذّر تحميل القصص المحلية. بيانات النسخة القديمة لم تتغيّر.") }
        }
    }

    /**
     * Optimistic writes: the interface is updated the moment the finger lifts,
     * the vault write follows on its own coroutine, and the same Telegram
     * requests as before are used. Only the timing of the refresh changed.
     * 2.9.6: a transient failure no longer kills the press on the spot.
     * A flaky network or a moved pin retries silently in the background
     * (up to 3 times, 2s/5s/10s apart) while the row stays "sending";
     * only a failure that really cannot succeed — validation, restriction,
     * a full vault, a dead session — marks it failed, with the real cause
     * shown. A conflict detected after an upload is never re-run blindly:
     * the snapshot is reloaded and the change is only retried when it is
     * provably absent, so nothing is ever published twice.
     */
    private var pending = PendingState()
    private var base: Snapshot? = null
    /** Errors worth a silent background retry, instead of a failed row. */
    private val retryableErrors = setOf("TELEGRAM_REQUEST_FAILED", "TELEGRAM_READ_FAILED", "TELEGRAM_WRITE_FAILED", "TELEGRAM_CONFLICT")
    private val retryDelays = longArrayOf(2000L, 5000L, 10000L)
    /** Generated notifications already seen, by their stable id. */
    private var readGenerated = mutableSetOf<String>()
    private var lastGeneratedIds = emptyList<String>()
    private fun paint() {
        val value = base ?: return
        state.update { it.copy(snapshot = attachGeneratedNotes(value.optimistic(pending))) }
    }
    private fun optimistic(key: String, edit: (PendingState) -> PendingState, work: suspend NabdRepository.() -> Unit,
        landed: ((Snapshot) -> Boolean)? = null) {
        viewModelScope.launch {
            pending = edit(pending); paint()
            var failure: Exception? = null
            var attempt = 0
            while (true) {
                try {
                    withContext(Dispatchers.IO) { work(repository) }
                    break
                } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) {
                    if (e.message == "TELEGRAM_CONFLICT_AFTER_UPLOAD") {
                        val fresh = runCatching { repository.snapshot() }.getOrNull()
                        if (fresh != null) {
                            base = fresh; SnapshotCache.save(getApplication(), fresh); rememberMe(fresh.me)
                            if (landed == null || landed(fresh)) break
                            if (attempt < 1) { attempt++; delay(2000); continue }
                        }
                        failure = e; break
                    }
                    if (e.message in retryableErrors && attempt < 3) { attempt++; delay(retryDelays[attempt - 1]); continue }
                    failure = e; break
                }
            }
            if (failure == null) {
                pending = pending.drop(key)
                val fresh = runCatching { repository.snapshot() }.getOrNull()
                if (fresh != null) { base = fresh; SnapshotCache.save(getApplication(), fresh); rememberMe(fresh.me) }
                paint()
            } else {
                pending = pending.fail(key); paint()
                state.update { it.copy(error = message(failure)) }
            }
        }
    }
    /**
     * Live notifications in the bell: the generated follows, reactions
     * and comments arrive as in-app notifications, built client-side
     * from the same deterministic growth the feed already shows — the
     * newest generated comment on each recent official post, the newest
     * generated reactors, and the newest generated followers. They are
     * never written to the vault (a generated person is not a real
     * account), and their read state lives in memory by stable id, so
     * opening the alerts tab clears them until new ones arrive.
     */
    private fun attachGeneratedNotes(snap: Snapshot): Snapshot {
        val generated = generatedNotifications(snap, System.currentTimeMillis())
        lastGeneratedIds = generated.map { it.id }
        if (generated.isEmpty()) return snap
        val notes = (generated + snap.notifications).sortedByDescending { it.date }.take(50)
        return snap.copy(notifications = notes)
    }
    private fun generatedNotifications(snap: Snapshot, nowMs: Long): List<Notification> {
        val official = snap.people.find { it.username == OFFICIAL_HANDLE } ?: return emptyList()
        val officialPosts = snap.posts.filter { it.author.username == OFFICIAL_HANDLE }.sortedByDescending { it.date }
        val notes = mutableListOf<Notification>()
        officialPosts.take(2).forEach { post ->
            val last = FakePeople.commentsForPost(post, nowMs).lastOrNull()
            if (last != null) notes += Notification("gen-comment:${post.id}:${last.id.substringAfterLast(':')}",
                "comment", last.author, post.id, last.date, false)
        }
        officialPosts.maxByOrNull { it.likes }?.let { post ->
            val (reactors, _) = FakePeople.reactorsForPost(post)
            reactors.take(3).forEachIndexed { index, (person, _) ->
                notes += Notification("gen-pulse:${post.id}:$index", "pulse", person, post.id,
                    isoDate(nowMs - (index + 1) * 4 * 60 * 1000), false)
            }
        }
        val total = snap.social.followers[official.id] ?: 0
        val real = snap.social.edges.values.count { official.id in it }
        val (followers, _) = FakePeople.followersForPerson(official.id, total, real)
        followers.take(3).forEachIndexed { index, person ->
            notes += Notification("gen-follow:${official.id}:$index", "follow", person, null,
                isoDate(nowMs - (index + 1) * 7 * 60 * 1000), false)
        }
        return notes.map { it.copy(read = it.id in readGenerated) }
    }
    /** Keeps the local address book in step with the account that is really signed in. */
    private suspend fun rememberMe(me: Person) {
        val token = withContext(Dispatchers.IO) { sessions.token } ?: return
        val avatar = withContext(Dispatchers.IO) { runCatching { me.avatar?.let { thumbnailImage(it) } }.getOrNull() }
            ?: me.avatar?.takeIf { it.length <= ACCOUNT_AVATAR_LIMIT }
        val entry = SavedAccount(me.id, me.name, me.username, avatar, token, 0)
        withContext(Dispatchers.IO) {
            val known = book.read().firstOrNull { it.id == me.id }
            if (known != null && known.token == token && known.name == entry.name && known.username == entry.username && known.avatar == entry.avatar) return@withContext
            book.save(entry)
        }
        refreshAccounts()
    }
    private suspend fun refreshAccounts() { accountState.value = withContext(Dispatchers.IO) { book.read() } }

    /** Switches to another real session already issued for this device. */
    fun switchAccount(id: String) = perform {
        val entry = withContext(Dispatchers.IO) { book.read() }.firstOrNull { it.id == id } ?: error("FORBIDDEN")
        withContext(Dispatchers.IO) { sessions.token = entry.token }
        try { reload() } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                book.remove(id)
                if (sessions.token == entry.token) sessions.token = null
            }
            refreshAccounts()
            state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList()) }
            throw e
        }
        state.update { it.copy(signedIn = true, error = null) }
        refreshAccounts()
    }

    /** Forgets an account on this device only; the server session expires on its own. */
    fun forgetAccount(id: String) { viewModelScope.launch { withContext(Dispatchers.IO) { book.remove(id) }; refreshAccounts() } }

    /** Opens the sign-in screen to add another account; the current one stays listed. */
    fun addAccount() = perform {
        withContext(Dispatchers.IO) { sessions.token = null }
        state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList(), error = null) }
        refreshAccounts()
    }

    /** Stories are real vault records now: visible to everyone until they expire after 24h. */
    fun addStory(text: String, image: String?, color: String? = null, success: () -> Unit) {
        val key = pendingKey("story"); val stamp = isoDate(System.currentTimeMillis())
        success()
        optimistic(key, { state -> state.copy(stories = state.stories + PendingStory(key, text, image, stamp)) }, { addStory(text, image, color) })
    }
    /** A story that failed stays in my tray with a retry, so nothing is lost. */
    fun retryStory(item: LocalStory) = addStory(item.text, item.image) { }
    /** Retrying sends the same content again and forgets the refused copy. */
    fun retryComment(post: FeedPost, comment: Comment) { pending = pending.drop(comment.id); comment(post.id, comment.text, comment.image, comment.replyTo) { } }
    fun retryMessage(thread: String, message: Message) { pending = pending.drop(message.id); send(thread, message.text, message.replyToId, message.image, 1.0) { } }
    fun retryPost(post: FeedPost) { pending = pending.drop(post.id); post(post.text, post.image, 1.0, post.privacy) { } }
    fun retryStoryPost(item: Story) { pending = pending.drop(item.id); addStory(item.text, item.image) { } }
    fun removeStory(id: String, success: () -> Unit) = change { removeStory(id); success() }
    fun seeStory(id: String) { state.update { it.copy(seen = it.seen + id) }; viewModelScope.launch(Dispatchers.IO) { views.add(id) } }
    /** A story reply is a real chat message to the story owner, not a stored reaction count. */
    fun readNotifications() {
        readGenerated += lastGeneratedIds
        paint()
        change { markNotificationsRead() }
    }
    fun storyReply(person: Person, text: String) = perform {
        val thread = repository.startThread(person.id)
        repository.sendMessage(thread, text, null, null, 1.0)
        reload()
    }
    fun clearError() { state.update { it.copy(error = null) } }
    fun login(identity: String, password: String) = perform {
        repository.login(identity, password)
        state.update { it.copy(signedIn = true) }
        reload()
    }
    fun register(name: String, email: String, password: String, invite: String? = null) = perform {
        repository.register(name, email, password, invite)
        state.update { it.copy(signedIn = true) }
        reload()
        // A new account meets the official account first: its profile opens
        // so the user can follow it with their own tap. Never auto-followed.
        state.update { it.copy(suggestOfficial = true) }
    }
    /** The official-profile suggestion was shown (or there is no official account). */
    fun dismissOfficialSuggestion() { state.update { it.copy(suggestOfficial = false) } }
    /**
     * Official-account auto-engagement, throttled inside the repository to
     * one vault write per 10 seconds. A failed or not-due bump is silent:
     * it must never break a refresh or a poll.
     */
    private fun bumpOfficialGrowth() { viewModelScope.launch { runCatching { repository.bumpOfficialEngagement() } } }
    /** Following a generated person is local only: there is no account to write to. */
    fun toggleFakeFollow(id: String) { state.update { it.copy(fakeFollowing = if (id in it.fakeFollowing) it.fakeFollowing - id else it.fakeFollowing + id) } }
    /** Reacting to a generated post is local only. */
    fun toggleFakeReaction(postId: String, kind: String = "like") { state.update { it.copy(fakeReactions = if (it.fakeReactions[postId] == kind) it.fakeReactions - postId else it.fakeReactions + (postId to kind)) } }
    /** Local-only reaction on a generated comment: no vault account to write it to. */
    fun toggleFakeCommentReaction(postId: String, commentId: String, kind: String) {
        val key = "$postId:$commentId"
        state.update { it.copy(fakeCommentReactions = if (it.fakeCommentReactions[key] == kind) it.fakeCommentReactions - key else it.fakeCommentReactions + (key to kind)) }
    }
    /** Admin pins/unpins a comment; the pin is stored, the comment is never rewritten. */
    fun pinComment(postId: String, commentId: String, pinned: Boolean) = change { pinComment(postId, commentId, pinned) }
    /** Admin boost: add generated followers to any account, stored in the vault. */
    fun boostFollowers(person: Person, count: Int) = change { boostFollowers(person.id, count) }
    /** Admin boost: add generated reactions to any post, stored in the vault. */
    fun boostReactions(postId: String, count: Int) = change { boostReactions(postId, count) }
    fun refresh() = perform {
        state.update { it.copy(refreshing = true) }
        try {
            // 2.9.5: a transient network hiccup retries once, silently, in
            // the background — the user never sees a failure for it.
            try { reload() } catch (e: Exception) {
                if (e.message in setOf("TELEGRAM_REQUEST_FAILED", "TELEGRAM_READ_FAILED", "TELEGRAM_WRITE_FAILED")) { kotlinx.coroutines.delay(1500); reload() } else throw e
            }
        } finally { state.update { it.copy(refreshing = false) } }
    }
    private fun change(work: suspend NabdRepository.() -> Unit) = perform {
        repository.work(); reload()
    }
    /** Publishing closes the composer at once; the post is on screen immediately. */
    fun post(text: String, image: String?, ratio: Double, privacy: String, success: () -> Unit) {
        val key = pendingKey("post"); val stamp = isoDate(System.currentTimeMillis())
        success()
        optimistic(key, { state -> state.copy(posts = state.posts + PendingPost(key, text, image, stamp)) },
            { createPost(text, image, ratio, privacy) },
            landed = { snap -> snap.posts.any { it.author.id == snap.me.id && it.text == text.trim() && it.date >= stamp } })
    }
    /** Editing my own post: only the text changes, and it is marked edited. */
    fun editPost(post: FeedPost, text: String) = change { editPost(post.id, text) }
    /** Editing my own comment: only its author can change it; it is marked edited. */
    fun editComment(post: FeedPost, comment: Comment, text: String) = change { editComment(post.id, comment.id, text) }
    /** A real share: the count moves at once, then the vault confirms it. */
    fun sharePost(post: FeedPost) {
        viewModelScope.launch {
            pending = pending.copy(shares = pending.shares + (post.id to ((pending.shares[post.id] ?: 0) + 1))); paint()
            val ok = runCatching { withContext(Dispatchers.IO) { repository.sharePost(post.id) } }.isSuccess
            pending = pending.dropShare(post.id)
            if (ok) {
                val fresh = runCatching { repository.snapshot() }.getOrNull()
                if (fresh != null) { base = fresh; SnapshotCache.save(getApplication(), fresh); rememberMe(fresh.me) }
            }
            paint()
        }
    }
    /** The one admin boost: post link, kind (reactions/comments/shares) and count. */
    fun boostUnified(postId: String, kind: String, count: Int) = change { adminBoostUnified(postId, kind, count) }
    fun createPoll(question: String, options: List<String>, days: Int, privacy: String, success: () -> Unit) = change { createPollPost(question, options, days, privacy); success() }
    fun votePoll(post: String, option: String) = change { votePoll(post, option) }
    /** A نبضة is a local switch first: the counter moves in the same frame. */
    fun react(post: FeedPost, kind: String? = if (post.myReaction == null) "like" else null) {
        val next = if (post.myReaction == kind) null else kind
        optimistic("reaction-$post.id-${System.currentTimeMillis()}",
            { state -> state.copy(reactions = state.reactions + (post.id to next)) }, { react(post.id, next) })
    }
    fun followUser(person: Person, follow: Boolean) = optimistic("follow-${person.id}-${System.currentTimeMillis()}",
        { state -> state.copy(follows = state.follows + (person.id to follow)) }, { followUser(person.id, follow) })
    fun likeComment(post: FeedPost, comment: Comment, liked: Boolean) = optimistic("clike-${comment.id}-${System.currentTimeMillis()}",
        { state -> state.copy(reactions = state.reactions + ("c:${post.id}:${comment.id}" to if (liked) "like" else null)) }, {
            if (comment.likedByMe != liked) likeComment(post.id, comment.id)
        })
    fun reactComment(post: FeedPost, comment: Comment, kind: String) = optimistic("creact-${comment.id}-${System.currentTimeMillis()}",
        { state -> state.copy(reactions = state.reactions + ("c:${post.id}:${comment.id}" to kind)) }, { reactComment(post.id, comment.id, kind) })
    fun reactMessage(thread: String, message: String, kind: String?) = optimistic("mreact-${message}-${System.currentTimeMillis()}",
        { state -> state.copy(reactions = state.reactions + ("m:$thread:$message" to kind)) }, { reactMessage(thread, message, kind) })
    fun createHighlight(title: String, stories: List<String>, success: () -> Unit) = change { createHighlight(title, stories); success() }
    fun removeHighlight(id: String) = change { removeHighlight(id) }
    fun sendVoice(thread: String, uri: String, seconds: Int, bars: List<Int>, success: () -> Unit) = change { sendVoice(thread, uri, seconds, bars); success() }
    /** Every preference change is written to the account, so it survives on any device. */
    fun saveSettings(next: UserSettings) = change { saveSettings(next) }
    fun setBlocked(person: Person, blocked: Boolean) = change { setBlocked(person.id, blocked) }
    fun logoutDevice(id: String) = perform { repository.logoutDevice(id); reload() }
    fun logoutOtherDevices() = perform { repository.logoutOtherDevices(); reload() }
    /** Deactivation hides the account from everyone and signs this device out. */
    fun deactivate(password: String) = perform {
        repository.deactivate(password)
        withContext(Dispatchers.IO) { sessions.token = null }
        refreshAccounts()
        state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList()) }
    }
    /** Permanent deletion: the account, its posts and every reference to it go away. */
    fun deleteAccount(password: String) = perform {
        val me = state.value.snapshot?.me?.id
        repository.deleteAccount(password)
        withContext(Dispatchers.IO) {
            if (me != null) book.remove(me)
            sessions.token = null
        }
        refreshAccounts()
        state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList()) }
    }
    /** A comment is on screen before the write starts, greyed with a small clock. */
    fun comment(post: String, text: String, image: String?, replyTo: String?, success: () -> Unit) {
        val key = pendingKey("comment"); val stamp = isoDate(System.currentTimeMillis()); val body = text.trim()
        success()
        optimistic(key, { state -> state.copy(comments = state.comments + PendingComment(key, post, body, image, replyTo, stamp)) },
            { comment(post, body, image, replyTo) },
            landed = { snap -> snap.posts.any { p -> p.id == post && p.comments.any { c -> c.author.id == snap.me.id && c.text == body } } })
    }
    /** Admin adds a comment to any post; it appears at once (optimistic), then syncs. */
    fun adminComment(postId: String, text: String) { comment(postId, text, null, null) {} }
    fun delete(post: String) = change { deletePost(post) }
    fun pin(post: FeedPost, pinned: Boolean) = change { setPinned(post.id, pinned) }
    fun boost(post: FeedPost) = change { boostPost(post.id) }
    fun verify(person: Person, verified: Boolean) = change { setVerified(person.id, verified) }
    fun restrict(person: Person, banned: Boolean?, restricted: Boolean?) = change { setRestriction(person.id, banned, restricted) }
    fun announce(text: String, success: () -> Unit) = change { announce(text); success() }
    /** Dismissal is per device and only hides the banner; the announcement stays on the server. */
    fun dismissAnnouncement(id: String) { state.update { it.copy(dismissed = it.dismissed + id) }; viewModelScope.launch(Dispatchers.IO) { dismissed.add(id) } }
    /**
     * Silent reload used by the poll. It downloads nothing while the pinned message
     * is unchanged, and it never touches the state unless the data really differs,
     * so the interface stops rebuilding itself every few seconds.
     */
    fun poll() { viewModelScope.launch {
        if (state.value.busy) return@launch
        try {
            // 2.9.5: the light snapshot reuses the last loaded vault and
            // projects the growth from the clock, so the paint never waits
            // on the network — the numbers move the moment the poll fires.
            val fresh = repository.snapshot(light = true)
            val known = base
            if (fresh !== known && fresh != known) {
                base = fresh
                runCatching { SnapshotCache.save(getApplication(), fresh) }
                state.update { it.copy(snapshot = fresh.optimistic(pending)) }
            }
        } catch (_: Exception) { }
        // The persisted write is throttled in the repository and runs after
        // the paint, so it never blocks the display. It must run on EVERY
        // poll, even when the snapshot is unchanged — the first bump is
        // what starts the growth (lastAt == 0 has nothing to project yet),
        // so skipping it would stall the growth forever.
        bumpOfficialGrowth()
    } }
    fun credentials(currentPassword: String, newPassword: String?, newEmail: String?, success: () -> Unit) = change { changeCredentials(currentPassword, newPassword, newEmail); success() }
    fun profile(name: String, username: String, bio: String, avatar: String?, removeAvatar: Boolean, success: () -> Unit) = change { updateProfile(name, username, bio, avatar, removeAvatar); success() }
    fun thread(person: Person, open: (String) -> Unit) = perform { val id = repository.startThread(person.id); reload(); open(id) }
    /** Same for a message: it leaves at once and the tick follows the vault. */
    fun send(thread: String, text: String, reply: String?, image: String?, ratio: Double, success: () -> Unit) {
        val key = pendingKey("message"); val stamp = isoDate(System.currentTimeMillis()); val body = text.trim()
        success()
        optimistic(key, { state -> state.copy(messages = state.messages + PendingMessage(key, thread, body, image, reply, stamp)) }, {
            sendMessage(thread, body, reply, image, ratio)
        })
    }
    /** One reel link travels to a friend exactly like any other real message. */
    fun sendReel(person: Person, text: String) = perform { val id = repository.startThread(person.id); repository.sendMessage(id, text, null, null, 1.0); reload() }
    fun editMessage(thread: String, id: String, text: String, success: () -> Unit) = change { editMessage(thread, id, text); success() }
    fun deleteMessage(thread: String, id: String) = change { deleteMessage(thread, id) }
    fun readThread(thread: String) = change { readThread(thread) }
    /**
     * The discovery row is a list of real TikTok videos, curated in the
     * repository so it can change without a new release. Every clip is asked
     * about before it is shown, so a link TikTok no longer answers for never
     * appears, and the answer is kept for a few hours so the tab opens at once.
     */
    fun loadDiscovery(refresh: Boolean = false) { viewModelScope.launch {
        if (refresh) state.update { it.copy(discoveryReady = false) }
        else if (state.value.discovery.isNotEmpty()) return@launch
        val cached = withContext(Dispatchers.IO) { TikTokDiscovery.cached(getApplication()) }.orEmpty()
        if (cached.isNotEmpty()) state.update { it.copy(discovery = cached, discoveryReady = true) }
        if (cached.isNotEmpty() && !refresh) return@launch
        val fresh = runCatching { TikTokDiscovery.clips(getApplication(), refresh) }.getOrDefault(emptyList())
        state.update { it.copy(discovery = fresh.ifEmpty { cached }, discoveryReady = true) }
    } }

    /**
     * A reel is a link, never a file: the vault keeps who published it and what
     * the community said, the video stays with the service that owns it.
     */
    fun publishReel(ref: String, caption: String, cover: String, video: String, done: () -> Unit) = perform {
        repository.publishReel(REEL_SOURCE_LINK, ref, video, cover, caption)
        reload(); done()
    }
    fun deleteReel(id: String) = perform { repository.deleteReel(id); reload() }
    fun pulseReel(reel: ReelItem, kind: String? = "like") {
        val next = if (reel.pulse == kind) null else kind
        optimistic("reel-${reel.key}-${System.currentTimeMillis()}",
            { state -> state.copy(reactions = state.reactions + (reel.key to next)) }, { pulseReel(reel.key, next) })
    }
    fun commentReel(reel: ReelItem, text: String) {
        val key = pendingKey("reelcomment"); val body = text.trim()
        if (body.isEmpty()) return
        optimistic(key, { state -> state.copy(reelComments = state.reelComments + PendingReelComment(key, reel.key, body, isoDate(System.currentTimeMillis()))) }, {
            commentReel(reel.key, body)
        })
    }
    fun retryReelComment(reel: ReelItem, comment: ReelComment) { pending = pending.drop(comment.id); commentReel(reel, comment.text) }
    fun removeReelComment(reel: ReelItem, id: String) = perform { repository.deleteReelComment(reel.key, id); reload() }

    /** Muting, hiding a message on my side, and emptying the thread. */
    fun setMuted(thread: String, muted: Boolean) = change { setMuted(thread, muted) }
    fun deleteMessageForMe(thread: String, message: String) = change { deleteMessageForMe(thread, message) }
    fun clearThread(thread: String) = change { clearThread(thread) }
    fun deleteComment(post: String, id: String) = change { deleteComment(post, id) }
    /**
     * Signing out is a way to change account, never a way to lose one: the session
     * stays valid and the account stays in the encrypted book of this device, so
     * the user comes back with one press. Only "forget" removes an account here.
     */
    fun logout() = perform {
        val me = state.value.snapshot?.me
        val token = withContext(Dispatchers.IO) { sessions.token }
        if (me != null && token != null) withContext(Dispatchers.IO) {
            book.save(SavedAccount(me.id, me.name, me.username,
                runCatching { me.avatar?.let { thumbnailImage(it) } }.getOrNull(), token, System.currentTimeMillis()))
        }
        withContext(Dispatchers.IO) { sessions.token = null }
        refreshAccounts()
        state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList()) }
    }
    /** The header update badge: tapping it opens the update download directly, no extra step. */
    fun downloadUpdateNow() {
        val release = updates.value.release
        if (release != null) updateState.update { it.copy(visible = true) }
        else checkUpdates(true)
    }
    fun checkUpdates(manual: Boolean = true) {
        if (download?.isActive == true || checking?.isActive == true) return
        checking = viewModelScope.launch {
            updateState.update { it.copy(phase = "checking", error = null) }
            try {
                val release = updater.check()
                updateState.value = UpdateState(release = release, visible = release != null || manual, phase = if (release == null) "none" else "available")
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
                updateState.update { it.copy(phase = "error", visible = manual, error = "تعذّر البحث عن التحديث. تحقق من الاتصال وأعد المحاولة.") }
            }
        }
    }
    fun downloadUpdate() {
        val release = updateState.value.release ?: return
        if (download?.isActive == true) return
        download = viewModelScope.launch {
            updateState.update { it.copy(phase = "downloading", received = 0, total = release.size, file = null, error = null) }
            try {
                val file = updater.download(release, { received, total -> updateState.update { it.copy(received = received, total = total) } }, { updateState.update { it.copy(phase = "verifying") } })
                updateState.update { it.copy(phase = "ready", file = file) }
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
                updateState.update { it.copy(phase = "error", file = null, error = "تعذّر تنزيل أو التحقق من التحديث. لم يتم تثبيت أي ملف.") }
            }
        }
    }
    fun cancelUpdate() {
        updater.cancel(); checking?.cancel(); checking = null; installing?.cancel(); installing = null; download?.cancel(); download = null
        updateState.update { it.copy(phase = if (it.release == null) "idle" else "available", received = 0, file = null, error = null) }
    }
    fun hideUpdate() { cancelUpdate(); updateState.update { it.copy(visible = false) } }
    fun installUpdate(start: (Intent) -> Unit) {
        val current = updateState.value
        if (current.phase != "ready" || current.file == null || current.release == null || installing?.isActive == true) return
        installing = viewModelScope.launch {
            updateState.update { it.copy(phase = "verifying") }
            try {
                val intent = withContext(Dispatchers.IO) { updater.installIntent(current.file, current.release) }
                start(intent)
                updateState.update { it.copy(phase = "ready") }
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
                updateState.update { it.copy(phase = "error", file = null, error = "تعذّر فتح المثبّت أو لم يجتز الملف التحقق. لم نثبّت التحديث.") }
            }
        }
    }
    override fun onCleared() { updater.cancel(); super.onCleared() }
}

fun message(error: Exception): String = when (error.message) {
    "INVALID_CREDENTIALS" -> "اسم المستخدم أو كلمة المرور غير صحيحة"
    "EMAIL_TAKEN" -> "هذا البريد مستعمل بالفعل"
    "USERNAME_TAKEN" -> "اسم المستخدم مستعمل بالفعل"
    "TELEGRAM_PASSWORD_LENGTH" -> "كلمة المرور خاصها بين 8 و128 حرف"
    "TELEGRAM_CONFIG_REQUIRED" -> "ما قدرناش نكملو هاد العملية دابا. بيانات حسابك ما تبدّلاتش."
    "TELEGRAM_SESSION_REQUIRED" -> "انتهت الجلسة. سجّل الدخول من جديد."
    "TELEGRAM_CONFLICT", "TELEGRAM_CONFLICT_AFTER_UPLOAD" -> "جهاز آخر بدّل البيانات. حدّث قبل إعادة المحاولة؛ لم نؤكّد الحفظ."
    "TELEGRAM_VAULT_MISSING", "TELEGRAM_FOREIGN_PIN", "TELEGRAM_INVALID_VAULT" -> "تعذّر التحقق من نسخة البيانات. لم يتم مسحها أو تعويضها."
    "TELEGRAM_VAULT_FULL" -> "النسخة وصلت للحد المسموح. لم نحفظ التغيير."
    "LOCAL_STORIES_FULL" -> "وصلت القصص المحلية للحد المسموح (80 قصة أو 16MB). احذف قصة قبل الإضافة."
    "FORBIDDEN" -> "هذا الإجراء غير مسموح لهذا الحساب"
    "ACCOUNT_BANNED" -> "هذا الحساب محظور من طرف الإدارة"
    "ACCOUNT_RESTRICTED" -> "حسابك مقيد: ما تقدرش تنشر ولا تعلّق دابا"
    "NOT_ENOUGH_POINTS" -> "ما عندكش نقاط كافية. كل منشور ولا دعوة كتزيد النقاط ديالك."
    "INVITE_NOT_FOUND" -> "كود الدعوة غير صحيح"
    "VALIDATION" -> "راجع الحقول وطول النص قبل المتابعة"
    // 2.9.5: only a genuine network failure blames the connection. Any
    // other error is a neutral "try again" — the user must never see
    // "connection failed" while the internet is working fine.
    "TELEGRAM_REQUEST_FAILED", "TELEGRAM_READ_FAILED", "TELEGRAM_WRITE_FAILED" -> "تعذّر الاتصال بالخادم. تحقق من الاتصال وأعد المحاولة."
    else -> "تعذّر إكمال العملية. حاول مجدداً."
}
