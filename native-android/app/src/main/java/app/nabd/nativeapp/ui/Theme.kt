package app.nabd.nativeapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Canvas = Color(0xFF0B0B0F)
val Surface = Color(0xFF16161C)
val Raised = Color(0xFF1F1F27)
val Outline = Color(0xFF383843)
val TextMain = Color(0xFFF4F4F6)
val Muted = Color(0xFFAEAEBD)
val Accent = Color(0xFFD7113D)
val AccentLight = Color(0xFFFF92AA)

@Composable fun NabdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = AccentLight, onPrimary = Canvas, primaryContainer = Accent, onPrimaryContainer = Color.White,
        background = Canvas, onBackground = TextMain, surface = Surface, onSurface = TextMain, surfaceVariant = Raised, onSurfaceVariant = Muted, outline = Outline, error = Color(0xFFFF879B))) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
