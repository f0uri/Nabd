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

data class AppState(val starting: Boolean = true, val signedIn: Boolean = false, val busy: Boolean = false, val snapshot: Snapshot? = null, val error: String? = null, val stories: List<LocalStory> = emptyList(), val dismissed: Set<String> = emptySet(), val seen: Set<String> = emptySet())
data class UpdateState(val release: ApkRelease? = null, val visible: Boolean = false, val phase: String = "idle", val received: Long = 0, val total: Long = 0, val file: File? = null, val error: String? = null)

class NabdViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(AppState()); val ui = state.asStateFlow()
    private val updateState = MutableStateFlow(UpdateState()); val updates = updateState.asStateFlow()
    private val namespace = digest("${BuildConfig.TELEGRAM_TOKEN.substringBefore(':')}:${BuildConfig.TELEGRAM_CHAT}").take(24)
    private val sessions = SessionStore(application, namespace)
    private val repository by lazy { NabdRepository(TelegramTransport(BuildConfig.TELEGRAM_TOKEN, BuildConfig.TELEGRAM_CHAT), namespace, sessions) }
    private val stories = LocalStories(application, namespace)
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
            state.update { it.copy(starting = false, signedIn = token != null) }
            if (token != null) refresh()
            delay(4000); checkUpdates(false)
        }
    }
    private fun perform(work: suspend () -> Unit) {
        while (true) {
            val previous = state.value
            if (previous.busy) return
            if (state.compareAndSet(previous, previous.copy(busy = true, error = null))) break
        }
        viewModelScope.launch {
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
        val fresh = repository.snapshot()
        state.update { it.copy(snapshot = fresh) }
        try {
            val local = stories.load(fresh.me.id)
            state.update { it.copy(stories = local) }
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
            state.update { it.copy(error = "تعذّر تحميل القصص المحلية. بيانات النسخة القديمة لم تتغيّر.") }
        }
    }
    /** Stories are real vault records now: visible to everyone until they expire after 24h. */
    fun addStory(text: String, image: String?, success: () -> Unit) = change { addStory(text, image); success() }
    fun removeStory(id: String, success: () -> Unit) = change { removeStory(id); success() }
    fun seeStory(id: String) { state.update { it.copy(seen = it.seen + id) }; viewModelScope.launch(Dispatchers.IO) { views.add(id) } }
    /** A story reply is a real chat message to the story owner, not a stored reaction count. */
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
    }
    fun refresh() = perform { reload() }
    private fun change(work: suspend NabdRepository.() -> Unit) = perform {
        repository.work(); reload()
    }
    fun post(text: String, image: String?, ratio: Double, success: () -> Unit) = change { createPost(text, image, ratio); success() }
    fun react(post: FeedPost, kind: String? = if (post.myReaction == null) "like" else null) = change { react(post.id, kind) }
    fun comment(post: String, text: String, image: String?, replyTo: String?, success: () -> Unit) = change { comment(post, text, image, replyTo); success() }
    fun delete(post: String) = change { deletePost(post) }
    fun follow(person: Person) = change { follow(person.id, !person.friend) }
    fun followUser(person: Person, following: Boolean) = change { followUser(person.id, following) }
    fun pin(post: FeedPost, pinned: Boolean) = change { setPinned(post.id, pinned) }
    fun boost(post: FeedPost) = change { boostPost(post.id) }
    fun verify(person: Person, verified: Boolean) = change { setVerified(person.id, verified) }
    fun restrict(person: Person, banned: Boolean?, restricted: Boolean?) = change { setRestriction(person.id, banned, restricted) }
    fun announce(text: String, success: () -> Unit) = change { announce(text); success() }
    /** Dismissal is per device and only hides the banner; the announcement stays on the server. */
    fun dismissAnnouncement(id: String) { state.update { it.copy(dismissed = it.dismissed + id) }; viewModelScope.launch(Dispatchers.IO) { dismissed.add(id) } }
    /** Silent reload used by the 3s poll: it never shows the busy bar or clears data. */
    fun poll() { viewModelScope.launch { try { repository.snapshot().let { fresh -> state.update { it.copy(snapshot = fresh) } } } catch (_: Exception) { } } }
    fun credentials(currentPassword: String, newPassword: String?, newEmail: String?, success: () -> Unit) = change { changeCredentials(currentPassword, newPassword, newEmail); success() }
    fun profile(name: String, username: String, bio: String, avatar: String?, removeAvatar: Boolean, success: () -> Unit) = change { updateProfile(name, username, bio, avatar, removeAvatar); success() }
    fun thread(person: Person, open: (String) -> Unit) = perform { val id = repository.startThread(person.id); reload(); open(id) }
    fun send(thread: String, text: String, reply: String?, image: String?, ratio: Double, success: () -> Unit) = change { sendMessage(thread, text, reply, image, ratio); success() }
    fun editMessage(thread: String, id: String, text: String, success: () -> Unit) = change { editMessage(thread, id, text); success() }
    fun deleteMessage(thread: String, id: String) = change { deleteMessage(thread, id) }
    fun reactMessage(thread: String, id: String, kind: String?) = change { reactMessage(thread, id, kind) }
    fun readThread(thread: String) = change { readThread(thread) }
    fun likeComment(post: String, id: String) = change { likeComment(post, id) }
    fun deleteComment(post: String, id: String) = change { deleteComment(post, id) }
    fun logout() = perform {
        try { repository.logout() } finally {
            withContext(Dispatchers.IO) { sessions.token = null }
            state.update { it.copy(signedIn = false, snapshot = null, stories = emptyList()) }
        }
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
    "TELEGRAM_CONFIG_REQUIRED" -> "إعداد الخدمة ناقص في هذه النسخة. لم نغيّر بيانات حسابك."
    "TELEGRAM_SESSION_REQUIRED" -> "انتهت الجلسة. سجّل الدخول من جديد."
    "TELEGRAM_CONFLICT" -> "جهاز آخر بدّل البيانات. حدّث قبل إعادة المحاولة؛ لم نؤكّد الحفظ."
    "TELEGRAM_VAULT_MISSING", "TELEGRAM_FOREIGN_PIN", "TELEGRAM_INVALID_VAULT" -> "تعذّر التحقق من نسخة البيانات. لم يتم مسحها أو تعويضها."
    "TELEGRAM_VAULT_FULL" -> "النسخة وصلت للحد المسموح. لم نحفظ التغيير."
    "LOCAL_STORIES_FULL" -> "وصلت القصص المحلية للحد المسموح (80 قصة أو 16MB). احذف قصة قبل الإضافة."
    "FORBIDDEN" -> "هذا الإجراء غير مسموح لهذا الحساب"
    "ACCOUNT_BANNED" -> "هذا الحساب محظور من طرف الإدارة"
    "ACCOUNT_RESTRICTED" -> "حسابك مقيد: ما تقدرش تنشر ولا تعلّق دابا"
    "NOT_ENOUGH_POINTS" -> "ما عندكش نقاط كافية. كل منشور ولا دعوة كتزيد النقاط ديالك."
    "INVITE_NOT_FOUND" -> "كود الدعوة غير صحيح"
    "VALIDATION" -> "راجع الحقول وطول النص قبل المتابعة"
    else -> "تعذّر إكمال العملية. تحقق من الاتصال وأعد المحاولة. لم نؤكّد الحفظ."
}
