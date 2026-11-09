package app.nabd.nativeapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nabd.nativeapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Full-screen glass panel that slides up from the bottom; never a centred white dialog. */
@Composable private fun GlassSheet(close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x660A1628))) {
            AnimatedVisibility(visible = visible, modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
                enter = slideInVertically(animationSpec = spring(dampingRatio = .82f, stiffness = Spring.StiffnessLow)) { it } + fadeIn()) {
                val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                Box(Modifier.fillMaxSize().shadow(10.dp, shape, spotColor = ShadowInk, ambientColor = ShadowInk).clip(shape).background(CardFill)) {
                    Box(Modifier.matchParentSize().blur(28.dp).background(GlassSheen))
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) { content(this) }
                }
            }
        }
    }
}

/**
 * Post composer: a glassy full screen with my identity, a frameless text field,
 * a glass image preview and a floating option bar for photo and story.
 */
@Composable internal fun ComposerScreen(me: app.nabd.nativeapp.data.Person, busy: Boolean, serviceError: String?,
    close: () -> Unit, publish: (String, String?, Double, String) -> Unit, openStory: () -> Unit,
    friends: List<app.nabd.nativeapp.data.Person> = emptyList(), onSendReel: (app.nabd.nativeapp.data.Person, String) -> Unit = { _, _ -> },
    publishPoll: (String, List<String>, String) -> Unit = { _, _, _ -> }) {
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var ratio by remember { mutableDoubleStateOf(1.0) }
    var imageBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var friendPicker by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf("public") }
    var pollMode by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    val reel = remember(text) { findReel(text) }
    val context = androidx.compose.ui.platform.LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            imageBusy = true
            try { val value = inlineImage(context, uri); image = value.first; ratio = value.second }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = "تعذّر تجهيز الصورة ضمن الحد المسموح." }
            finally { imageBusy = false }
        }
    }
    val clean = options.map { it.trim() }.filter { it.isNotBlank() }
    val ready = if (pollMode) question.isNotBlank() && clean.size >= 2 else text.isNotBlank() || image != null
    // 2.9.5: the composer never locks while a background sync runs — the
    // post publishes optimistically, so typing, publishing and closing
    // all stay available at any time.
    GlassSheet(close = { close() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(Icons.Outlined.Close, "إغلاق", { close() }, size = 40.dp, modifier = Modifier.testTag("composer-close"))
            Spacer(Modifier.weight(1f))
            PillButton(if (busy) "جارٍ النشر…" else "نشر", {
                if (pollMode) publishPoll(question.trim(), clean, privacy) else publish(text, image, ratio, privacy)
            }, enabled = !imageBusy && ready, height = 44.dp, fontSize = 14, testTag = "composer-publish")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(me, 46)
            Column(Modifier.weight(1f)) {
                PersonName(me, size = 15)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Public, null, tint = Muted, modifier = Modifier.size(15.dp))
                    Text("عام · يظهر لجميع مستخدمي نبض", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (pollMode) Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("استطلاع رأي", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Black)
            NabdField(question, { question = it.take(160) }, "السؤال", Modifier.fillMaxWidth(), enabled = true, tag = "poll-question")
            options.forEachIndexed { index, option ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { NabdField(option, { value -> options = options.toMutableList().also { it[index] = value.take(60) } },
                        "الخيار ${index + 1}", Modifier.fillMaxWidth(), enabled = true, tag = "poll-option-$index") }
                    if (options.size > 2) GlassCircle(Icons.Outlined.Close, "حذف الخيار", { options = options.filterIndexed { i, _ -> i != index } }, size = 36.dp, tint = Muted)
                }
            }
            if (options.size < 4) PillButton("إضافة خيار", { options = options + "" }, modifier = Modifier.fillMaxWidth(), brush = null, height = 42.dp, fontSize = 13, icon = Icons.Outlined.Add)
            Text("الاستطلاع كيبقى مفتوح، وكل صوت كيتحسب من حساب واحد.", color = Muted, fontSize = 11.5.sp)
        } else OutlinedTextField(text, { text = it.take(5000) }, placeholder = { Text("بمَ تفكر؟", color = Muted, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
            enabled = true, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).testTag("composer-text"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent, focusedContainerColor = Raised, unfocusedContainerColor = Raised),
            textStyle = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, color = TextMain))
        if (image != null) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val shape = RoundedCornerShape(24.dp)
            Box(Modifier.fillMaxWidth().shadow(10.dp, shape).clip(shape).background(Color(0xE6FFFFFF)).border(1.dp, GlassEdge, shape)) {
                Box(Modifier.matchParentSize().blur(18.dp).background(Color(0x26FFFFFF)))
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) {
                        NabdImage(image, "الصورة المختارة", Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)), ContentScale.Crop)
                        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color(0x990A1628))
                            .pressClick { image = null }.testTag("composer-remove-image"), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "إزالة الصورة", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        if (reel != null) {
            var playing by remember(reel.id) { mutableStateOf(false) }
            var full by remember(reel.id) { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(0.72f)) {
                ReelCard(reel, Modifier.fillMaxWidth(), playing = playing, onPlay = { playing = true }, onStop = { playing = false }, onExpand = { full = true })
            }
            if (friends.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                PillButton("إرسال كرسالة", { friendPicker = true }, enabled = text.isNotBlank(),
                    height = 46.dp, fontSize = 14, icon = Icons.AutoMirrored.Outlined.Send, testTag = "composer-send-reel")
            }
            if (full) ReelViewer(reel) { full = false }
            if (friendPicker) ReelSendSheet(friends, busy, close = { friendPicker = false },
                send = { person -> friendPicker = false; onSendReel(person, text.trim()); close() })
        }
        PrivacyPicker(privacy, { privacy = it }, enabled = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComposerChip(Icons.Outlined.Image, "صورة", !imageBusy) { picker.launch("image/*") }
            ComposerChip(Icons.Outlined.AutoAwesome, "قصة", !imageBusy) { close(); openStory() }
            ComposerChip(Icons.Outlined.Poll, if (pollMode) "منشور عادي" else "استطلاع", !imageBusy) { pollMode = !pollMode }
        }
        if (imageBusy) Text("جارٍ تجهيز الصورة…", Modifier.padding(horizontal = 16.dp), color = Muted, fontSize = 12.sp)
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = AccentLight, fontSize = 12.sp) }
        serviceError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = AccentLight, fontSize = 12.sp) }
    }
}

