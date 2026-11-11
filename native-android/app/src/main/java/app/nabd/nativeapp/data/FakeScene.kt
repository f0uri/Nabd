package app.nabd.nativeapp.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * A generated landscape for a generated photo post or a comment picture:
 * drawn in-app from the content's seed, so every post and every comment
 * picture is its own scene — no two share a picture. 16 sky palettes ×
 * 8 suns/moons × 5 cloud counts × 6 far layers × 6 mid layers × 8
 * foregrounds × 4 bird counts = 737,280 scenes, and the seed also jitters
 * every position and height, so even scenes of the same kind never line
 * up exactly. The result is a data:image/jpeg;base64 URI — the same shape
 * the vault keeps for real photos — so the existing image pipeline
 * displays it unchanged, and a post keeps the same picture every time
 * because the same seed always draws the same scene.
 */
object FakeScene {
    private const val WIDTH = 800
    private const val HEIGHT = 500

    /** (sky top, sky bottom) pairs: day, dawn, dusk, night, cloudy and more. */
    private val SKIES = listOf(
        0xFF7EC8E3.toInt() to 0xFFFDE68A.toInt(), 0xFFFFB75E.toInt() to 0xFFFFE3C2.toInt(), 0xFF6366F1.toInt() to 0xFFF472B6.toInt(),
        0xFF0F172A.toInt() to 0xFF334155.toInt(), 0xFF94A3B8.toInt() to 0xFFE2E8F0.toInt(), 0xFF38BDF8.toInt() to 0xFFDCFCE7.toInt(),
        0xFFF59E0B.toInt() to 0xFFFEF3C7.toInt(), 0xFF8B5CF6.toInt() to 0xFFDDD6FE.toInt(), 0xFF0EA5E9.toInt() to 0xFFBAE6FD.toInt(),
        0xFFFB7185.toInt() to 0xFFFFE4E6.toInt(), 0xFF14B8A6.toInt() to 0xFFCCFBF1.toInt(), 0xFFA16207.toInt() to 0xFFFDE68A.toInt(),
        0xFF1E3A8A.toInt() to 0xFF93C5FD.toInt(), 0xFFBE185D.toInt() to 0xFFFBCFE8.toInt(), 0xFF166534.toInt() to 0xFFBBF7D0.toInt(),
        0xFF7C2D12.toInt() to 0xFFFDDBA8.toInt()
    )
    private val FAR_COLORS = listOf(0xFF475569.toInt(), 0xFF64748B.toInt(), 0xFF334155.toInt(), 0xFF556B7A.toInt(), 0xFF3F4E5E.toInt(), 0xFF5B6B7C.toInt())

    fun sceneImage(seed: Long): String = try {
        sceneImageUnchecked(seed)
    } catch (_: Exception) {
        // JVM unit tests run against stub Android graphics (createBitmap
        // returns null there): no picture in tests, the real device draws it.
        ""
    }

    private fun sceneImageUnchecked(seed: Long): String {
        val random = Random(seed)
        val sky = SKIES[random.nextInt(SKIES.size)]
        val sunKind = random.nextInt(8) // 0-3 sun, 4-6 moon, 7 none
        val cloudCount = random.nextInt(5)
        val farKind = random.nextInt(6)
        val midKind = random.nextInt(6)
        val foreKind = random.nextInt(8)
        val birdCount = random.nextInt(4)
        val farColor = FAR_COLORS[random.nextInt(FAR_COLORS.size)]

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sky.
        paint.shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), sky.first, sky.second, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        // Sun or moon.
        if (sunKind < 7) {
            val cx = floatArrayOf(150f, 400f, 650f, 400f, 150f, 400f, 650f)[sunKind]
            val cy = if (sunKind < 4) 100f else 110f
            if (sunKind < 4) {
                paint.color = Color.argb(60, 255, 220, 100)
                canvas.drawCircle(cx, cy, 78f, paint)
                paint.color = 0xFFFFD54F.toInt()
                canvas.drawCircle(cx, cy, 46f, paint)
            } else {
                paint.color = 0xFFF1F5F9.toInt()
                canvas.drawCircle(cx, cy, 36f, paint)
                paint.color = 0xFFCBD5E1.toInt()
                canvas.drawCircle(cx - 10f, cy - 6f, 7f, paint)
                canvas.drawCircle(cx + 9f, cy + 8f, 5f, paint)
            }
        }

        // Clouds.
        paint.color = Color.argb(225, 255, 255, 255)
        repeat(cloudCount) {
            val x = random.nextInt(80, 720).toFloat()
            val y = random.nextInt(50, 170).toFloat()
            val s = random.nextInt(28, 52).toFloat()
            canvas.drawCircle(x, y, s, paint)
            canvas.drawCircle(x + s * 0.9f, y + s * 0.18f, s * 0.72f, paint)
            canvas.drawCircle(x - s * 0.9f, y + s * 0.22f, s * 0.66f, paint)
        }

