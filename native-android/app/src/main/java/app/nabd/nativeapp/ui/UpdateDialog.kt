package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nabd.nativeapp.UpdateState
import java.util.Locale

private val UpdateGreen = Color(0xFF10B981)
private val UpdateGrey = Color(0xFF94A3B8)

/**
 * Glassy update window. It shows only what the user asked for: a download icon,
 * a clear sentence, one big status button and "لاحقاً". No source, no version
 * number, no simulated progress — the bar follows the real download bytes.
 */
@Composable fun UpdateDialog(state: UpdateState, close: () -> Unit, download: () -> Unit, install: () -> Unit, retry: () -> Unit) {
    if (!state.visible) return
    val release = state.release
    val downloading = state.phase == "downloading" || state.phase == "verifying"
    val ready = state.phase == "ready"
    val fraction = if (state.total > 0L) (state.received.toFloat() / state.total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(WindowDim), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(28.dp)
            Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().widthIn(max = 440.dp)
                .nabdShadow(shape, elevation = 24.dp).clip(shape)
                .background(WindowFill)
                .border(1.dp, Color(0x80FFFFFF), shape).testTag("native-update-dialog")) {
                Box(Modifier.matchParentSize().glassBlur(30.dp).background(Color(0x1AFFFFFF)))
                Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(74.dp).clip(CircleShape).background(if (release != null) UpdateGreen else UpdateGrey), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Download, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Text(if (release != null) "التحديث متوفر" else "التطبيق محدّث", fontSize = 21.sp, fontWeight = FontWeight.Black, color = TextMain)
                    Text(if (release != null) "إصدار جديد جاهز. الحجم: ${String.format(Locale.ROOT, "%.1f", release.size / 1_000_000.0)} MB"
                        else "أنت تستخدم أحدث نسخة من نبض الآن",
                        color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                    state.error?.let { Text(it, color = AccentLight, fontSize = 13.sp, lineHeight = 20.sp) }
                    Spacer(Modifier.height(2.dp))
                    val press = rememberPress()
                    val active = release != null && !downloading
                    Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
                        .background(if (downloading) Accent else if (active) UpdateGreen else Raised)
                        .pressClick(press = press, enabled = active) { if (ready) install() else download() }
                        .testTag("update-install"), contentAlignment = Alignment.Center) {
                        if (downloading) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f).height(6.dp).testTag("update-progress"),
                                    color = Color.White, trackColor = Color(0x55FFFFFF))
                                Text("$percent%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        } else Text(if (ready) "إعادة التشغيل" else if (release != null) "تحديث الآن" else "التطبيق محدّث",
                            color = if (active) Color.White else Muted, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("لاحقاً", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressClick(onClick = close).padding(6.dp))
                        if (state.error != null) Text("إعادة المحاولة", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressClick(onClick = retry).padding(6.dp))
                    }
                    if (downloading) Text(if (state.phase == "verifying") "جارٍ تجهيز التثبيت…" else "جارٍ تنزيل التحديث…", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
