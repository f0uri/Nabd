package app.nabd.nativeapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * The one switch of the whole app. Every colour below is read from it, so turning
 * it on repaints every screen, window and chat at once, without a restart.
 */
object NabdThemeState { var dark by mutableStateOf(false) }

private data class NabdPalette(
    val canvas: Color, val card: Color, val window: Color, val dim: Color, val text: Color, val muted: Color,
    val raised: Color, val outline: Color, val hairline: Color, val accent: Color, val accentLight: Color,
    val ink: Color, val edge: Color, val sheen: Color, val scrim: Color, val scheme: ColorScheme
)

/** Nabd design system, light: canvas #EAF0F8, white glass, titles #0F172A, secondary #64748B. */
private fun lightPalette() = NabdPalette(
    canvas = Color(0xFFEAF0F8), card = Color(0xE0FFFFFF), window = Color(0xEBFFFFFF), dim = Color(0x660A1628),
    text = Color(0xFF0F172A), muted = Color(0xFF64748B), raised = Color(0xFFF1F5F9), outline = Color(0xFFCBD5E1),
    hairline = Color(0xFFE6EDF9), accent = Color(0xFF0866FF), accentLight = Color(0xFFE11D48), ink = Color(0x1A0F172A),
    edge = Color(0x66FFFFFF), sheen = Color(0x1AFFFFFF), scrim = Color(0x660A1628),
    scheme = lightColorScheme(primary = Color(0xFF0866FF), onPrimary = Color.White, primaryContainer = Color(0x240866FF),
        onPrimaryContainer = Color(0xFF0866FF), secondary = Color(0xFFE11D48), onSecondary = Color.White,
        background = Color(0xFFEAF0F8), onBackground = Color(0xFF0F172A), surface = Color(0xE0FFFFFF),
        onSurface = Color(0xFF0F172A), surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFFCBD5E1), error = Color(0xFFE11D48), scrim = Color(0x660A1628))
)

/** Dark glass: canvas #0B0F19, cards #151C2C with #26324D hairlines and light text. */
private fun darkPalette() = NabdPalette(
    canvas = Color(0xFF0B0F19), card = Color(0xE6151C2C), window = Color(0xF2151C2C), dim = Color(0xB3000000),
    text = Color(0xFFF1F5F9), muted = Color(0xFF94A3B8), raised = Color(0xFF1B2436), outline = Color(0xFF26324D),
    hairline = Color(0xFF26324D), accent = Color(0xFF4C8DFF), accentLight = Color(0xFFFF6B81), ink = Color(0x66000000),
    edge = Color(0x8026324D), sheen = Color(0x14FFFFFF), scrim = Color(0xB3000000),
    scheme = darkColorScheme(primary = Color(0xFF4C8DFF), onPrimary = Color(0xFF08101F), primaryContainer = Color(0x334C8DFF),
        onPrimaryContainer = Color(0xFFBBD6FF), secondary = Color(0xFFFF6B81), onSecondary = Color(0xFF2A0710),
        background = Color(0xFF0B0F19), onBackground = Color(0xFFF1F5F9), surface = Color(0xE6151C2C),
        onSurface = Color(0xFFF1F5F9), surfaceVariant = Color(0xFF1B2436), onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF26324D), error = Color(0xFFFF6B81), scrim = Color(0xB3000000))
)

private val Light by lazy(LazyThreadSafetyMode.NONE) { lightPalette() }
private val Dark by lazy(LazyThreadSafetyMode.NONE) { darkPalette() }
private val palette: NabdPalette get() = if (NabdThemeState.dark) Dark else Light

/** Background of every screen. */
val Canvas: Color get() = palette.canvas
/** Card fill: white at 88% in daylight, #151C2C glass at night. */
val CardFill: Color get() = palette.card
/** Window fill: the slightly stronger glass of dialogs and sheets. */
val WindowFill: Color get() = palette.window
/** Dim behind every window. */
val WindowDim: Color get() = palette.dim
/** Filled fields, chips and rows. */
val Raised: Color get() = palette.raised
val Outline: Color get() = palette.outline
val Hairline: Color get() = palette.hairline
/** Titles. */
val TextMain: Color get() = palette.text
/** Secondary text under every setting and row. */
val Muted: Color get() = palette.muted
val Accent: Color get() = palette.accent
val AccentLight: Color get() = palette.accentLight
/** Deep soft shadow ink: #0F172A at 10% by day, black by night. */
val ShadowInk: Color get() = palette.ink
/** Hairline of a glass surface. */
val GlassEdge: Color get() = palette.edge
/** Light sheen painted over the blur layer. */
val GlassSheen: Color get() = palette.sheen
/** Card fill kept as a separate name so material's Surface composable stays unambiguous. */
val Surface: Color get() = CardFill
val Glass: Color get() = CardFill

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

/** The single content card of the app: 20dp corners on an opaque surface, no glass — the list-row card of the feed, friends, chats and menu. */
@Composable fun NabdCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ContentCard(modifier = modifier, onClick = onClick, radius = 20.dp) { content(this) }
}

/** Applies the current palette: daylight by default, dark glass when the switch is on. */
@Composable fun NabdTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = Type, colorScheme = palette.scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
