package app.nabd.nativeapp.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.ModeNight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.nabd.nativeapp.data.Device
import app.nabd.nativeapp.data.FeedPost
import app.nabd.nativeapp.data.Notification
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.Story
import app.nabd.nativeapp.data.UserSettings
import app.nabd.nativeapp.data.VISIBILITY_CHOICES
import app.nabd.nativeapp.data.autoplayLabel
import app.nabd.nativeapp.data.notifyLabel
import app.nabd.nativeapp.data.reachLabel
import app.nabd.nativeapp.data.themeLabel
import app.nabd.nativeapp.data.visibilityLabel
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal enum class SettingsPage { ROOT, ACCOUNT, SECURITY, DEVICES, PRIVACY, VISIBILITY, NOTIFICATIONS, APPEARANCE, DATA, ACTIVITY, HELP, ABOUT, BLOCKED, OWNERSHIP, CREDENTIALS }

private data class Entry(val title: String, val hint: String, val icon: ImageVector, val tint: Color,
    val danger: Boolean = false, val keywords: String = "", val value: String = "", val go: () -> Unit)

private val SettingsPurple = Color(0xFF7C3AED)
private val SettingsCyan = Color(0xFF0EA5E9)
private val SettingsAmber = Color(0xFFF59E0B)
private val SettingsGreen = Color(0xFF10B981)

/** Sections are rendered in the order Facebook uses, with the same glass language. */
@Composable private fun SettingsSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Text(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.padding(horizontal = 12.dp).nabdShadow(shape, elevation = 10.dp).clip(shape).background(CardFill).border(1.dp, GlassEdge, shape)) {
        Box(Modifier.matchParentSize().glassBlur(22.dp).background(GlassSheen))
        Column(Modifier.fillMaxWidth()) { content(this) }
    }
}

@Composable private fun IconDisc(icon: ImageVector, tint: Color) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Blue radio circle: a plain ring when idle, a filled dot inside the ring when chosen. */
@Composable internal fun RadioDot(selected: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        drawCircle(color = if (selected) Accent else Outline, radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        if (selected) drawCircle(color = Accent, radius = size.minDimension / 5.5f)
    }
}

