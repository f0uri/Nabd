package app.nabd.nativeapp.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The single blue gradient of the app: #0866FF → #00A3FF. */
val NabdBlue: Brush get() = Brush.horizontalGradient(listOf(Color(0xFF0866FF), Color(0xFF00A3FF)))
/** The red gradient of an active نبضة. */
val NabdRed: Brush get() = Brush.horizontalGradient(listOf(Color(0xFFFF5A5F), Color(0xFFE11D48)))
/** Soft blue halo under primary buttons, taken from the live accent of the theme. */
val BlueShadow: Color get() = Accent.copy(alpha = .35f)

/** Real blur only where the platform supports it; older devices keep the translucent fill. */
fun Modifier.glassBlur(radius: Dp): Modifier = if (Build.VERSION.SDK_INT >= 31) this.blur(radius) else this

/** Deep soft shadow: offset Y ≈ 10, blur ≈ 30, ink at 10%. */
fun Modifier.nabdShadow(shape: Shape, elevation: Dp = 20.dp): Modifier =
    this.shadow(elevation, shape, spotColor = ShadowInk, ambientColor = ShadowInk)

/** Shared press state so the scale animation and the clickable agree on one source. */
class PressState internal constructor(val source: MutableInteractionSource)
@Composable fun rememberPress(): PressState = remember { PressState(MutableInteractionSource()) }

/** Every pressable element shrinks to 0.96 with a spring. */
@Composable fun Modifier.pressScale(press: PressState, scale: Float = 0.96f): Modifier {
    val pressed by press.source.collectIsPressedAsState()
    val value by animateFloatAsState(targetValue = if (pressed) scale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press-scale")
    return this.graphicsLayer { scaleX = value; scaleY = value }
}

/** Light haptic tick plus the spring squeeze on every real interaction. */
@Composable fun Modifier.pressClick(press: PressState = rememberPress(), enabled: Boolean = true, haptic: Boolean = true, onClick: () -> Unit): Modifier {
    val view = LocalView.current
    return this.pressScale(press).clickable(interactionSource = press.source, indication = null, enabled = enabled) {
        if (haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        onClick()
    }
}

/**
 * The one glass surface of the app: white at 88%, real blur behind it, 24dp
 * corners, the deep soft shadow of the design system and a spring press.
 */
@Composable fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, radius: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    val press = rememberPress()
    Box(modifier.fillMaxWidth().nabdShadow(shape).clip(shape).background(CardFill).border(1.dp, GlassEdge, shape)
        .then(if (onClick != null) Modifier.pressClick(press = press, onClick = onClick) else Modifier)) {
        Box(Modifier.matchParentSize().glassBlur(22.dp).background(GlassSheen))
        Column(Modifier.fillMaxWidth()) { content(this) }
    }
}

/** Pill filled with the blue gradient, a soft blue halo and a spring press. */
@Composable fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    brush: Brush? = NabdBlue, height: Dp = 50.dp, fontSize: Int = 15, icon: ImageVector? = null, testTag: String? = null) {
    val press = rememberPress()
    val fill = brush
    val painted = enabled && fill != null
    Box(modifier.then(Modifier.height(height))
        .then(if (painted) Modifier.shadow(10.dp, CircleShape, spotColor = BlueShadow, ambientColor = BlueShadow) else Modifier)
        .clip(CircleShape)
        .then(if (painted) Modifier.background(fill!!) else Modifier.background(if (enabled) Raised else Raised.copy(alpha = .7f)))
        .pressClick(press = press, enabled = enabled, onClick = onClick)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 18.dp)) {
            if (icon != null) Icon(icon, null, tint = if (enabled && brush != null) Color.White else Muted, modifier = Modifier.size(18.dp))
            Text(text, color = if (enabled && brush != null) Color.White else Muted, fontSize = fontSize.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/** Circular glass button used by headers, chats and lists. */
@Composable fun GlassCircle(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    filled: Boolean = false, size: Dp = 38.dp, tint: Color = TextMain) {
    val press = rememberPress()
    Box(modifier.size(size).clip(CircleShape)
        .then(if (filled) Modifier.background(NabdBlue) else Modifier.background(Raised))
        .pressClick(press = press, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (filled) Color.White else tint, modifier = Modifier.size((size.value * .5f).dp))
    }
}

/** Filled field: #F1F5F9, 16dp corners, no hairline border. */
@Composable fun NabdField(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    singleLine: Boolean = true, maxLines: Int = 1, tag: String? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null) {
    androidx.compose.material3.OutlinedTextField(value = value, onValueChange = change, enabled = enabled, singleLine = singleLine, maxLines = if (singleLine) 1 else maxLines,
        placeholder = { Text(label, color = Muted, fontSize = 14.sp) }, textStyle = TextStyle(color = TextMain, fontSize = 15.sp),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Raised, unfocusedContainerColor = Raised, disabledContainerColor = Raised,
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
            focusedTextColor = TextMain, unfocusedTextColor = TextMain,
            cursorColor = Accent, focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted),
        trailingIcon = trailingIcon, keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
        modifier = modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
}

/** Real glass window: white 92%, blur, 28dp corners, 16dp shadow over a 40% dim. */
@Composable fun GlassWindow(close: () -> Unit, dismissible: Boolean = true, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (dismissible) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(WindowDim), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(28.dp)
            Box(Modifier.padding(horizontal = 22.dp).fillMaxWidth().widthIn(max = 460.dp)
                .nabdShadow(shape, elevation = 24.dp).clip(shape).background(WindowFill).border(1.dp, GlassEdge, shape)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
                Box(Modifier.matchParentSize().glassBlur(30.dp).background(GlassSheen))
                Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { content(this) }
            }
        }
    }
}

