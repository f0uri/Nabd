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

data class AppState(val starting: Boolean = true, val signedIn: Boolean = false, val busy: Boolean = false, val snapshot: Snapshot? = null, val error: String? = null)
data class UpdateState(val release: ApkRelease? = null, val visible: Boolean = false, val phase: String = "idle", val received: Long = 0, val total: Long = 0, val file: File? = null, val error: String? = null)

class NabdViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(AppState()); val ui = state.asStateFlow()
    private val updateState = MutableStateFlow(UpdateState()); val updates = updateState.asStateFlow()
    private val namespace = digest("${BuildConfig.TELEGRAM_TOKEN.substringBefore(':')}:${BuildConfig.TELEGRAM_CHAT}").take(24)
    private val sessions = SessionStore(application, namespace)
    private val repository by lazy { NabdRepository(TelegramTransport(BuildConfig.TELEGRAM_TOKEN, BuildConfig.TELEGRAM_CHAT), namespace, sessions) }
    private val updater = ApkUpdater(application)
    private var download: Job? = null
    private var checking: Job? = null
    private var installing: Job? = null
    init {
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { sessions.token }
            state.update { it.copy(starting = false, signedIn = token != null) }
            if (token != null) refresh()
            delay(4000); checkUpdates(false)
        }
    }
    private fun perform(work: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            state.update { it.copy(busy = true, error = null) }
            try { work() } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) {
                if (e.message == "TELEGRAM_SESSION_REQUIRED") {
                    withContext(Dispatchers.IO) { sessions.token = null }
                    state.update { it.copy(signedIn = false, snapshot = null) }
                }
                state.update { it.copy(error = message(e)) }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
    fun clearError() { state.update { it.copy(error = null) } }
    fun login(identity: String, password: String) = perform {
        repository.login(identity, password)
        state.update { it.copy(signedIn = true) }
        state.update { it.copy(snapshot = repository.snapshot()) }
    }
    fun register(name: String, email: String, password: String) = perform {
        repository.register(name, email, password)
        state.update { it.copy(signedIn = true) }
        state.update { it.copy(snapshot = repository.snapshot()) }
    }
    fun refresh() = perform { val snapshot = repository.snapshot(); state.update { it.copy(snapshot = snapshot) } }
    private fun change(work: suspend NabdRepository.() -> Unit) = perform {
        repository.work(); val fresh = repository.snapshot(); state.update { it.copy(snapshot = fresh) }
    }
    fun post(text: String, image: String?, ratio: Double, success: () -> Unit) = change { createPost(text, image, ratio); success() }
    fun react(post: FeedPost) = change { react(post.id, if (post.myReaction == null) "like" else null) }
    fun comment(post: String, text: String, success: () -> Unit) = change { comment(post, text); success() }
    fun delete(post: String) = change { deletePost(post) }
    fun follow(person: Person) = change { follow(person.id, !person.friend) }
    fun profile(name: String, username: String, bio: String, avatar: String?, success: () -> Unit) = change { updateProfile(name, username, bio, avatar); success() }
    fun thread(person: Person, open: (String) -> Unit) = perform { val id = repository.startThread(person.id); state.update { it.copy(snapshot = repository.snapshot()) }; open(id) }
    fun send(thread: String, text: String, success: () -> Unit) = change { sendMessage(thread, text); success() }
    fun logout() = perform {
        try { repository.logout() } finally {
            withContext(Dispatchers.IO) { sessions.token = null }
            state.update { it.copy(signedIn = false, snapshot = null) }
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
    "FORBIDDEN" -> "هذا الإجراء غير مسموح لهذا الحساب"
    "VALIDATION" -> "راجع الحقول وطول النص قبل المتابعة"
    else -> "تعذّر إكمال العملية. تحقق من الاتصال وأعد المحاولة. لم نؤكّد الحفظ."
}
