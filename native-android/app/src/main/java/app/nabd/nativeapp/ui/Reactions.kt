package app.nabd.nativeapp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The six Nabd reactions, drawn as custom vectors: a red heart crossed by an ECG
 * line for نبضة and five gradient faces with real eyes, eyebrows and mouths.
 * No emoji glyph, no system icon, no thumbs-up anywhere in the application.
 */
internal val reactionOrder = listOf("like", "love", "haha", "wow", "sad", "angry")

/** Every label is user-facing Arabic only; no technical wording is shown. */
internal val reactionLabels = linkedMapOf("like" to "نبضة", "love" to "دعم", "haha" to "هاها", "wow" to "واو", "sad" to "أحزنني", "angry" to "أغضبني")

/** Each reaction owns one colour, used for the button label and the hover bubble. */
internal val reactionColors = mapOf(
    "like" to Color(0xFFE11D48), "love" to Color(0xFFEC4899), "haha" to Color(0xFFF59E0B),
    "wow" to Color(0xFF0EA5E9), "sad" to Color(0xFF6366F1), "angry" to Color(0xFFF97316))

internal fun reactionLabel(kind: String?): String = reactionLabels[kind] ?: "نبضة"
internal fun reactionColor(kind: String?): Color = reactionColors[kind] ?: Accent

private val FaceTop = Color(0xFFFFE066)
private val FaceBottom = Color(0xFFFFB020)
private val FaceEdge = Color(0x1F000000)
private val Ink = Color(0xFF4A3200)
private val Mouth = Color(0xFF5A3A00)
private val Teeth = Color(0xFFFFF3D6)
private val Tongue = Color(0xFFE0556B)
private val Tear = Color(0xFF57A9FF)
private val HeartTop = Color(0xFFFF6A8A)
private val HeartBottom = Color(0xFFE11D48)
private val AngryTop = Color(0xFFFF9A4D)
private val AngryBottom = Color(0xFFE0245E)

/** Heart outline in 0..1 space, scaled and shifted into place. */
private fun heartPath(scale: Float, dx: Float, dy: Float): Path = Path().apply {
    val x = { v: Float -> dx + v * scale }
    val y = { v: Float -> dy + v * scale }
    moveTo(x(.5f), y(.92f))
    cubicTo(x(.16f), y(.66f), x(.04f), y(.48f), x(.04f), y(.34f))
    cubicTo(x(.04f), y(.16f), x(.18f), y(.05f), x(.33f), y(.05f))
    cubicTo(x(.44f), y(.05f), x(.49f), y(.12f), x(.5f), y(.19f))
    cubicTo(x(.51f), y(.12f), x(.56f), y(.05f), x(.67f), y(.05f))
    cubicTo(x(.82f), y(.05f), x(.96f), y(.16f), x(.96f), y(.34f))
    cubicTo(x(.96f), y(.48f), x(.84f), y(.66f), x(.5f), y(.92f))
    close()
}

private fun DrawScope.face(top: Color, bottom: Color) {
    val s = size.minDimension
    drawCircle(brush = Brush.linearGradient(listOf(top, bottom), start = Offset(0f, 0f), end = Offset(0f, s)), radius = s * .5f, center = center)
    drawCircle(color = FaceEdge, radius = s * .49f, center = center, style = Stroke(width = s * .022f))
}

private fun DrawScope.eyes(y: Float, radius: Float, ink: Color = Ink) {
    val s = size.minDimension
    drawCircle(ink, s * radius, Offset(s * .36f, s * y))
    drawCircle(ink, s * radius, Offset(s * .64f, s * y))
    drawCircle(Color.White.copy(alpha = .85f), s * radius * .34f, Offset(s * .335f, s * (y - radius * .38f)))
    drawCircle(Color.White.copy(alpha = .85f), s * radius * .34f, Offset(s * .615f, s * (y - radius * .38f)))
}

/** Closed, laughing eyes drawn as two upward arcs. */
private fun DrawScope.laughEyes() {
    val s = size.minDimension
    val stroke = Stroke(width = s * .055f, cap = StrokeCap.Round)
    drawArc(color = Ink, startAngle = 195f, sweepAngle = 150f, useCenter = false, topLeft = Offset(s * .25f, s * .26f), size = Size(s * .22f, s * .18f), style = stroke)
    drawArc(color = Ink, startAngle = 195f, sweepAngle = 150f, useCenter = false, topLeft = Offset(s * .53f, s * .26f), size = Size(s * .22f, s * .18f), style = stroke)
}