        // Birds.
        paint.color = Color.argb(200, 30, 41, 59)
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        repeat(birdCount) {
            val x = random.nextInt(100, 700).toFloat()
            val y = random.nextInt(60, 200).toFloat()
            val s = random.nextInt(10, 18).toFloat()
            val path = Path()
            path.moveTo(x - s, y)
            path.quadTo(x - s / 2, y - s * 0.7f, x, y)
            path.quadTo(x + s / 2, y - s * 0.7f, x + s, y)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL

        // Far mountains: 2-3 peaks, base height varies with farKind.
        paint.color = farColor
        val baseY = 300f + farKind * 10f
        val peaks = 2 + (farKind % 2)
        val farPath = Path()
        farPath.moveTo(0f, baseY)
        var px = 0f
        repeat(peaks) {
            val topX = px + random.nextInt(140, 260)
            val topY = random.nextInt(140, 260).toFloat()
            farPath.lineTo(topX, topY)
            px = topX + random.nextInt(140, 260)
            farPath.lineTo(px.coerceAtMost(WIDTH.toFloat()), baseY)
        }
        farPath.lineTo(WIDTH.toFloat(), baseY)
        farPath.close()
        canvas.drawPath(farPath, paint)

        // Mid layer.
        when (midKind) {
            0 -> { // Rolling hills.
                paint.color = 0xFF65A30D.toInt()
                val path = Path()
                path.moveTo(0f, 500f)
                path.quadTo(200f, 300f, 450f, 400f)
                path.quadTo(650f, 480f, 800f, 360f)
                path.lineTo(800f, 500f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = 0xFF84CC16.toInt()
                val path2 = Path()
                path2.moveTo(0f, 500f)
                path2.quadTo(250f, 380f, 550f, 460f)
                path2.quadTo(700f, 500f, 800f, 440f)
                path2.lineTo(800f, 500f)
                path2.close()
                canvas.drawPath(path2, paint)
            }
            1 -> { // Sea.
                paint.color = 0xFF0284C7.toInt()
                canvas.drawRect(0f, 350f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
                paint.color = Color.argb(160, 255, 255, 255)
                paint.strokeWidth = 3f
                repeat(4) { i ->
                    val y = 375f + i * 28f
                    val x0 = random.nextInt(0, 400).toFloat()
                    canvas.drawLine(x0, y, x0 + random.nextInt(120, 300), y, paint)
                }
                paint.style = Paint.Style.FILL
            }
            2 -> { // City skyline.
                paint.color = 0xFF1E293B.toInt()
                var x = 0f
                while (x < WIDTH) {
                    val bw = random.nextInt(45, 95).toFloat()
                    val bh = random.nextInt(90, 190).toFloat()
                    canvas.drawRect(x, 380f - bh, x + bw, 380f, paint)
                    x += bw + random.nextInt(4, 18)
                }
                paint.color = 0xFFFDE047.toInt()
                repeat(14) {
                    val wx = random.nextInt(0, 780).toFloat()
                    val wy = random.nextInt(210, 350).toFloat()
                    canvas.drawRect(wx, wy, wx + 8f, wy + 10f, paint)
                }
            }
            3 -> { // Desert dunes.
                paint.color = 0xFFEAB308.toInt()
                val path = Path()
                path.moveTo(0f, 500f)
                path.quadTo(250f, 330f, 520f, 420f)
                path.quadTo(680f, 470f, 800f, 390f)
                path.lineTo(800f, 500f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = 0xFFF59E0B.toInt()
                val path2 = Path()
                path2.moveTo(0f, 500f)
                path2.quadTo(300f, 400f, 600f, 470f)
                path2.quadTo(720f, 495f, 800f, 460f)
                path2.lineTo(800f, 500f)
                path2.close()
                canvas.drawPath(path2, paint)
            }
            4 -> { // Forest.
                paint.color = 0xFF166534.toInt()
                canvas.drawRect(0f, 400f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
                var x = 10f
                while (x < WIDTH) {
                    val th = random.nextInt(60, 120).toFloat()
                    val tw = random.nextInt(34, 56).toFloat()
                    val path = Path()
                    path.moveTo(x, 400f - th)
                    path.lineTo(x + tw / 2, 400f - th - tw * 0.7f)
                    path.lineTo(x + tw, 400f - th)
                    path.close()
                    canvas.drawPath(path, paint)
                    x += tw + random.nextInt(6, 26)
                }
            }
            else -> { // Lake with reflections.
                paint.color = 0xFF0EA5E9.toInt()
                canvas.drawRect(0f, 360f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
                paint.color = Color.argb(140, 255, 255, 255)
                repeat(5) { i ->
                    val rx = random.nextInt(0, 600).toFloat()
                    canvas.drawRect(rx, 380f + i * 20f, rx + random.nextInt(80, 240), 388f + i * 20f, paint)
                }
            }
        }

        // Foreground.
        when (foreKind) {
            0 -> { // Trees.
                repeat(3) {
                    val x = random.nextInt(60, 740).toFloat()
                    paint.color = 0xFF92400E.toInt()
                    canvas.drawRect(x - 7f, 420f, x + 7f, 500f, paint)
                    paint.color = 0xFF15803D.toInt()
                    canvas.drawCircle(x, 400f, random.nextInt(34, 52).toFloat(), paint)
                }
            }
            1 -> { // Flowers.
                repeat(6) {
                    val x = random.nextInt(40, 760).toFloat()
                    val y = random.nextInt(430, 490).toFloat()
                    paint.color = 0xFF15803D.toInt()
                    paint.strokeWidth = 4f
                    canvas.drawLine(x, y, x, y + 22f, paint)
                    paint.style = Paint.Style.FILL
                    val petal = listOf(0xFFF472B6.toInt(), 0xFFFACC15.toInt(), 0xFFFB7185.toInt(), 0xFFC084FC.toInt(), 0xFFF97316.toInt(), 0xFF38BDF8.toInt())[it]
                    paint.color = petal
                    repeat(5) { p ->
                        val a = p * 2f * Math.PI.toFloat() / 5f
                        canvas.drawCircle(x + 11f * kotlin.math.cos(a), y + 11f * kotlin.math.sin(a), 8f, paint)
                    }
                    paint.color = 0xFFFDE047.toInt()
                    canvas.drawCircle(x, y, 7f, paint)
                }
            }
            2 -> { // Buildings.
                repeat(2) {
                    val x = random.nextInt(40, 620).toFloat()
                    val bw = random.nextInt(90, 150).toFloat()
                    val bh = random.nextInt(70, 130).toFloat()
                    paint.color = 0xFF475569.toInt()
                    canvas.drawRect(x, 500f - bh, x + bw, 500f, paint)
                    paint.color = 0xFFFDE047.toInt()
                    var wy = 500f - bh + 14f
                    while (wy < 485f) {
                        var wx = x + 12f
                        while (wx < x + bw - 14f) {
                            canvas.drawRect(wx, wy, wx + 12f, wy + 12f, paint)
                            wx += 26f
                        }
                        wy += 28f
                    }
                }
            }
            3 -> { // Rocks.
                paint.color = 0xFF78716C.toInt()
                repeat(4) {
                    val x = random.nextInt(60, 740).toFloat()
                    canvas.drawCircle(x, random.nextInt(460, 495).toFloat(), random.nextInt(18, 40).toFloat(), paint)
                }
            }
            4 -> { // Grass band.
                paint.color = 0xFF4D7C0F.toInt()
                canvas.drawRect(0f, 440f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
                paint.strokeWidth = 3f
                paint.color = 0xFF65A30D.toInt()
                repeat(24) {
                    val x = random.nextInt(0, 800).toFloat()
                    canvas.drawLine(x, 500f, x + random.nextInt(-8, 8), random.nextInt(430, 465).toFloat(), paint)
                }
                paint.style = Paint.Style.FILL
            }
            5 -> { // Beach umbrella.
                val x = random.nextInt(200, 600).toFloat()
                paint.color = 0xFF92400E.toInt()
                paint.strokeWidth = 5f
                canvas.drawLine(x, 380f, x, 500f, paint)
                paint.style = Paint.Style.FILL
                paint.color = 0xFFEF4444.toInt()
                val path = Path()
                path.moveTo(x - 90f, 385f)
                path.quadTo(x, 300f, x + 90f, 385f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = 0xFFF8FAFC.toInt()
                val path2 = Path()
                path2.moveTo(x - 45f, 385f)
                path2.quadTo(x - 22f, 330f, x, 385f)
                path2.close()
                canvas.drawPath(path2, paint)
            }
            6 -> { // Road.
                paint.color = 0xFF334155.toInt()
                canvas.drawRect(0f, 430f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
                paint.color = 0xFFF8FAFC.toInt()
                paint.strokeWidth = 5f
                var x = 0f
                while (x < WIDTH) {
                    canvas.drawLine(x, 465f, x + 40f, 465f, paint)
                    x += 80f
                }
                paint.style = Paint.Style.FILL
            }
            else -> { // Wooden bridge.
                paint.color = 0xFF92400E.toInt()
                val path = Path()
                path.moveTo(0f, 500f)
                path.quadTo(400f, 380f, 800f, 500f)
                path.lineTo(800f, 500f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = 0xFF78350F.toInt()
                repeat(7) { i ->
                    canvas.drawRect(60f + i * 110f, 440f, 68f + i * 110f, 500f, paint)
                }
            }
        }

        var quality = 72
        val bytes = ByteArrayOutputStream()
        do {
            bytes.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
            quality -= 8
        } while (bytes.size() > 128 * 1024 && quality >= 20)
        bitmap.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
