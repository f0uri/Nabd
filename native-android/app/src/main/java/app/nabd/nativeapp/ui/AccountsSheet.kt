package app.nabd.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.SavedAccount

/**
 * Account switcher. It lists every account a real session was issued for on this
 * device, so signing out becomes a choice instead of a dead end. Nothing is
 * invented here: the rows come from the encrypted local address book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AccountsSheet(me: Person, accounts: List<SavedAccount>, busy: Boolean, error: String?,
    close: () -> Unit, onSwitch: (String) -> Unit, onForget: (String) -> Unit, onLogout: () -> Unit, onAdd: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<SavedAccount?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val others = remember(accounts, me) { accounts.filter { it.id != me.id } }
    ModalBottomSheet(onDismissRequest = close, sheetState = state,
        scrimColor = WindowDim, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = WindowFill,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Outline)) }) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().glassBlur(28.dp).background(Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x14F1F5FF)))))
            Column(Modifier.fillMaxWidth().testTag("accounts-sheet").navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("الحسابات", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), color = TextMain,
                    fontSize = 21.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                // The account in use right now.
                NabdCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(me, 56)
                        Column(Modifier.weight(1f)) {
                            PersonName(me, size = 16)
                            Text("@${me.username}", color = Muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.clip(CircleShape).background(Accent.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text("الحساب الحالي", color = Accent, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                if (others.isNotEmpty()) {
                    Text("حسابات محفوظة", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(others, key = { it.id }) { entry ->
                                AccountRow(entry, busy, onClick = { onSwitch(entry.id) }, onForget = { forgetting = entry })
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("إضافة حساب", onAdd, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = null,
                        icon = Icons.Outlined.PersonAdd, testTag = "accounts-add")
                    PillButton("تسجيل الخروج", { confirmLogout = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        brush = NabdRed, icon = Icons.Outlined.Logout, testTag = "accounts-logout")
                    PillButton("إلغاء", close, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
                }
                if (error != null) Text(error, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    color = AccentLight, fontSize = 12.5.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (confirmLogout) GlassConfirm("تسجيل الخروج؟", "حسابك ومنشوراتك غيبقاو محفوظين. غتحتاج تسجّل الدخول من جديد.", "خروج",
        onConfirm = { confirmLogout = false; onLogout() }, close = { confirmLogout = false }, busy = busy, danger = true, confirmTag = "accounts-logout-confirm")
    forgetting?.let { entry -> GlassConfirm("نسيان هاد الحساب؟", "غيتحيّد من هاد الجهاز فقط وغتحتاج كلمة المرور باش ترجّعو.", "نسيان",
        onConfirm = { forgetting = null; onForget(entry.id) }, close = { forgetting = null }, busy = busy, danger = true) }
}

@Composable private fun AccountRow(entry: SavedAccount, busy: Boolean, onClick: () -> Unit, onForget: () -> Unit) {
    NabdCard(modifier = Modifier.testTag("account-${entry.id}"), onClick = if (busy) null else onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountAvatar(entry, 52)
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${entry.username}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassCircle(Icons.Outlined.Close, "نسيان ${entry.name}", onForget, enabled = !busy, size = 32.dp, tint = Muted)
        }
    }
}

/** Saved accounts are drawn from their real stored profile, never a placeholder number. */
@Composable internal fun AccountAvatar(entry: SavedAccount, size: Int) {
    val colors = listOf(Brush.linearGradient(listOf(Color(0xFF0866FF), Color(0xFF38BDF8))), Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFFFB7185))),
        Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF7DD3FC))), Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF60A5FA))))
    val brush = colors[(entry.id.hashCode() and Int.MAX_VALUE) % colors.size]
    NabdImage(entry.avatar, "صورة ${entry.name}", Modifier.size(size.dp).clip(CircleShape).background(brush).border(2.dp, Color.White, CircleShape),
        androidx.compose.ui.layout.ContentScale.Crop) {
        Text(entry.name.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "ن", color = Color.White, fontWeight = FontWeight.Black,
            fontSize = (size * .38f).sp, lineHeight = (size * .45f).sp)
    }
}
