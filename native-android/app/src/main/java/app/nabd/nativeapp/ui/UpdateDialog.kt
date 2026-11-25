package app.nabd.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nabd.nativeapp.UpdateState
import java.util.Locale

private val UpdateBlue = Color(0xFF2878FF)
private val UpdateCyan = Color(0xFF31C6E8)
private val UpdateGreen = Color(0xFF16B981)

/** A floating glass update card. Progress always reflects the real download bytes. */
@Composable fun UpdateDialog(state: UpdateState, close: () -> Unit, download: () -> Unit, install: () -> Unit, retry: () -> Unit) {
    if (!state.visible) return
    val release = state.release
    val downloading = state.phase == "downloading" || state.phase == "verifying"
    val ready = state.phase == "ready"
    val fraction = if (state.total > 0L) (state.received.toFloat() / state.total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    val floatMotion = rememberInfiniteTransition(label = "update-card-float")
    val floatY by floatMotion.animateFloat(-3.5f, 3.5f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "float-y")
    val isDark = NabdThemeState.dark
    val accent = if (release != null) UpdateBlue else UpdateGreen
    val cardBrush = Brush.linearGradient(
        if (isDark) listOf(Color(0xE51A263B), Color(0xDF162032), Color(0xE50E192A))
        else listOf(Color(0xE7FFFFFF), Color(0xDFF4F8FF), Color(0xE7FFFFFF)))

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(WindowDim), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(
                listOf(accent.copy(alpha = if (isDark) .16f else .12f), Color.Transparent),
                radius = 760f)))

            val shape = RoundedCornerShape(34.dp)
            Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().widthIn(max = 440.dp)
                .graphicsLayer { translationY = floatY.dp.toPx() }
                .shadow(36.dp, shape, clip = false, ambientColor = accent.copy(alpha = .22f), spotColor = Color(0x99030A18))
                .clip(shape)
                .background(cardBrush)
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .88f), accent.copy(alpha = .20f), GlassEdge)), shape)
                .testTag("native-update-dialog")) {
                // Soft color blooms sit inside the glass rather than competing with the message.
                Box(Modifier.matchParentSize().background(Brush.radialGradient(
                    listOf(accent.copy(alpha = if (isDark) .14f else .09f), Color.Transparent), radius = 360f)))
                Box(Modifier.align(Alignment.TopEnd).offset(x = 54.dp, y = (-56).dp).size(150.dp)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = .19f), Color.Transparent)), CircleShape))

                Column(Modifier.padding(horizontal = 27.dp, vertical = 30.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    Box(Modifier.size(104.dp).shadow(22.dp, CircleShape, ambientColor = accent.copy(alpha = .35f), spotColor = accent.copy(alpha = .42f))
                        .clip(CircleShape).background(Brush.sweepGradient(listOf(UpdateCyan, UpdateBlue, accent, UpdateCyan)))
                        .padding(1.5.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(if (isDark) Color(0xFF263B59) else Color(0xFF7D96B5), if (isDark) Color(0xFF111B2C) else Color(0xFF9CAEC5))), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(if (release == null) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Box(Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 6.dp).size(17.dp)
                            .clip(CircleShape).background(if (ready) UpdateGreen else accent).border(2.dp, if (isDark) Color(0xFF172338) else Color.White, CircleShape))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (release != null) "تحديث جديد لنبض" else "التطبيق محدّث",
                            fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, color = TextMain, textAlign = TextAlign.Center)
                        Text(if (release != null) "نسخة جديدة جاهزة لك" else "أنت تستخدم أحدث نسخة من نبض الآن",
                            color = Muted, fontSize = 15.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
                    }

                    if (release != null) {
                        Row(Modifier.clip(CircleShape).background(if (isDark) Color(0xFF223149) else Color(0xFFEAF1FC))
                            .border(1.dp, accent.copy(alpha = .12f), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                            Text("${String.format(Locale.ROOT, "%.1f", release.size / 1_000_000.0)} MB",
                                color = if (isDark) Color(0xFFD5E4FA) else Color(0xFF51647F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("حجم التحديث", color = Muted, fontSize = 12.sp)
                        }
                    } else Spacer(Modifier.height(2.dp))

                    state.error?.let {
                        Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0x33FF6B81) else Color(0x18E11D48))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                            color = AccentLight, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                    }

                    val press = rememberPress()
                    val active = release != null && !downloading
                    Box(Modifier.fillMaxWidth().height(58.dp).shadow(if (active || downloading) 12.dp else 0.dp, CircleShape,
                        ambientColor = accent.copy(alpha = .28f), spotColor = accent.copy(alpha = .24f))
                        .clip(CircleShape)
                        .background(if (downloading) Brush.horizontalGradient(listOf(UpdateBlue, UpdateCyan))
                            else if (active) Brush.horizontalGradient(listOf(UpdateBlue, Color(0xFF5755E8), UpdateCyan))
                            else Brush.horizontalGradient(listOf(Raised, Raised)))
                        .pressClick(press = press, enabled = active) { if (ready) install() else download() }
                        .testTag("update-install"), contentAlignment = Alignment.Center) {
                        if (downloading) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).testTag("update-progress"),
                                    color = Color.White, trackColor = Color.White.copy(alpha = .28f))
                                Text("$percent%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        } else Text(if (ready) "تثبيت التحديث" else if (release != null) "حدّث نبض الآن" else "التطبيق محدّث",
                            color = if (active) Color.White else Muted, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("لاحقاً", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.pressClick(onClick = close).padding(horizontal = 16.dp, vertical = 8.dp).testTag("update-later"))
                        if (state.error != null) Text("إعادة المحاولة", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.pressClick(onClick = retry).padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    if (downloading) Text(if (state.phase == "verifying") "جارٍ تجهيز التثبيت…" else "جارٍ تنزيل التحديث…",
                        color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