/** One setting line: icon, title, a grey explanation under it and an arrow. */
@Composable private fun SettingsRow(entry: Entry, enabled: Boolean = true, testTag: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val press = rememberPress()
    Row(Modifier.fillMaxWidth()
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .pressClick(press = press, enabled = enabled, onClick = entry.go).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconDisc(entry.icon, if (entry.danger) AccentLight else entry.tint)
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = if (entry.danger) AccentLight else TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
            if (entry.hint.isNotBlank()) Text(entry.hint, color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        if (trailing != null) trailing()
        else if (entry.value.isNotBlank()) Text(entry.value, color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

/** A switch carries its explanation underneath, exactly like the rows. */
@Composable private fun ToggleRow(title: String, hint: String, icon: ImageVector, tint: Color, value: Boolean, testTag: String? = null, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (testTag != null) Modifier.testTag(testTag) else Modifier).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconDisc(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
            Text(hint, color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Switch(checked = value, onCheckedChange = onValue,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedThumbColor = Color.White, uncheckedTrackColor = Outline))
    }
}

/** A small black toast that rises from the bottom after every saved change. */
@Composable private fun BoxScope.SavedToast(visible: Boolean) {
    androidx.compose.animation.AnimatedVisibility(visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
        exit = androidx.compose.animation.fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE60A1628)).padding(horizontal = 18.dp, vertical = 11.dp)) {
            Text("تم حفظ الإعدادات", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun deviceAgo(at: Long): String {
    val minutes = java.time.Duration.ofMillis(System.currentTimeMillis() - at).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 2 -> "الآن"
        minutes < 60 -> "قبل $minutes دقيقة"
        minutes < 1440 -> "قبل ${minutes / 60} ساعة"
        else -> "قبل ${minutes / 1440} يوم"
    }
}

/**
 * The settings of the account. Every switch, audience and list here is written to
 * the account on the server, so it comes back on any other device that signs in.
 */
@Composable internal fun SettingsScreen(me: Person, people: List<Person>, settings: UserSettings, blocked: List<Person>,
    devices: List<Device>, posts: List<FeedPost>, notes: List<Notification>, stories: List<Story>, busy: Boolean, error: String?, version: String,
    onBack: () -> Unit, onSettings: (UserSettings) -> Unit, onBlock: (Person, Boolean) -> Unit, onLogoutDevice: (String) -> Unit,
    onLogoutAll: () -> Unit, onDeactivate: (String) -> Unit, onDelete: (String) -> Unit, onLogout: () -> Unit,
    onCredentials: (String, String?, String?) -> Unit, onProfile: () -> Unit, onReport: (String) -> Unit, onContact: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    var query by rememberSaveable { mutableStateOf("") }
    var field by rememberSaveable { mutableStateOf("") }
    var toast by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast) { delay(1600); toast = false } }
    val save: (UserSettings) -> Unit = { next -> if (next != settings) { onSettings(next); toast = true } }
    val context = LocalContext.current

    val account = listOf(
        Entry("الاسم", me.name, Icons.Outlined.Badge, Accent, keywords = "اسم name") { onProfile() },
        Entry("اسم المستخدم", "@${me.username}", Icons.Outlined.Person, Accent, keywords = "مستخدم username") { onProfile() },
        Entry("البريد الإلكتروني", "بدّلو من نفس نافذة كلمة السر", Icons.Outlined.Email, Accent, keywords = "بريد email") { page = SettingsPage.CREDENTIALS },
        Entry("تغيير كلمة السر", "خاصك تأكد بكلمة السر الحالية", Icons.Outlined.Lock, Accent, keywords = "كلمة السر password") { page = SettingsPage.CREDENTIALS },
        Entry("ملكية الحساب", "تعطيل مؤقت أو حذف نهائي", Icons.Outlined.DeleteForever, AccentLight, danger = true, keywords = "حذف تعطيل delete") { page = SettingsPage.OWNERSHIP })
    val security = listOf(
        Entry("الأجهزة المتصلة حالياً", "${devices.size} أجهزة فيها جلسة مفتوحة", Icons.Outlined.Devices, SettingsCyan, keywords = "أجهزة devices جلسات") { page = SettingsPage.DEVICES },
        Entry("تنبيهات تسجيل الدخول", "خبرني إلا دخل حسابي من جهاز جديد", Icons.Outlined.Security, SettingsCyan, keywords = "تنبيه دخول login") { page = SettingsPage.SECURITY })
    val privacy = listOf(
        Entry("من يشوف منشوراتي", visibilityLabel(settings.posts), Icons.Outlined.Visibility, SettingsPurple, keywords = "منشورات خصوصية") { field = "posts"; page = SettingsPage.VISIBILITY },
        Entry("من يشوف قصصي", visibilityLabel(settings.stories), Icons.Outlined.Visibility, SettingsPurple, keywords = "قصص ستوري") { field = "stories"; page = SettingsPage.VISIBILITY },
        Entry("من يراسلني", reachLabel(settings.messages), Icons.Outlined.MailOutline, SettingsPurple, keywords = "رسائل مراسلة") { field = "messages"; page = SettingsPage.PRIVACY },
        Entry("من يرسل لي طلب صداقة", reachLabel(settings.requests), Icons.Outlined.Group, SettingsPurple, keywords = "صداقة طلبات") { field = "requests"; page = SettingsPage.PRIVACY },
        Entry("قائمة الحظر", "${blocked.size} محظور", Icons.Outlined.Block, SettingsPurple, keywords = "حظر بلوك") { page = SettingsPage.BLOCKED })
    val appearance = listOf(
        Entry("الوضع الليلي", themeLabel(settings.theme), Icons.Outlined.ModeNight, SettingsAmber, keywords = "ليلي نهاري مظهر") { field = "theme"; page = SettingsPage.VISIBILITY },
        Entry("اللغة", "العربية", Icons.Outlined.Language, SettingsAmber, keywords = "لغة language") { field = "language"; page = SettingsPage.VISIBILITY },
        Entry("التشغيل التلقائي للفيديو", autoplayLabel(settings.autoplay), Icons.Outlined.Wifi, SettingsAmber, keywords = "فيديو تشغيل تلقائي") { field = "autoplay"; page = SettingsPage.VISIBILITY })
    val data = listOf(
        Entry("تنزيل نسخة من بياناتك", "ملف فيه منشوراتك وتعليقاتك وإعداداتك", Icons.Outlined.Download, SettingsGreen, keywords = "بيانات تنزيل export") { exportData(context, me, posts, notes, stories, settings, devices) },
        Entry("سجل النشاط", "منشوراتك، تعليقاتك وأجهزتك", Icons.Outlined.History, SettingsGreen, keywords = "نشاط سجل") { page = SettingsPage.ACTIVITY })
    val help = listOf(
        Entry("مركز المساعدة", "أجوبة على الأسئلة الشائعة", Icons.Outlined.HelpOutline, Muted, keywords = "مساعدة help") { info = "help" },
        Entry("الإبلاغ عن مشكلة", "وصف المشكلة وغتوصل للإدارة", Icons.Outlined.ReportProblem, Muted, keywords = "بلاغ مشكلة report") { info = "report" },
        Entry("تواصل معنا", "محادثة مباشرة مع إدارة نبض", Icons.Outlined.MailOutline, Muted, keywords = "تواصل اتصل contact") { onContact() })
    val about = listOf(
        Entry("الشروط", "قواعد استخدام نبض", Icons.Outlined.Policy, Muted, keywords = "شروط terms") { info = "terms" },
        Entry("سياسة الخصوصية", "شنو كنجمعو وشنو ما كنجمعوش", Icons.Outlined.PrivacyTip, Muted, keywords = "خصوصية privacy") { info = "privacy" })
    val searchable = account + security + privacy + appearance + data + help + about

    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCircle(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "رجوع", { if (page == SettingsPage.ROOT) onBack() else { page = SettingsPage.ROOT; query = "" } }, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(when (page) {
                    SettingsPage.ROOT -> "الإعدادات والخصوصية"
                    SettingsPage.ACCOUNT -> "الحساب"
                    SettingsPage.SECURITY -> "الأمان"
                    SettingsPage.DEVICES -> "الأجهزة المتصلة"
                    SettingsPage.PRIVACY -> "الخصوصية"
                    SettingsPage.VISIBILITY -> "الاختيار"
                    SettingsPage.NOTIFICATIONS -> "التنبيهات"
                    SettingsPage.APPEARANCE -> "المظهر والتفضيلات"
                    SettingsPage.DATA -> "بياناتك"
                    SettingsPage.ACTIVITY -> "سجل النشاط"
                    SettingsPage.HELP -> "المساعدة"
                    SettingsPage.ABOUT -> "حول التطبيق"
                    SettingsPage.BLOCKED -> "قائمة الحظر"
                    SettingsPage.OWNERSHIP -> "ملكية الحساب"
                    SettingsPage.CREDENTIALS -> "كلمة السر والبريد"
                }, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
        }
        if (page == SettingsPage.ROOT) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            NabdField(query, { query = it }, "ابحث في الإعدادات", Modifier.fillMaxWidth(), tag = "settings-search",
                trailingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted, modifier = Modifier.size(20.dp)) })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                SettingsPage.ROOT -> {
                    if (query.isBlank()) {
                        item { Box(Modifier.padding(horizontal = 12.dp)) { AccountCard(me, devices.size) { page = SettingsPage.ACCOUNT } } }
                        item { SettingsSection("الحساب") { account.forEach { SettingsRow(it) } } }
                        item { SettingsSection("الأمان") { security.forEach { SettingsRow(it) } } }
                        item { SettingsSection("الخصوصية") {
                            privacy.forEach { SettingsRow(it) }
                            ToggleRow("الحالة النشطة", "إظهار أنك متصل الآن", Icons.Outlined.Visibility, SettingsPurple, settings.activeStatus, "toggle-active") { save(settings.copy(activeStatus = it)) }
                        } }
                        item { SettingsSection("التنبيهات") {
                            SettingsRow(Entry("التنبيهات", "إقلاب كل صنف على حدة", Icons.Outlined.NotificationsActive, SettingsCyan, keywords = "تنبيهات notifications") { page = SettingsPage.NOTIFICATIONS })
                            ToggleRow("الوضع الهادئ", "إيقاف كل التنبيهات مؤقتاً", Icons.Outlined.NotificationsActive, SettingsCyan, settings.quiet, "toggle-quiet") { save(settings.copy(quiet = it)) }
                        } }
                        item { SettingsSection("المظهر والتفضيلات") {
                            appearance.forEach { SettingsRow(it) }
                            ToggleRow("أصوات التطبيق", "أصوات الضغط والتنبيهات", Icons.Outlined.VolumeUp, SettingsAmber, settings.sounds, "toggle-sounds") { save(settings.copy(sounds = it)) }
                        } }
                        item { SettingsSection("بياناتك") { data.forEach { SettingsRow(it) } } }
                        item { SettingsSection("المساعدة") { help.forEach { SettingsRow(it) } } }
                        item { SettingsSection("حول التطبيق") {
                            about.forEach { SettingsRow(it) }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconDisc(Icons.Outlined.Info, Muted)
                                Column(Modifier.weight(1f)) {
                                    Text("رقم الإصدار", color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                                    Text(version, color = Muted, fontSize = 11.5.sp)
                                }
                            }
                        } }
                        item { Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            PillButton("تسجيل الخروج", { confirm = "logout" }, modifier = Modifier.fillMaxWidth(), brush = NabdRed, icon = Icons.Outlined.Logout, testTag = "settings-logout")
                        } }
                    } else {
                        val found = searchable.filter { query.trim() in it.title || query.trim() in it.hint || query.trim() in it.keywords }
                        if (found.isEmpty()) item { EmptyState(Icons.Outlined.Search, "ما لقينا والو", "جرّب كلمة أخرى بحال «خصوصية» ولا «أجهزة».", modifier = Modifier.padding(horizontal = 12.dp)) }
                        else item { SettingsSection("النتائج") { found.forEach { SettingsRow(it) } } }
                    }
                }
                SettingsPage.ACCOUNT -> item { SettingsSection { account.forEach { SettingsRow(it) } } }
                SettingsPage.SECURITY -> item { SettingsSection {
                    ToggleRow("تنبيهات تسجيل الدخول", "إلا تسجّل حسابك من جهاز ما كنعرفوش", Icons.Outlined.Security, SettingsCyan, settings.loginAlerts, "toggle-alerts") { save(settings.copy(loginAlerts = it)) }
                    SettingsRow(Entry("الأجهزة المتصلة حالياً", "${devices.size} أجهزة", Icons.Outlined.Devices, SettingsCyan) { page = SettingsPage.DEVICES })
                } }
                SettingsPage.DEVICES -> {
                    if (devices.isEmpty()) item { EmptyState(Icons.Outlined.Devices, "ما كاين تا جهاز", "الأجهزة اللي دخلات بحسابك غيبانو هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
                    else {
                        item { SettingsSection("الأجهزة") { devices.forEach { device ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconDisc(Icons.Outlined.Smartphone, SettingsCyan)
                                Column(Modifier.weight(1f)) {
                                    Text(device.label, color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                                    Text(if (device.current) "هذا الجهاز · ${deviceAgo(device.lastActive)}" else "آخر نشاط ${deviceAgo(device.lastActive)}", color = Muted, fontSize = 11.5.sp)
                                }
                                if (!device.current) PillButton("خروج", { onLogoutDevice(device.id) }, enabled = !busy, brush = null, height = 36.dp, fontSize = 12)
                            }
                        } } }
                        item { Box(Modifier.padding(horizontal = 12.dp)) {
                            PillButton("الخروج من كل الأجهزة", { confirm = "devices" }, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = NabdRed, icon = Icons.Outlined.Logout)
                        } }
                    }
                }
                SettingsPage.PRIVACY -> item { SettingsSection("من يوصلني") {
                    listOf("everyone" to "الجميع", "friends" to "الأصدقاء فقط").forEach { (value, label) ->
                        val entry = Entry(label, if (value == "friends") "غير أصدقائك ما غيقدّوش يوصلوك" else "أي حساب فنبض يقدر يوصللك",
                            if (value == "friends") Icons.Outlined.Group else Icons.Outlined.MailOutline, SettingsPurple, value = "") {
                            val next = when (field) { "messages" -> settings.copy(messages = value); else -> settings.copy(requests = value) }
                            save(next); page = SettingsPage.ROOT
                        }
                        Row(Modifier.fillMaxWidth().pressClick(enabled = !busy, onClick = entry.go).padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioDot(selected = (if (field == "messages") settings.messages else settings.requests) == value)
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                                Text(entry.hint, color = Muted, fontSize = 11.5.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                } }
                SettingsPage.VISIBILITY -> item { SettingsSection(if (field == "theme") "الوضع الليلي" else if (field == "autoplay") "التشغيل التلقائي" else if (field == "language") "اللغة" else "من يشوف") {
                    val choices = when (field) {
                        "theme" -> listOf("system" to "حسب النظام", "light" to "نهاري", "dark" to "ليلي")
                        "autoplay" -> listOf("always" to "دائماً", "wifi" to "على الواي فاي فقط", "never" to "أبداً")
                        "language" -> listOf("ar" to "العربية", "en" to "الإنجليزية")
                        else -> VISIBILITY_CHOICES.map { it to visibilityLabel(it) }
                    }
                    val current = when (field) {
                        "theme" -> settings.theme; "autoplay" -> settings.autoplay; "language" -> settings.language
                        "posts" -> settings.posts; "stories" -> settings.stories; else -> "public"
                    }
                    choices.forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().pressClick(enabled = !busy) {
                            val next = when (field) {
                                "theme" -> settings.copy(theme = value); "autoplay" -> settings.copy(autoplay = value)
                                "language" -> settings.copy(language = value); "posts" -> settings.copy(posts = value)
                                else -> settings.copy(stories = value)
                            }
                            save(next); page = SettingsPage.ROOT
                        }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioDot(selected = current == value)
                            Text(label, color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                } }
                SettingsPage.NOTIFICATIONS -> item { SettingsSection { app.nabd.nativeapp.data.NOTIFY_KINDS.forEach { kind ->
                    ToggleRow(notifyLabel(kind), "واش باغي توصلك تنبيهات ${notifyLabel(kind)}", Icons.Outlined.NotificationsActive, SettingsCyan,
                        settings.notify[kind] != false, "notify-$kind") { save(settings.copy(notify = settings.notify + (kind to it))) }
                } } }
                SettingsPage.APPEARANCE -> item { SettingsSection { appearance.forEach { SettingsRow(it) } } }
                SettingsPage.DATA -> item { SettingsSection { data.forEach { SettingsRow(it) } } }
                SettingsPage.ACTIVITY -> {
                    val mine = posts.filter { it.author.id == me.id }
                    val comments = posts.flatMap { p -> p.comments.filter { it.author.id == me.id }.map { p to it } }
                    if (mine.isEmpty() && comments.isEmpty()) item { EmptyState(Icons.Outlined.History, "ما كاين تا نشاط", "منشوراتك وتعليقاتك غيبانو هنا.", modifier = Modifier.padding(horizontal = 12.dp)) }
                    else item { SettingsSection("آخر نشاط") {
                        devices.forEach { device -> Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconDisc(Icons.Outlined.Smartphone, SettingsGreen)
                            Column(Modifier.weight(1f)) {
                                Text("تسجيل الدخول من ${device.label}", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text(deviceAgo(device.lastActive), color = Muted, fontSize = 11.5.sp)
                            }
                        } }
                        mine.take(30).forEach { post -> Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconDisc(Icons.Outlined.ManageAccounts, SettingsGreen)
                            Column(Modifier.weight(1f)) {
                                Text(post.text.ifBlank { "منشور بصورة" }, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("منشور · ${relativePostDate(post.date)}", color = Muted, fontSize = 11.5.sp)
                            }
                        } }
                        comments.take(30).forEach { (post, comment) -> Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconDisc(Icons.Outlined.ChevronRight, SettingsGreen)
                            Column(Modifier.weight(1f)) {
                                Text(comment.text, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("تعليق على منشور · ${relativePostDate(comment.date)}", color = Muted, fontSize = 11.5.sp)
                            }
                        } }
                    } }
                }
                SettingsPage.HELP -> item { SettingsSection { help.forEach { SettingsRow(it) } } }
                SettingsPage.ABOUT -> item { SettingsSection { about.forEach { SettingsRow(it) } } }
                SettingsPage.BLOCKED -> {
                    item { SettingsSection("المحظورون") {
                        if (blocked.isEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp)) {
                            Text("ما حظرتي تا شي حساب.", color = Muted, fontSize = 13.sp)
                        } else blocked.forEach { person ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(person, 44)
                                Column(Modifier.weight(1f)) {
                                    Text(person.name, color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                                    Text("@${person.username}", color = Muted, fontSize = 11.5.sp)
                                }
                                PillButton("إلغاء", { onBlock(person, false) }, enabled = !busy, brush = null, height = 36.dp, fontSize = 12)
                            }
                        }
                    } }
                    item { SettingsSection("حظر حساب") {
                        people.filter { p -> p.id != me.id && blocked.none { it.id == p.id } }.take(40).forEach { person ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(person, 44)
                                Column(Modifier.weight(1f)) {
                                    Text(person.name, color = TextMain, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                                    Text("@${person.username}", color = Muted, fontSize = 11.5.sp)
                                }
                                PillButton("حظر", { onBlock(person, true) }, enabled = !busy, brush = null, height = 36.dp, fontSize = 12)
                            }
                        }
                    } }
                }
                SettingsPage.OWNERSHIP -> {
                    item { SettingsSection("تعطيل مؤقت") {
                        SettingsRow(Entry("تعطيل الحساب مؤقتاً", "كيتخبّى على كاع المستخدمين حتى ترجع تدخل", Icons.Outlined.Lock, AccentLight, danger = true) { confirm = "deactivate" })
                    } }
                    item { SettingsSection("حذف نهائي") {
                        SettingsRow(Entry("حذف الحساب نهائياً", "منشوراتك وتعليقاتك ورسائلك كتمشى معاه", Icons.Outlined.DeleteForever, AccentLight, danger = true) { confirm = "delete" })
                    } }
                }
                SettingsPage.CREDENTIALS -> item { CredentialsCard(busy, error, { page = SettingsPage.ROOT }, onCredentials) }
            }
            if (page == SettingsPage.ROOT && query.isBlank()) item { Spacer(Modifier.height(8.dp)) }
        }
        if (error != null && page != SettingsPage.CREDENTIALS) Text(error, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            color = AccentLight, fontSize = 12.5.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth()) { SavedToast(toast) }
    }

    when (confirm) {
        "logout" -> GlassConfirm("تسجيل الخروج؟", "حسابك غيبقى محفوظ فهاد الجهاز. تقدر ترجع ليه بضغطة وحدة.", "خروج",
            onConfirm = { confirm = null; onLogout() }, close = { confirm = null }, busy = busy, danger = true)
        "devices" -> GlassConfirm("الخروج من كل الأجهزة؟", "كل الجلسات غتسجّل الخروج من غير هاد الجهاز.", "خروج",
            onConfirm = { confirm = null; onLogoutAll() }, close = { confirm = null }, busy = busy, danger = true)
        else -> Unit
    }
    if (confirm == "deactivate") PasswordWindow("تعطيل الحساب", "دخل كلمة السر باش تخبّى حسابك على الجميع. كترجعو غير منين تدخل again.", "تعطيل", busy,
        close = { confirm = null }, confirm2 = { onDeactivate(it); confirm = null })
    if (confirm == "delete") PasswordWindow("حذف الحساب", "هاد الإجراء ما كيرجعش. دخل كلمة السر باش تمسح حسابك وكلشي تابع ليه.", "حذف نهائي", busy,
        close = { confirm = null }, confirm2 = { onDelete(it); confirm = null })
    info?.let { key -> InfoWindow(key, close = { info = null }, onReport = { text -> info = null; onReport(text) }) }
}

@Composable private fun AccountCard(me: Person, devices: Int, open: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().nabdShadow(shape, elevation = 10.dp).clip(shape).background(CardFill).border(1.dp, GlassEdge, shape)
        .pressClick(onClick = open).testTag("settings-account")) {
        Box(Modifier.matchParentSize().glassBlur(22.dp).background(GlassSheen))
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(me, 56)
            Column(Modifier.weight(1f)) {
                PersonName(me, size = 16)
                Text("إدارة معلومات حسابك · $devices أجهزة", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Full-screen glass credentials: the current password, a new one and a new email. */
@Composable private fun CredentialsCard(busy: Boolean, serviceError: String?, close: () -> Unit, save: (String, String?, String?) -> Unit) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    Box(Modifier.padding(horizontal = 12.dp)) {
        SettingsSection {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NabdField(current, { current = it }, "كلمة السر الحالية", Modifier.fillMaxWidth(), enabled = !busy, tag = "credential-current",
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                NabdField(password, { password = it.take(128) }, "كلمة سر جديدة (اختياري)", Modifier.fillMaxWidth(), enabled = !busy, tag = "credential-password",
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                NabdField(email, { email = it }, "بريد جديد (اختياري)", Modifier.fillMaxWidth(), enabled = !busy, tag = "credential-email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Text("تغيير كلمة السر كيسجّل خروج باقي الأجهزة، وهذا الجهاز كيبقى مسجّل. الطول الأدنى 8 أحرف.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                if (localError != null) Text(localError!!, color = AccentLight, fontSize = 12.sp)
                if (serviceError != null) Text(serviceError, color = AccentLight, fontSize = 12.sp, lineHeight = 18.sp)
                PillButton("حفظ", {
                    val next = password.trim().ifEmpty { null }; val mail = email.trim().ifEmpty { null }
                    when {
                        current.isEmpty() -> localError = "دخل كلمة السر الحالية"
                        next != null && next.length < 8 -> localError = "كلمة السر الجديدة قصيرة"
                        next == null && mail == null -> localError = "بدّل كلمة السر أو البريد"
                        else -> { localError = null; save(current, next, mail) }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy, testTag = "credentials-save")
                PillButton("إلغاء", close, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = null, height = 46.dp, fontSize = 14)
            }
        }
    }
}

@Composable private fun PasswordWindow(title: String, text: String, label: String, busy: Boolean, close: () -> Unit, confirm2: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    GlassConfirm(title, text, label, onConfirm = {
        if (password.length < 8) problem = "كلمة السر غالطة" else confirm2(password)
    }, close = close, busy = busy, danger = true) {
        NabdField(password, { password = it.take(128) }, "كلمة السر", Modifier.fillMaxWidth(), enabled = !busy, tag = "ownership-password",
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (problem != null) Text(problem!!, color = AccentLight, fontSize = 12.sp)
    }
}

@Composable private fun InfoWindow(key: String, close: () -> Unit, onReport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    GlassWindow(close = close) {
        when (key) {
            "help" -> {
                IconPlate(Icons.Outlined.HelpOutline, size = 58.dp, tint = Accent)
                Text("مركز المساعدة", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("نبض كيخدم بلا سيرفر خاص: البيانات كتسافر عبر قناة Telegram ديال الحساب الرسمي. المنشورات كتبقى 24 ساعة فالقصص، والنقاط كتزيد بتسجيل الدخول اليومي والنشر والدعوة. إلا نسّيتي كلمة السر، ما كاين تا شي رابط خارجي كيبدّلو: خاص الإدارة.",
                    color = Muted, fontSize = 13.5.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            }
            "report" -> {
                IconPlate(Icons.Outlined.ReportProblem, size = 58.dp, tint = AccentLight)
                Text("الإبلاغ عن مشكلة", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
                NabdField(text, { text = it.take(500) }, "وصف المشكلة…", Modifier.fillMaxWidth().height(120.dp), singleLine = false, maxLines = 5, tag = "report-text")
                PillButton("إرسال البلاغ", { if (text.trim().length >= 5) onReport(text.trim()) }, enabled = text.trim().length >= 5,
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.ReportProblem)
            }
            "terms" -> {
                IconPlate(Icons.Outlined.Policy, size = 58.dp, tint = Accent)
                Text("الشروط", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("الاستخدام مسموح غير بحساب واحد حقيقي لكل شخص. ممنوع النشر اللي فيه سب، تهديد، تشهير، ولا محتوى مخالف. الإدارة تقدر تقيّد ولا تحجب أي حساب كيخالف، وكلشي منشور كيبقى ظاهر لكاع المستخدمين إلا حطّيتيه «الأصدقاء فقط».",
                    color = Muted, fontSize = 13.5.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            }
            else -> {
                IconPlate(Icons.Outlined.PrivacyTip, size = 58.dp, tint = Accent)
                Text("سياسة الخصوصية", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("كنديرو غير بالمعطيات اللي كتبعثها: الاسم، اسم المستخدم، البريد وكلمة السر مشفّرة. كلمة السر ما كتحفظش فالجهاز، والجلسة كتبقى صالحة 30 يوم. ما كنبيعو والو وما كنشاركو المعطيات مع تا طرف ثالث، وتقدر تنزّل نسخة من بياناتك من الإعدادات فأي وقت.",
                    color = Muted, fontSize = 13.5.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            }
        }
        PillButton("إغلاق", close, modifier = Modifier.fillMaxWidth(), brush = null, height = 46.dp, fontSize = 14)
    }
}

/**
 * The real export: the account, its posts, comments, stories, devices and
 * preferences, written to a file the user shares wherever they want.
 */
private fun exportData(context: android.content.Context, me: Person, posts: List<FeedPost>, notes: List<Notification>,
    stories: List<Story>, settings: UserSettings, devices: List<Device>) {
    val mine = posts.filter { it.author.id == me.id }
    val json = JSONObject()
        .put("account", JSONObject().put("id", me.id).put("name", me.name).put("username", me.username).put("email", "")
            .put("bio", me.bio).put("points", me.points))
        .put("posts", JSONArray(mine.map { p -> JSONObject().put("id", p.id).put("text", p.text).put("date", p.date).put("likes", p.likes) }))
        .put("comments", JSONArray(mine.flatMap { p -> p.comments.filter { it.author.id == me.id }.map { c -> JSONObject().put("post", p.id).put("text", c.text).put("date", c.date) } }))
        .put("stories", JSONArray(stories.filter { it.owner.id == me.id }.map { JSONObject().put("id", it.id).put("text", it.text).put("date", it.date) }))
        .put("notifications", JSONArray(notes.map { JSONObject().put("kind", it.kind).put("date", it.date).put("read", it.read) }))
        .put("devices", JSONArray(devices.map { JSONObject().put("label", it.label).put("lastActive", it.lastActive) }))
        .put("settings", app.nabd.nativeapp.data.writeSettings(settings))
    val folder = File(context.cacheDir, "shared-media").apply { mkdirs() }
    val file = File(folder, "nabd-data-${me.username}.json").apply { writeText(json.toString(2)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.native.updates", file)
    val intent = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "نسخة من بياناتك")) }
}