/** Confirmation windows use the same glass as every other window, never a plain alert. */
@Composable fun GlassConfirm(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, close: () -> Unit,
    busy: Boolean = false, danger: Boolean = false, confirmTag: String? = null, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    GlassWindow(close = { if (!busy) close() }) {
        Text(title, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        if (extra != null) extra()
        PillButton(confirmLabel, { onConfirm() }, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = if (danger) NabdRed else NabdBlue, testTag = confirmTag)
        PillButton("إلغاء", close, modifier = Modifier.fillMaxWidth(), enabled = !busy, brush = null, height = 46.dp, fontSize = 14)
    }
}

/** The wordmark: the title drawn with the blue gradient instead of a flat colour. */
@Composable fun GradientText(text: String, brush: Brush = NabdBlue, size: TextUnit = 22.sp, weight: FontWeight = FontWeight.Black, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = TextStyle(brush = brush, fontFamily = ArabicFont, fontSize = size, fontWeight = weight, lineHeight = (size.value + 4).sp))
}

/** Friendly empty state: a large custom vector, never a bare line of text. */
@Composable fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    GlassCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(Accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(36.dp))
            }
            Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(text, color = Muted, fontSize = 13.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            if (action != null) Box(Modifier.padding(top = 4.dp)) { action() }
        }
    }
}

/** Small pill used by filters and chips. */
@Composable fun FilterPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    Box(modifier.clip(CircleShape).background(if (selected) Accent else CardFill).border(1.dp, if (selected) Color.Transparent else GlassEdge, CircleShape)
        .pressClick(press = press, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** 16dp rounded media frame used by posts, chats and comments. */
val MediaShape: Shape get() = RoundedCornerShape(18.dp)
/** Vertical hairline used between the statistics of one glass bar. */
@Composable fun VerticalDivider(modifier: Modifier = Modifier) = Box(modifier.width(1.dp).height(30.dp).background(Outline.copy(alpha = .5f)))

/** Soft blue gradient strip that marks a card as special (points, invitations). */
val AccentStrip: Brush get() = Brush.verticalGradient(listOf(Color(0xFF0866FF), Color(0xFF00A3FF)))

/** Small blue-tinted icon plate used by rows and notifications. */
@Composable fun IconPlate(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Accent, size: Dp = 42.dp) {
    Box(modifier.size(size).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size.value * .5f).dp))
    }
}

/** Follow pill with the custom person-plus vector. */
@Composable fun FollowPill(following: Boolean, busy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PillButton(if (following) "إلغاء المتابعة" else "متابعة", onClick, modifier = modifier.widthIn(min = 116.dp), enabled = !busy,
        brush = if (following) null else NabdBlue, height = 42.dp, fontSize = 13, icon = if (following) null else Icons.Outlined.Add)
}