@Composable private fun ComposerChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, click: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val press = rememberPress()
    Row(Modifier.shadow(10.dp, shape, spotColor = ShadowInk, ambientColor = ShadowInk).clip(shape).background(CardFill).border(1.dp, GlassEdge, shape)
        .pressClick(press = press, enabled = enabled, onClick = click).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
        Text(label, color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Story creator, camera first: the photo picker opens straight away, then the
 * full-screen preview carries the text itself, which can be dragged and coloured
 * before publishing a real story for every user.
 */
/** Background choices for a story: solid colours and two gradients, stored as hex or `gradient:<name>`. */
private data class StoryBg(val value: String, val brush: Brush)
private val StoryBackgrounds = listOf(
    StoryBg("#0B0F19", Brush.linearGradient(listOf(Color(0xFF0B0F19), Color(0xFF1A2340)))),
    StoryBg("#FFE066", Brush.linearGradient(listOf(Color(0xFFFFE066), Color(0xFFFFC53D)))),
    StoryBg("#38BDF8", Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))),
    StoryBg("#E11D48", Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF9F1239)))),
    StoryBg("#22C55E", Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D)))),
    StoryBg("gradient:sunset", Brush.linearGradient(listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)))),
    StoryBg("gradient:ocean", Brush.linearGradient(listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))))
)

