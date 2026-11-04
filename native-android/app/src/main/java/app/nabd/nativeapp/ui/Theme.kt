package app.nabd.nativeapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import app.nabd.nativeapp.R

/**
 * Light "iOS glass" palette taken from the supplied Nabd Pro reference:
 * blue primary, red for نبضة reactions, white translucent cards on a soft blue canvas.
 */
val Canvas = Color(0xFFF0F4F9)
val Surface = Color(0xFFF7FAFF)
val Raised = Color(0xFFF1F5F9)
val Outline = Color(0xFFCBD5E1)
val TextMain = Color(0xFF0A1628)
val Muted = Color(0xFF4B5E78)
val Accent = Color(0xFF0866FF)
val AccentLight = Color(0xFFE11D48)
/** Translucent card fill used by the glass app bar, composer, stories and posts. */
val Glass = Color(0xC7FFFFFF)
val Hairline = Color(0xFFE6EDF9)

private val Arabic = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_bold, FontWeight.Bold))
private fun TextStyle.arabic() = copy(fontFamily = Arabic, platformStyle = PlatformTextStyle(includeFontPadding = false))
private val Type = Typography().let { t -> Typography(
    displayLarge=t.displayLarge.arabic(), displayMedium=t.displayMedium.arabic(), displaySmall=t.displaySmall.arabic(),
    headlineLarge=t.headlineLarge.arabic(), headlineMedium=t.headlineMedium.arabic(), headlineSmall=t.headlineSmall.arabic(),
    titleLarge=t.titleLarge.arabic(), titleMedium=t.titleMedium.arabic(), titleSmall=t.titleSmall.arabic(),
    bodyLarge=t.bodyLarge.arabic(), bodyMedium=t.bodyMedium.arabic(), bodySmall=t.bodySmall.arabic(),
    labelLarge=t.labelLarge.arabic(), labelMedium=t.labelMedium.arabic(), labelSmall=t.labelSmall.arabic()
) }
/** Floating white card used by posts, chats, alerts and menu rows. */
@Composable fun NabdCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth()
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 10.dp,
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base) {
        Column(Modifier.fillMaxWidth()) { content(this) }
    }
}

@Composable fun NabdTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = Type, colorScheme = lightColorScheme(primary = Accent, onPrimary = Color.White, primaryContainer = Accent.copy(alpha = .14f), onPrimaryContainer = Accent,
        secondary = AccentLight, onSecondary = Color.White, background = Canvas, onBackground = TextMain, surface = Surface, onSurface = TextMain,
        surfaceVariant = Raised, onSurfaceVariant = Muted, outline = Outline, error = AccentLight, scrim = Color(0x660A1628))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
