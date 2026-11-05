package app.nabd.nativeapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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

private val StoryColors = listOf(Color.White, Color(0xFFFFE066), Color(0xFF38BDF8), Color(0xFFE11D48), Color(0xFF22C55E))

/** Full-screen glass panel that slides up from the bottom; never a centred white dialog. */
@Composable private fun GlassSheet(close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x660A1628))) {
            AnimatedVisibility(visible = visible, modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(),
                enter = slideInVertically(animationSpec = spring(dampingRatio = .82f, stiffness = Spring.StiffnessLow)) { it } + fadeIn()) {
                val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                Box(Modifier.fillMaxSize().shadow(10.dp, shape).clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0xF5FFFFFF), Color(0xE6F1F5FF))))) {
                    Box(Modifier.matchParentSize().blur(28.dp).background(Color(0x26FFFFFF)))
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
    close: () -> Unit, publish: (String, String?, Double) -> Unit, openStory: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var ratio by remember { mutableDoubleStateOf(1.0) }
    var imageBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
    val ready = text.isNotBlank() || image != null
    GlassSheet(close = { if (!busy) close() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(Icons.Outlined.Close, "إغلاق", { if (!busy) close() }, size = 40.dp, modifier = Modifier.testTag("composer-close"))
            Spacer(Modifier.weight(1f))
            PillButton(if (busy) "جارٍ النشر…" else "نشر", { publish(text, image, ratio) }, enabled = !busy && !imageBusy && ready,
                height = 44.dp, fontSize = 14, testTag = "composer-publish")
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
        OutlinedTextField(text, { text = it.take(5000) }, placeholder = { Text("بمَ تفكر؟", color = Muted, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).testTag("composer-text"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent, focusedContainerColor = Raised, unfocusedContainerColor = Raised),
            textStyle = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, color = TextMain))
        if (image != null) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val shape = RoundedCornerShape(24.dp)
            Box(Modifier.fillMaxWidth().shadow(10.dp, shape).clip(shape).background(Color(0xE6FFFFFF)).border(1.dp, Color(0x66FFFFFF), shape)) {
                Box(Modifier.matchParentSize().blur(18.dp).background(Color(0x26FFFFFF)))
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) {
                        NabdImage(image, "الصورة المختارة", Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)), ContentScale.Crop)
                        if (!busy) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color(0x990A1628))
                            .pressClick { image = null }.testTag("composer-remove-image"), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Close, "إزالة الصورة", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComposerChip(Icons.Outlined.Image, "صورة", !busy && !imageBusy) { picker.launch("image/*") }
            ComposerChip(Icons.Outlined.AutoAwesome, "قصة", !busy && !imageBusy) { close(); openStory() }
        }
        if (imageBusy) Text("جارٍ تجهيز الصورة…", Modifier.padding(horizontal = 16.dp), color = Muted, fontSize = 12.sp)
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = AccentLight, fontSize = 12.sp) }
        serviceError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = AccentLight, fontSize = 12.sp) }
    }
}

@Composable private fun ComposerChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, click: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val press = rememberPress()
    Row(Modifier.shadow(10.dp, shape, spotColor = ShadowInk, ambientColor = ShadowInk).clip(shape).background(CardFill).border(1.dp, Color(0x66FFFFFF), shape)
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
@Composable internal fun StoryCreator(busy: Boolean, error: String?, close: () -> Unit, publish: (String, String?) -> Unit, autoPick: Boolean = true) {
    var text by remember { mutableStateOf("") }
    var shiftX by remember { mutableFloatStateOf(0f) }
    var shiftY by remember { mutableFloatStateOf(0f) }
    var color by remember { mutableStateOf(Color.White) }
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
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF05070D)).testTag("story-creator")) {
            if (image != null) NabdImage(image, "معاينة القصة", Modifier.fillMaxSize(), ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = Color(0xAAFFFFFF), modifier = Modifier.size(52.dp))
                    Text(if (imageBusy) "جارٍ تجهيز الصورة…" else "اختيار صورة للقصة", color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    PillButton("اختيار صورة", { picker.launch("image/*") }, enabled = !imageBusy, height = 44.dp, fontSize = 13, icon = Icons.Outlined.Image)
                }
            }
            if (image != null) Box(Modifier.align(Alignment.Center).offset { IntOffset(shiftX.roundToInt(), shiftY.roundToInt()) }
                .pointerInput(Unit) { detectDragGestures { _, drag -> shiftX += drag.x; shiftY += drag.y } }
                .padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                Text(text.ifBlank { "" }, color = color, fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 34.sp)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(42.dp).clip(CircleShape).background(Color(0x59FFFFFF))
                .pressClick(enabled = !busy, onClick = close).testTag("story-creator-close"), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "إلغاء القصة", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    StoryColors.forEach { tint ->
                        Box(Modifier.padding(horizontal = 5.dp).size(if (tint == color) 30.dp else 24.dp).clip(CircleShape)
                            .background(tint).border(if (tint == color) 2.dp else 1.dp, Color.White, CircleShape)
                            .clickable { color = tint }.testTag("story-color"))
                    }
                }
                OutlinedTextField(text, { text = it.take(300) }, placeholder = { Text("اكتب على القصة...", color = Color(0xAAFFFFFF), fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("story-creator-text"), maxLines = 3, shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0x22FFFFFF), unfocusedContainerColor = Color(0x22FFFFFF),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0x66FFFFFF), unfocusedBorderColor = Color(0x44FFFFFF)))
                PillButton(if (busy) "جارٍ النشر…" else "نشر القصة", { publish(text, image) },
                    enabled = !busy && !imageBusy && (text.isNotBlank() || image != null),
                    modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Send, testTag = "story-publish")
                Text("تبقى قصتك ظاهرة 24 ساعة ثم تختفي تلقائياً", color = Color(0xAAFFFFFF), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                problem?.let { Text(it, color = AccentLight, fontSize = 12.sp) }
                error?.let { Text(it, color = AccentLight, fontSize = 12.sp) }
            }
        }
    }
}
