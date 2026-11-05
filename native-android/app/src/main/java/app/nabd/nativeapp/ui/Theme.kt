package app.nabd.nativeapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nabd.nativeapp.R

/**
 * Nabd design system palette.
 * Canvas #EAF0F8, cards white at 88% with real blur, titles #0F172A,
 * secondary text #64748B, fields #F1F5F9 and the blue gradient #0866FF → #00A3FF.
 */
val Canvas = Color(0xFFEAF0F8)
val Raised = Color(0xFFF1F5F9)
val Outline = Color(0xFFCBD5E1)
val TextMain = Color(0xFF0F172A)
val Muted = Color(0xFF64748B)
val Accent = Color(0xFF0866FF)
val AccentLight = Color(0xFFE11D48)
/** Card fill kept as a separate name so material's Surface composable stays unambiguous. */
val Surface = CardFill
val Glass = CardFill
val Hairline = Color(0xFFE6EDF9)

private val ArabicFontFamily = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_bold, FontWeight.Bold))
/** Modern rounded Arabic face used by every string in the app. */
internal val ArabicFont: FontFamily get() = ArabicFontFamily
private fun TextStyle.arabic() = copy(fontFamily = ArabicFontFamily, platformStyle = PlatformTextStyle(includeFontPadding = false))
private val Type = Typography().let { t -> Typography(
    displayLarge=t.displayLarge.arabic(), displayMedium=t.displayMedium.arabic(), displaySmall=t.displaySmall.arabic(),
    headlineLarge=t.headlineLarge.arabic(), headlineMedium=t.headlineMedium.arabic(), headlineSmall=t.headlineSmall.arabic(),
    titleLarge=t.titleLarge.arabic(), titleMedium=t.titleMedium.arabic(), titleSmall=t.titleSmall.arabic(),
    bodyLarge=t.bodyLarge.arabic(), bodyMedium=t.bodyMedium.arabic(), bodySmall=t.bodySmall.arabic(),
    labelLarge=t.labelLarge.arabic(), labelMedium=t.labelMedium.arabic(), labelSmall=t.labelSmall.arabic()
) }

/** The single glass card of the app: 24dp corners, deep soft shadow, spring press. */
@Composable fun NabdCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = modifier, onClick = onClick, radius = 24.dp) { content(this) }
}

@Composable fun NabdTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = Type, colorScheme = lightColorScheme(primary = Accent, onPrimary = Color.White, primaryContainer = Accent.copy(alpha = .14f), onPrimaryContainer = Accent,
        secondary = AccentLight, onSecondary = Color.White, background = Canvas, onBackground = TextMain, surface = CardFill, onSurface = TextMain,
        surfaceVariant = Raised, onSurfaceVariant = Muted, outline = Outline, error = AccentLight, scrim = WindowDim)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
