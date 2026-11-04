package app.nabd.nativeapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import app.nabd.nativeapp.R

val Canvas = Color(0xFF0B0B0F)
val Surface = Color(0xFF16161C)
val Raised = Color(0xFF1F1F27)
val Outline = Color(0xFF272731)
val TextMain = Color(0xFFF4F4F6)
val Muted = Color(0xFF9A9AA8)
val Accent = Color(0xFFE8264A)
val AccentLight = Color(0xFFF34361)
private val Arabic = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_bold, FontWeight.Bold))
private fun TextStyle.arabic() = copy(fontFamily = Arabic, platformStyle = PlatformTextStyle(includeFontPadding = false))
private val Type = Typography().let { t -> Typography(
    displayLarge=t.displayLarge.arabic(), displayMedium=t.displayMedium.arabic(), displaySmall=t.displaySmall.arabic(),
    headlineLarge=t.headlineLarge.arabic(), headlineMedium=t.headlineMedium.arabic(), headlineSmall=t.headlineSmall.arabic(),
    titleLarge=t.titleLarge.arabic(), titleMedium=t.titleMedium.arabic(), titleSmall=t.titleSmall.arabic(),
    bodyLarge=t.bodyLarge.arabic(), bodyMedium=t.bodyMedium.arabic(), bodySmall=t.bodySmall.arabic(),
    labelLarge=t.labelLarge.arabic(), labelMedium=t.labelMedium.arabic(), labelSmall=t.labelSmall.arabic()
) }
@Composable fun NabdTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = Type, colorScheme = darkColorScheme(primary = Accent, onPrimary = Color.White, primaryContainer = Accent.copy(alpha=.16f), onPrimaryContainer = AccentLight,
        secondary = AccentLight, background = Canvas, onBackground = TextMain, surface = Surface, onSurface = TextMain, surfaceVariant = Raised, onSurfaceVariant = Muted, outline = Outline, error = Color(0xFFFF879B))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
