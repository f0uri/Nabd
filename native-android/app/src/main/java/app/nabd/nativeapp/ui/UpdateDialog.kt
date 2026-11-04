package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** UI reads the real download state. No simulated timer/progress or silent install. */
@Composable fun UpdateDialog(state: UpdateState, close: () -> Unit, download: () -> Unit, install: () -> Unit, retry: () -> Unit) {
    if (!state.visible) return
    val working = state.phase in listOf("downloading", "verifying", "checking")
    val release = state.release
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(36.dp)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 32.dp).widthIn(max = 500.dp).fillMaxWidth().heightIn(max = 720.dp)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .5f), Outline, AccentLight.copy(alpha = .45f))), shape)
            .background(Brush.linearGradient(listOf(Color(0xF12B2631), Color(0xEB1B1B24), Color(0xF51B1B23))), shape)
            .padding(24.dp).testTag("native-update-dialog"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).background(Accent.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SystemUpdate, null, tint = AccentLight, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(if (release != null) "تحديث متوفر" else if (state.phase == "none") "التطبيق محدّث" else "تحديث نبض", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    release?.let { Text("نبض ${it.versionName} · ${String.format(Locale.ROOT, "%.1f", it.size / 1_000_000.0)} MB", color = Muted, fontSize = 13.sp) }
                }
                IconButton(onClick = close) { Icon(Icons.Outlined.Close, "إغلاق") }
            }
            if (release != null) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("شنو الجديد", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    release.notes.forEach { note -> Text("• $note", color = TextMain, lineHeight = 25.sp) }
                }
            }
            if (state.phase == "downloading" || state.phase == "verifying") {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    val fraction = if (state.total > 0) (state.received.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).testTag("update-progress"), color = AccentLight, trackColor = Outline)
                    Text(if (state.phase == "verifying") "جارٍ التحقق من الملف والتوقيع…" else "${(fraction * 100).toInt()}% · جارٍ تنزيل تحديث نبض", color = Muted, fontSize = 13.sp)
                }
            }
            if (state.phase == "none") Text("ما كاين حتى تحديث متوافق أحدث من النسخة المثبّتة.", color = Muted)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = close) { Text(if (working) "إلغاء" else "لاحقاً", color = Muted) }
                Spacer(Modifier.width(8.dp))
                if (release != null) Button(onClick = { if (state.phase == "ready") install() else download() }, enabled = !working,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White), shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp).testTag("update-install")) {
                    Text(if (state.phase == "ready" || working) "تثبيت" else if (state.phase == "error") "إعادة المحاولة" else "تنزيل التحديث", fontWeight = FontWeight.Bold)
                } else if (state.phase == "error") Button(onClick = retry) { Text("إعادة المحاولة") }
            }
            Text(if (state.phase == "ready") "الملف متحقق منه. Android غيطلب تأكيد التثبيت؛ إذا طلب السماح من هاد التطبيق، فعّلو وعاود ضغط تثبيت." else "المصدر: GitHub Releases · لن يتم التثبيت بلا موافقتك", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}