private fun DrawScope.brow(fromX: Float, fromY: Float, toX: Float, toY: Float, ink: Color = Ink) {
    val s = size.minDimension
    drawLine(ink, Offset(s * fromX, s * fromY), Offset(s * toX, s * toY), strokeWidth = s * .05f, cap = StrokeCap.Round)
}

private fun DrawScope.laughMouth() {
    val s = size.minDimension
    drawOval(Mouth, Offset(s * .29f, s * .47f), Size(s * .42f, s * .27f))
    drawRoundRect(Teeth, Offset(s * .295f, s * .475f), Size(s * .41f, s * .075f), CornerRadius(s * .035f, s * .035f))
    drawOval(Tongue, Offset(s * .40f, s * .645f), Size(s * .20f, s * .10f))
}

private fun DrawScope.smallMouth() {
    val s = size.minDimension
    drawCircle(Mouth, s * .085f, Offset(s * .5f, s * .60f))
}

private fun DrawScope.frown() {
    val s = size.minDimension
    drawArc(color = Mouth, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * .30f, s * .52f), size = Size(s * .40f, s * .24f), style = Stroke(s * .055f, cap = StrokeCap.Round))
}

private fun DrawScope.smile() {
    val s = size.minDimension
    drawArc(color = Mouth, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * .30f, s * .48f), size = Size(s * .40f, s * .24f), style = Stroke(s * .055f, cap = StrokeCap.Round))
}

private fun DrawScope.angryMouth() {
    val s = size.minDimension
    drawOval(Mouth, Offset(s * .29f, s * .52f), Size(s * .42f, s * .22f))
    drawRoundRect(Teeth, Offset(s * .295f, s * .525f), Size(s * .41f, s * .06f), CornerRadius(s * .03f, s * .03f))
    drawRoundRect(Teeth, Offset(s * .295f, s * .655f), Size(s * .41f, s * .06f), CornerRadius(s * .03f, s * .03f))
}

private fun DrawScope.tear() {
    val s = size.minDimension
    drawOval(Tear, Offset(s * .315f, s * .46f), Size(s * .065f, s * .13f))
}

private fun DrawScope.heart(scale: Float, dx: Float, dy: Float, top: Color, bottom: Color, edge: Color) {
    val path = heartPath(scale, dx, dy)
    drawPath(path, Brush.linearGradient(listOf(top, bottom), start = Offset(dx, dy), end = Offset(dx + scale, dy + scale)))
    drawPath(path, edge, style = Stroke(width = scale * .02f))
}

private fun DrawScope.pulseHeart() {
    val s = size.minDimension
    heart(s * .96f, s * .02f, s * .01f, HeartTop, HeartBottom, Color(0x33FFFFFF))
    val path = Path().apply {
        val pts = listOf(.20f to .50f, .32f to .50f, .38f to .34f, .44f to .66f, .50f to .46f, .57f to .50f, .63f to .38f, .69f to .50f, .80f to .50f)
        pts.forEachIndexed { i, (x, y) -> val px = s * x; val py = s * y; if (i == 0) moveTo(px, py) else lineTo(px, py) }
    }
    drawPath(path, Color.White, style = Stroke(width = s * .055f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * One reaction, drawn from scratch at any size: the نبضة heart carries the ECG
 * line, every other kind is a gradient face with eyes, brows and a mouth.
 */
@Composable internal fun ReactionGlyph(kind: String, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        when (kind) {
            "like" -> pulseHeart()
            "love" -> {
                face(FaceTop, FaceBottom)
                eyes(.40f, .072f)
                smile()
                val s = this.size.minDimension
                heart(s * .52f, s * .5f, s * .5f, HeartTop, HeartBottom, Color(0x22FFFFFF))
            }
            "haha" -> {
                face(FaceTop, FaceBottom)
                laughEyes()
                laughMouth()
                brow(.24f, .20f, .40f, .16f)
                brow(.76f, .20f, .60f, .16f)
            }
            "wow" -> {
                face(FaceTop, FaceBottom)
                eyes(.38f, .085f)
                smallMouth()
                brow(.26f, .18f, .42f, .14f)
                brow(.74f, .18f, .58f, .14f)
            }
            "sad" -> {
                face(FaceTop, FaceBottom)
                eyes(.42f, .075f)
                frown()
                brow(.26f, .30f, .44f, .24f)
                brow(.74f, .30f, .56f, .24f)
                tear()
            }
            "angry" -> {
                face(AngryTop, AngryBottom)
                eyes(.40f, .075f)
                angryMouth()
                brow(.26f, .22f, .46f, .33f)
                brow(.74f, .22f, .54f, .33f)
            }
            else -> pulseHeart()
        }
    }
}

/** Top three reactions of a real post, drawn as overlapping glyphs next to the real count. */
@Composable internal fun ReactionSummary(breakdown: Map<String, Int>, total: Int, modifier: Modifier = Modifier) {
    if (total <= 0) return
    val top = remember(breakdown) { breakdown.filter { it.value > 0 }.toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { reactionOrder.indexOf(it.first) }).take(3) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (top.isNotEmpty()) Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
            top.forEachIndexed { index, (kind, _) ->
                Box(Modifier.offset(x = (index * 13).dp).size(20.dp).clip(CircleShape)
                    .background(Color.White).border(1.2.dp, Color.White, CircleShape)
                    .padding(1.5.dp), contentAlignment = Alignment.Center) {
                    ReactionGlyph(kind, size = 16.dp)
                }
            }
            Spacer(Modifier.width(((top.size - 1) * 13 + 20).dp))
        }
        Text("$total", color = Muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("reaction-total"))
    }
}

