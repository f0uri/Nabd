package app.nabd.nativeapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val FakeBackgrounds = listOf(Color(0xFFDBEAFE), Color(0xFFFCE7F3), Color(0xFFDCFCE7),
    Color(0xFFFEF3C7), Color(0xFFE0E7FF), Color(0xFFFFEDD5), Color(0xFFCCFBF1), Color(0xFFF3E8FF))
private val FakeSkins = listOf(Color(0xFFFFDBAC), Color(0xFFF1C27D), Color(0xFFE0AC69),
    Color(0xFFC68642), Color(0xFF8D5524))
private val FakeHairs = listOf(Color(0xFF1A1A1A), Color(0xFF3B2F2F), Color(0xFF8B5A2B),
    Color(0xFFB5651D), Color(0xFFD2B48C), Color(0xFF6B6B6B))

/**
 * A generated avatar: a flat face drawn in-app, deterministic per seed.
 * No photo, no network, no real person — the same seed always draws the
 * same background, skin, hair and face. When [asian] is true the hair is
 * straight black and the styles are East Asian ones (straight bob,
 * straight bangs, long straight hair).
 */
@Composable internal fun FakeAvatar(seed: Long, size: Int = 40, asian: Boolean = false) {
    val style = remember(seed, asian) {
        val random = Random(seed)
        val hair = if (asian) {
            val black = listOf(Color(0xFF1A1A1A), Color(0xFF2B2118))
            listOf(0, 2, 4, 5)[random.nextInt(4)] to black[random.nextInt(black.size)]
        } else {
            random.nextInt(4) to FakeHairs[random.nextInt(FakeHairs.size)]
        }
        Triple(FakeBackgrounds[random.nextInt(FakeBackgrounds.size)],
            FakeSkins[random.nextInt(FakeSkins.size)], hair)
    }
    val (bg, skin, hair) = style
    val (hairStyle, hairColor) = hair
    Canvas(Modifier.size(size.dp).clip(CircleShape)) {
        val w = size.width
        val h = size.height
        drawRect(bg)
        val faceCenter = Offset(w * .5f, h * .56f)
        val faceRadius = w * .30f
        // Long hair falls behind the face.
        if (hairStyle == 2) {
            drawRoundRect(hairColor, topLeft = Offset(w * .16f, h * .30f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
            drawRoundRect(hairColor, topLeft = Offset(w * .72f, h * .30f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
        }
        drawCircle(skin, radius = faceRadius, center = faceCenter)
        when (hairStyle) {
            0 -> drawPath(Path().apply {
                moveTo(w * .20f, h * .52f)
                quadraticTo(w * .22f, h * .14f, w * .50f, h * .16f)
                quadraticTo(w * .78f, h * .14f, w * .80f, h * .52f)
                quadraticTo(w * .72f, h * .30f, w * .50f, h * .30f)
                quadraticTo(w * .28f, h * .30f, w * .20f, h * .52f)
                close()
            }, hairColor)
            1 -> drawPath(Path().apply {
                moveTo(w * .20f, h * .55f)
                quadraticTo(w * .18f, h * .12f, w * .52f, h * .14f)
                quadraticTo(w * .80f, h * .14f, w * .82f, h * .42f)
                quadraticTo(w * .70f, h * .26f, w * .52f, h * .28f)
                quadraticTo(w * .30f, h * .28f, w * .20f, h * .55f)
                close()
            }, hairColor)
            3 -> {
                listOf(.28f to .30f, .40f to .20f, .52f to .18f, .64f to .20f, .74f to .30f).forEach { (x, y) ->
                    drawCircle(hairColor, radius = w * .085f, center = Offset(w * x, h * y))
                }
            }
            4 -> {
                // Straight bob: a cap plus straight side panels to chin length.
                drawPath(Path().apply {
                    moveTo(w * .20f, h * .48f)
                    quadraticTo(w * .22f, h * .15f, w * .50f, h * .15f)
                    quadraticTo(w * .78f, h * .15f, w * .80f, h * .48f)
                    quadraticTo(w * .72f, h * .30f, w * .50f, h * .30f)
                    quadraticTo(w * .28f, h * .30f, w * .20f, h * .48f)
                    close()
                }, hairColor)
                drawRoundRect(hairColor, topLeft = Offset(w * .18f, h * .30f),
                    size = androidx.compose.ui.geometry.Size(w * .11f, h * .48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f, w * .05f))
                drawRoundRect(hairColor, topLeft = Offset(w * .71f, h * .30f),
                    size = androidx.compose.ui.geometry.Size(w * .11f, h * .48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f, w * .05f))
            }
            5 -> {
                // Straight bangs: a straight fringe across the forehead, plus two thin side strands.
                drawPath(Path().apply {
                    moveTo(w * .20f, h * .36f)
                    quadraticTo(w * .20f, h * .13f, w * .50f, h * .13f)
                    quadraticTo(w * .80f, h * .13f, w * .80f, h * .36f)
                    lineTo(w * .80f, h * .40f)
                    lineTo(w * .20f, h * .40f)
                    close()
                }, hairColor)
                drawRoundRect(hairColor, topLeft = Offset(w * .19f, h * .34f),
                    size = androidx.compose.ui.geometry.Size(w * .07f, h * .40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .035f, w * .035f))
                drawRoundRect(hairColor, topLeft = Offset(w * .74f, h * .34f),
                    size = androidx.compose.ui.geometry.Size(w * .07f, h * .40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .035f, w * .035f))
            }
            else -> drawPath(Path().apply {
                moveTo(w * .20f, h * .50f)
                quadraticTo(w * .22f, h * .16f, w * .50f, h * .16f)
                quadraticTo(w * .78f, h * .16f, w * .80f, h * .50f)
                quadraticTo(w * .72f, h * .30f, w * .50f, h * .30f)
                quadraticTo(w * .28f, h * .30f, w * .20f, h * .50f)
                close()
            }, hairColor)
        }
        val eyeColor = Color(0xFF262626)
        drawCircle(eyeColor, radius = w * .028f, center = Offset(w * .40f, h * .56f))
        drawCircle(eyeColor, radius = w * .028f, center = Offset(w * .60f, h * .56f))
        drawPath(Path().apply {
            moveTo(w * .43f, h * .66f)
            quadraticTo(w * .50f, h * .72f, w * .57f, h * .66f)
        }, eyeColor, style = Stroke(width = w * .022f, cap = StrokeCap.Round))
    }
}
