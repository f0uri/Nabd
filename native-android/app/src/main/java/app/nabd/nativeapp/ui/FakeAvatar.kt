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
import app.nabd.nativeapp.data.FakePeople

/**
 * A generated avatar: a flat face drawn in-app, unique per generated person.
 * No photo, no network, no real person — and no repeats: the style is a
 * number in a ~6.9M-combination space (16 backgrounds × 10 skins × 12 hair
 * colours × 8 hair styles × 5 glasses × 5 facial hair × 6 accessories ×
 * 3 face shapes), picked by a multiplicative permutation of the person's
 * number inside their list, so two different people in the same list never
 * share a look. The same id always draws the same face, so a person looks
 * the same every time the app opens their list or profile — including
 * people who already existed before this change: their old repeated
 * pictures are replaced by fresh unique ones the moment the app updates,
 * because nothing about the picture is stored, it is computed from the id.
 *
 * East Asian named people keep straight black hair: their hair colour
 * comes from a palette of 12 near-black shades instead of the 12 varied
 * colours, so the combination space stays the same size for everyone and
 * the no-repeat promise holds for them too.
 */
private val AvatarBackgrounds = listOf(Color(0xFFDBEAFE), Color(0xFFFCE7F3), Color(0xFFDCFCE7),
    Color(0xFFFEF3C7), Color(0xFFE0E7FF), Color(0xFFFFEDD5), Color(0xFFCCFBF1), Color(0xFFF3E8FF),
    Color(0xFFFDE68A), Color(0xFFBFDBFE), Color(0xFFFBCFE8), Color(0xFFA7F3D0),
    Color(0xFFDDD6FE), Color(0xFFFFE4E6), Color(0xFFD1FAE5), Color(0xFFE0F2FE))
private val AvatarSkins = listOf(Color(0xFFFFDBAC), Color(0xFFF1C27D), Color(0xFFE0AC69),
    Color(0xFFC68642), Color(0xFF8D5524), Color(0xFFFFE0B2), Color(0xFFD4A373),
    Color(0xFFA06844), Color(0xFF6B4226), Color(0xFFFFC9A3))
private val AvatarHairs = listOf(Color(0xFF1A1A1A), Color(0xFF3B2F2F), Color(0xFF8B5A2B),
    Color(0xFFB5651D), Color(0xFFD2B48C), Color(0xFF6B6B6B), Color(0xFF4A2C2A),
    Color(0xFF7B3F00), Color(0xFFB5522B), Color(0xFFD2691E), Color(0xFFE6C9A8),
    Color(0xFF2F2F4F))
private val AvatarHairsAsian = listOf(Color(0xFF1A1A1A), Color(0xFF2B2118), Color(0xFF1F1B16),
    Color(0xFF241A12), Color(0xFF2E2620), Color(0xFF17130F), Color(0xFF0F0D0B),
    Color(0xFF3A2E25), Color(0xFF2A1F1A), Color(0xFF1C1611), Color(0xFF33261B),
    Color(0xFF201914))

/** 16 × 10 × 12 × 8 × 5 × 5 × 6 × 3 combinations. */
private const val AVATAR_SPACE = 6_912_000L
/** Coprime to AVATAR_SPACE (odd, not divisible by 3 or 5), so multiplying a person's index by it permutes the space: distinct indices never collide. */
private const val AVATAR_MULTIPLIER = 2_654_435_761L

internal data class AvatarStyle(
    val background: Color, val skin: Color, val hairColor: Color, val hairStyle: Int,
    val glasses: Int, val facialHair: Int, val accessory: Int, val faceShape: Int
)

/** The unique drawn-avatar style of a generated person, from their id "fake:<namespace>:<index>". */
internal fun avatarStyleFor(personId: String): AvatarStyle {
    val body = personId.removePrefix("fake:")
    val split = body.lastIndexOf(':')
    val namespace = if (split > 0) body.substring(0, split) else body
    val index = if (split > 0) body.substring(split + 1).toIntOrNull() ?: 0 else 0
    val nsSeed = FakePeople.seedOf("avatar-ns:$namespace")
    var code = Math.floorMod(nsSeed + index.toLong() * AVATAR_MULTIPLIER, AVATAR_SPACE)
    val background = AvatarBackgrounds[(code % 16).toInt()]; code /= 16
    val skin = AvatarSkins[(code % 10).toInt()]; code /= 10
    val hairIndex = (code % 12).toInt(); code /= 12
    val hairStyle = (code % 8).toInt(); code /= 8
    val glasses = (code % 5).toInt(); code /= 5
    val facialHair = (code % 5).toInt(); code /= 5
    val accessory = (code % 6).toInt(); code /= 6
    val faceShape = (code % 3).toInt()
    val hairColor = if (FakePeople.isAsianId(personId)) AvatarHairsAsian[hairIndex] else AvatarHairs[hairIndex]
    return AvatarStyle(background, skin, hairColor, hairStyle, glasses, facialHair, accessory, faceShape)
}