/**
 * Floating glass reaction bar. Long press on the نبضة button opens it: the six
 * reactions fly in with a staggered spring, the finger can drag across them to
 * enlarge one with its name bubble, and releasing it flies the chosen reaction
 * back into the button before the real server write happens.
 */
@Composable internal fun ReactionDock(expanded: Boolean, modifier: Modifier = Modifier, pick: (String) -> Unit) {
    if (!expanded) return
    var hovered by remember { mutableStateOf<String?>(null) }
    var flying by remember { mutableStateOf<String?>(null) }
    val centers = remember { mutableStateMapOf<String, Float>() }
    fun nearest(x: Float): String? = centers.minByOrNull { (_, cx) -> abs(cx - x) }?.key
    LaunchedEffect(flying) {
        val chosen = flying ?: return@LaunchedEffect
        delay(340)
        pick(chosen)
    }
    val flight by animateFloatAsState(targetValue = if (flying != null) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "reaction-flight")
    val shape = RoundedCornerShape(22.dp)
    Box(modifier) {
        Box(Modifier.shadow(10.dp, shape).clip(shape).background(Color(0xEFFFFFFF)).border(1.dp, GlassEdge, shape)) {
            Box(Modifier.matchParentSize().blur(26.dp).background(Color(0x33FFFFFF)))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hovered = nearest(down.position.x)
                        var last = down
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change
                            hovered = nearest(change.position.x)
                            if (!change.pressed) break
                        }
                        val chosen = hovered
                        hovered = null
                        if (chosen != null) flying = chosen
                    }
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                reactionOrder.forEachIndexed { index, kind ->
                    var itemShown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(index * 32L); itemShown = true }
                    val enter by animateFloatAsState(targetValue = if (itemShown) 1f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "dock-enter-$kind")
                    val lift by animateFloatAsState(targetValue = if (hovered == kind) 1f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "dock-lift-$kind")
                    val scale = enter * (1f + .55f * lift)
                    val rise = 26.dp * lift + 10.dp * (1f - enter)
                    Column(Modifier.onGloballyPositioned { centers[kind] = it.positionInParent().x + it.size.width / 2f }
                        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = enter.coerceIn(0f, 1f); translationY = -rise.toPx() }
                        .testTag("dock-$kind"), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (lift > .05f) Surface(color = reactionColor(kind), shape = RoundedCornerShape(999.dp), shadowElevation = 4.dp,
                            modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(reactionLabel(kind), color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                        Box(Modifier.size(38.dp).padding(3.dp), contentAlignment = Alignment.Center) {
                            ReactionGlyph(kind, size = 32.dp)
                        }
                    }
                }
            }
        }
        if (flying != null) ReactionGlyph(flying!!, size = (34 - 18 * flight).dp,
            modifier = Modifier.align(Alignment.TopStart)
                .offset { IntOffset(((centers[flying] ?: 0f) - 17.dp.toPx()).roundToInt(), (-8 + flight * 52).dp.roundToPx()) }
                .graphicsLayer { alpha = 1f - flight })
    }
}