/** The story creator: a live 9:16 floating preview in the centre — the photo area IS the preview, tap it to pick a photo, type the text directly on it. */
@Composable internal fun StoryCreator(busy: Boolean, error: String?, close: () -> Unit, publish: (String, String?, String?) -> Unit, autoPick: Boolean = false) {
    var text by remember { mutableStateOf("") }
    var shiftX by remember { mutableFloatStateOf(0f) }
    var shiftY by remember { mutableFloatStateOf(0f) }
    var bg by remember { mutableStateOf(StoryBackgrounds[0]) }
    var image by remember { mutableStateOf<String?>(null) }
    var imageBusy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            imageBusy = true
            try { image = inlineImage(context, uri).first } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { problem = "تعذّر تجهيز الصورة ضمن الحد المسموح." } finally { imageBusy = false }
        }
    }
    LaunchedEffect(Unit) { if (autoPick && !opened) { opened = true; picker.launch("image/*") } }
    // 2.9.5: the story creator also stays open and usable while a
    // background sync runs — closing and publishing never wait on it.
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF05070D)).testTag("story-creator")) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp)
                    .fillMaxWidth(.78f).aspectRatio(9f / 16f)
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = ShadowInk, ambientColor = ShadowInk)
                    .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = GoldGlow, ambientColor = GoldGlow)
                    .clip(RoundedCornerShape(24.dp)).background(bg.brush).border(1.dp, GlassEdge, RoundedCornerShape(24.dp))
                    .pressClick { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (image != null) NabdImage(image, "معاينة القصة", Modifier.fillMaxSize(), ContentScale.Crop)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null, tint = Color(0xAAFFFFFF), modifier = Modifier.size(52.dp))
                        Text(if (imageBusy) "جارٍ تجهيز الصورة…" else "اضغط لاختيار صورة", color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    if (image != null || text.isNotBlank()) Box(Modifier.align(Alignment.Center)
                        .offset { IntOffset(shiftX.roundToInt(), shiftY.roundToInt()) }
                        .pointerInput(Unit) { detectDragGestures { _, drag -> shiftX += drag.x; shiftY += drag.y } }
                        .padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                        BasicTextField(text, { text = it.take(300) }, textStyle = TextStyle(color = if (bg.value == "#FFE066") Color(0xFF0F172A) else Color.White,
                            fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 34.sp),
                            modifier = Modifier.fillMaxWidth().testTag("story-creator-text"),
                            decorationBox = { inner ->
                                if (text.isBlank()) Text("اكتب على القصة...", color = Color(0xAAFFFFFF), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                inner()
                            })
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(42.dp).clip(CircleShape).background(Color(0x59FFFFFF))
                .pressClick(onClick = close).testTag("story-creator-close"), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "إلغاء القصة", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    StoryBackgrounds.forEach { option ->
                        Box(Modifier.padding(horizontal = 5.dp).size(if (option == bg) 30.dp else 24.dp).clip(CircleShape)
                            .background(option.brush).border(if (option == bg) 2.dp else 1.dp, if (option == bg) Gold else Color.White, CircleShape)
                            .clickable { bg = option }.testTag("story-color"))
                    }
                }
                Box(Modifier.fillMaxWidth().shadow(10.dp, CircleShape, spotColor = GoldGlow, ambientColor = GoldGlow)) {
                    PillButton(if (busy) "جارٍ النشر…" else "نشر القصة", { publish(text, image, bg.value) },
                        enabled = !imageBusy && (text.isNotBlank() || image != null),
                        modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Send, testTag = "story-publish")
                }
                Text("تبقى قصتك ظاهرة 24 ساعة ثم تختفي تلقائياً", color = Color(0xAAFFFFFF), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                problem?.let { Text(it, color = AccentLight, fontSize = 12.sp) }
                error?.let { Text(it, color = AccentLight, fontSize = 12.sp) }
            }
        }
    }
}

/** Glass audience selector: who really sees the post, chosen before publishing. */
@Composable private fun PrivacyPicker(value: String, change: (String) -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VISIBILITY_CHOICES.forEach { choice ->
            val selected = choice == value
            val shape = RoundedCornerShape(16.dp)
            val press = rememberPress()
            Row(Modifier.clip(shape).background(if (selected) Accent.copy(alpha = .14f) else Raised)
                .pressClick(press = press, enabled = enabled, onClick = { change(choice) }).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (choice == "friends") Icons.Outlined.Group else Icons.Outlined.Public, null,
                    tint = if (selected) Accent else Muted, modifier = Modifier.size(18.dp))
                Column {
                    Text(if (choice == "friends") "الأصدقاء فقط" else "عام", color = if (selected) Accent else TextMain,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                    Text(if (choice == "friends") "غيشوفوه غير أصدقائك" else "غيشوفوه كاع المستخدمين", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Edit my own post: the same text, changed in place, marked edited when saved. */
@Composable internal fun PostEditDialog(post: app.nabd.nativeapp.data.FeedPost, busy: Boolean, close: () -> Unit, save: (String) -> Unit) {
    var text by remember { mutableStateOf(post.text) }
    GlassWindow(close = close) {
        Text("تعديل المنشور", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Black)
        NabdField(text, { text = it.take(2000) }, "عدّل منشورك…", Modifier.fillMaxWidth(), enabled = !busy, singleLine = false, maxLines = 6, tag = "post-edit-input")
        PillButton("حفظ التعديل", { save(text.trim()) }, modifier = Modifier.fillMaxWidth(), enabled = !busy && text.trim().isNotBlank() && text.trim() != post.text)
        PillButton("إلغاء", close, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
}