@Composable internal fun FakeAvatar(personId: String, size: Int = 40) {
    val style = remember(personId) { avatarStyleFor(personId) }
    Canvas(Modifier.size(size.dp).clip(CircleShape)) {
        val w = drawContext.size.width
        val h = drawContext.size.height
        drawRect(style.background)
        // Long hair falls behind the face.
        if (style.hairStyle == 2) {
            drawRoundRect(style.hairColor, topLeft = Offset(w * .16f, h * .30f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
            drawRoundRect(style.hairColor, topLeft = Offset(w * .72f, h * .30f), size = androidx.compose.ui.geometry.Size(w * .12f, h * .55f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .06f, w * .06f))
        }
        // Face shape: 0 round, 1 oval, 2 rounded square.
        val faceCenter = Offset(w * .5f, h * .56f)
        val faceRadius = w * .30f
        when (style.faceShape) {
            1 -> drawOval(style.skin, topLeft = Offset(w * .22f, h * .24f), size = androidx.compose.ui.geometry.Size(w * .56f, h * .66f))
            2 -> drawRoundRect(style.skin, topLeft = Offset(w * .21f, h * .27f), size = androidx.compose.ui.geometry.Size(w * .58f, h * .58f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .12f, w * .12f))
            else -> drawCircle(style.skin, radius = faceRadius, center = faceCenter)
        }
        // Hair: 0 cap, 1 side sweep, 2 long (panels drawn above), 3 curls, 4 bob, 5 bangs, 6 curly puffs, 7 buzz cut.
        when (style.hairStyle) {
            0 -> drawPath(Path().apply {
                moveTo(w * .20f, h * .52f)
                quadraticTo(w * .22f, h * .14f, w * .50f, h * .16f)
                quadraticTo(w * .78f, h * .14f, w * .80f, h * .52f)
                quadraticTo(w * .72f, h * .30f, w * .50f, h * .30f)
                quadraticTo(w * .28f, h * .30f, w * .20f, h * .52f)
                close()
            }, style.hairColor)
            1 -> drawPath(Path().apply {
                moveTo(w * .20f, h * .55f)
                quadraticTo(w * .18f, h * .12f, w * .52f, h * .14f)
                quadraticTo(w * .80f, h * .14f, w * .82f, h * .42f)
                quadraticTo(w * .70f, h * .26f, w * .52f, h * .28f)
                quadraticTo(w * .30f, h * .28f, w * .20f, h * .55f)
                close()
            }, style.hairColor)
            3 -> {
                listOf(.28f to .30f, .40f to .20f, .52f to .18f, .64f to .20f, .74f to .30f).forEach { (x, y) ->
                    drawCircle(style.hairColor, radius = w * .085f, center = Offset(w * x, h * y))
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
                }, style.hairColor)
                drawRoundRect(style.hairColor, topLeft = Offset(w * .18f, h * .30f),
                    size = androidx.compose.ui.geometry.Size(w * .11f, h * .48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f, w * .05f))
                drawRoundRect(style.hairColor, topLeft = Offset(w * .71f, h * .30f),
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
                }, style.hairColor)
                drawRoundRect(style.hairColor, topLeft = Offset(w * .19f, h * .34f),
                    size = androidx.compose.ui.geometry.Size(w * .07f, h * .40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .035f, w * .035f))
                drawRoundRect(style.hairColor, topLeft = Offset(w * .74f, h * .34f),
                    size = androidx.compose.ui.geometry.Size(w * .07f, h * .40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .035f, w * .035f))
            }
            6 -> {
                // Curly puffs across the top and at the sides.
                listOf(.30f to .32f, .42f to .22f, .54f to .20f, .66f to .24f, .74f to .34f).forEach { (x, y) ->
                    drawCircle(style.hairColor, radius = w * .09f, center = Offset(w * x, h * y))
                }
                drawCircle(style.hairColor, radius = w * .07f, center = Offset(w * .18f, h * .45f))
                drawCircle(style.hairColor, radius = w * .07f, center = Offset(w * .82f, h * .45f))
            }
            7 -> {
                // Buzz cut: a thin cap hugging the head.
                drawPath(Path().apply {
                    moveTo(w * .22f, h * .50f)
                    quadraticTo(w * .24f, h * .26f, w * .50f, h * .26f)
                    quadraticTo(w * .76f, h * .26f, w * .78f, h * .50f)
                    quadraticTo(w * .70f, h * .36f, w * .50f, h * .36f)
                    quadraticTo(w * .30f, h * .36f, w * .22f, h * .50f)
                    close()
                }, style.hairColor)
            }
            else -> drawPath(Path().apply {
                moveTo(w * .20f, h * .50f)
                quadraticTo(w * .22f, h * .16f, w * .50f, h * .16f)
                quadraticTo(w * .78f, h * .16f, w * .80f, h * .50f)
                quadraticTo(w * .72f, h * .30f, w * .50f, h * .30f)
                quadraticTo(w * .28f, h * .30f, w * .20f, h * .50f)
                close()
            }, style.hairColor)
        }
        // Facial hair, drawn under the eyes and mouth.
        when (style.facialHair) {
            1 -> drawRoundRect(style.hairColor, topLeft = Offset(w * .44f, h * .63f),
                size = androidx.compose.ui.geometry.Size(w * .12f, h * .035f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .017f, w * .017f))
            2 -> drawPath(Path().apply {
                moveTo(w * .22f, h * .58f)
                quadraticTo(w * .50f, h * .95f, w * .78f, h * .58f)
                lineTo(w * .72f, h * .52f)
                quadraticTo(w * .50f, h * .78f, w * .28f, h * .52f)
                close()
            }, style.hairColor)
            3 -> {
                drawRoundRect(style.hairColor, topLeft = Offset(w * .44f, h * .63f),
                    size = androidx.compose.ui.geometry.Size(w * .12f, h * .035f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .017f, w * .017f))
                drawCircle(style.hairColor, radius = w * .05f, center = Offset(w * .50f, h * .73f))
            }
            4 -> drawRoundRect(style.hairColor, topLeft = Offset(w * .485f, h * .70f),
                size = androidx.compose.ui.geometry.Size(w * .03f, h * .04f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .015f, w * .015f))
        }
        val eyeColor = Color(0xFF262626)
        drawCircle(eyeColor, radius = w * .028f, center = Offset(w * .40f, h * .56f))
        drawCircle(eyeColor, radius = w * .028f, center = Offset(w * .60f, h * .56f))
        drawPath(Path().apply {
            moveTo(w * .43f, h * .66f)
            quadraticTo(w * .50f, h * .72f, w * .57f, h * .66f)
        }, eyeColor, style = Stroke(width = w * .022f, cap = StrokeCap.Round))
        // Glasses: 0 none, 1 round, 2 square, 3 sunglasses, 4 oval.
        val frame = Color(0xFF262626)
        when (style.glasses) {
            1 -> {
                drawCircle(frame, radius = w * .075f, center = Offset(w * .40f, h * .56f), style = Stroke(width = w * .02f))
                drawCircle(frame, radius = w * .075f, center = Offset(w * .60f, h * .56f), style = Stroke(width = w * .02f))
                drawLine(frame, Offset(w * .475f, h * .56f), Offset(w * .525f, h * .56f), strokeWidth = w * .02f)
            }
            2 -> {
                drawRoundRect(frame, topLeft = Offset(w * .325f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .15f, h * .14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .02f, w * .02f), style = Stroke(width = w * .02f))
                drawRoundRect(frame, topLeft = Offset(w * .525f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .15f, h * .14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .02f, w * .02f), style = Stroke(width = w * .02f))
                drawLine(frame, Offset(w * .475f, h * .56f), Offset(w * .525f, h * .56f), strokeWidth = w * .02f)
            }
            3 -> {
                drawRoundRect(Color(0xFF1F2937), topLeft = Offset(w * .325f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .15f, h * .14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .02f, w * .02f))
                drawRoundRect(Color(0xFF1F2937), topLeft = Offset(w * .525f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .15f, h * .14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .02f, w * .02f))
                drawLine(Color(0xFF1F2937), Offset(w * .475f, h * .56f), Offset(w * .525f, h * .56f), strokeWidth = w * .02f)
            }
            4 -> {
                drawOval(frame, topLeft = Offset(w * .325f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .15f, h * .14f), style = Stroke(width = w * .02f))
                drawOval(frame, topLeft = Offset(w * .525f, h * .49f), size = androidx.compose.ui.geometry.Size(w * .15f, h * .14f), style = Stroke(width = w * .02f))
                drawLine(frame, Offset(w * .475f, h * .56f), Offset(w * .525f, h * .56f), strokeWidth = w * .02f)
            }
        }
        // Accessories: 0 none, 1 earrings, 2 cap, 3 headband, 4 hairclip, 5 flower pin.
        when (style.accessory) {
            1 -> {
                drawCircle(Color(0xFFD4AF37), radius = w * .02f, center = Offset(w * .20f, h * .58f))
                drawCircle(Color(0xFFD4AF37), radius = w * .02f, center = Offset(w * .80f, h * .58f))
            }
            2 -> drawRoundRect(Color(0xFF1E3A8A), topLeft = Offset(w * .22f, h * .10f),
                size = androidx.compose.ui.geometry.Size(w * .56f, h * .20f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .08f, w * .08f))
            3 -> drawRoundRect(Color(0xFFD4AF37), topLeft = Offset(w * .20f, h * .30f),
                size = androidx.compose.ui.geometry.Size(w * .60f, h * .06f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .03f, w * .03f))
            4 -> drawRoundRect(Color(0xFFEC4899), topLeft = Offset(w * .68f, h * .28f),
                size = androidx.compose.ui.geometry.Size(w * .10f, h * .05f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .025f, w * .025f))
            5 -> {
                listOf(0f to -.055f, .052f to -.017f, .032f to .045f, -.032f to .045f, -.052f to -.017f).forEach { (dx, dy) ->
                    drawCircle(Color(0xFFF472B6), radius = w * .025f, center = Offset(w * (.70f + dx), h * (.30f + dy)))
                }
                drawCircle(Color(0xFFFDE047), radius = w * .03f, center = Offset(w * .70f, h * .30f))
            }
        }
    }
}
