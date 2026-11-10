package app.nabd.nativeapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nabd.nativeapp.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/** أخضر مزرق غامق (فوق) — كيفما فالريل ديال Cura. */
private val TealDark = Color(0xFF0B3D3A)

/** أخضر مزرق فاتح (تحت) — كيفما فالريل ديال Cura. */
private val TealGreen = Color(0xFF0E8A6E)

/** أحمر القلب والنقطة ديال راس الخط: نفس أحمر اللوغو. */
private val HeartRed = Color(0xFFE8264A)

/** مدة شاشة الترحيب قبل ما يمشي للفيد. */
private const val SPLASH_DURATION_MS = 2500L

/** مدة رسم خط ECG من اليسار لليمين. */
private const val DRAW_DURATION_MS = 2000

/**
 * شاشة الترحيب من بعد إنشاء حساب جديد بـ Google (2.9.18).
 *
 * خلفية الريل: متدرجة من الأخضر المزرق الغامق لتحت مع علامات +
 * بيضاء خفيفة، وفوقها لوغو نبض والقلب الأحمر كانبض (كيفما فـ
 * 2.9.17)، وتحت القلب خط ECG أبيض رفيع كيرسم راسو من اليسار
 * لليمين مع نقطة حمراء صغيرة فراس الخط — التوقيع ديال الريل.
 * من بعد ~2.5 ثواني كيمشي للفيد بلا ما يضغط المستخدم حتى حاجة.
 * ما كاين حتى نص. كتبان غير مرة وحدة ملي كيتصاوب الحساب؛
 * الدخول العادي (وحتى الدخول بـ Google بحساب موجود) ما كيشوفهاش.
 */
@Composable
fun WelcomeSplashScreen(onDone: () -> Unit) {
    // القلب: نبضة مزدوجة (دقّة-دقّة) كل ثانية — بحال دقات القلب الحقيقي:
    // دقة قوية، رجوع، دقة خفيفة، رجوع، ثم راحة حتى الثانية الجاية.
    val beat = rememberInfiniteTransition(label = "heartbeat")
    val scale by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1.22f at 120
                1f at 240
                1.14f at 360
                1f at 500
                1f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "heartbeat-scale"
    )
    // الخط كيرسم راسو من 0 حتى 1 فـ ~2 ثواني، ومن بعد كيبقى مرسوم
    // حتى كتسالي مدة الشاشة (2.5 ثانية) وكنمشيو للفيد.
    val drawProgress = remember { Animatable(0f) }
    // بلايص علامات + ثابتة (seed واحد) باش ما يتبدلوش مع كل إعادة رسم.
    val plusMarks = remember { generatePlusMarks() }
    LaunchedEffect(Unit) {
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = DRAW_DURATION_MS, easing = LinearEasing)
        )
        delay(SPLASH_DURATION_MS - DRAW_DURATION_MS)
        onDone()
    }
    val progress = drawProgress.value
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val topPad = maxHeight * 0.16f
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1) الخلفية المتدرجة: غامق فوق، فاتح لتحت.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(TealDark, TealGreen),
                    startY = 0f,
                    endY = size.height
                )
            )
            // 2) علامات + بيضاء خفيفة مبعثرة.
            for (mark in plusMarks) {
                val x = mark.x * size.width
                val y = mark.y * size.height
                val half = mark.halfDp.dp.toPx()
                val stroke = mark.strokeDp.dp.toPx()
                val color = Color.White.copy(alpha = mark.alpha)
                drawLine(color, Offset(x - half, y), Offset(x + half, y), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(x, y - half), Offset(x, y + half), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            // 3) خط ECG تحت القلب: كيرسم راسو من اليسار لليمين.
            val midY = size.height * 0.68f
            val amp = 28.dp.toPx()
            val (path, tip) = ecgUpTo(progress, size.width, midY, amp)
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // 4) نقطة حمراء صغيرة فراس الخط.
            if (progress > 0f) {
                drawCircle(color = HeartRed, radius = 4.dp.toPx(), center = tip)
            }
        }
        // اللوغو والقلب فالنص الفوقاني.
        Column(
            modifier = Modifier.fillMaxSize().padding(top = topPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.nabd_icon),
                contentDescription = "نبض",
                modifier = Modifier.size(148.dp).clip(RoundedCornerShape(36.dp))
            )
            Box(
                modifier = Modifier.size(72.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .drawBehind { drawHeart(HeartRed) }
            )
        }
    }
}

/** قلب مرسوم بمنحنيات بيزير: قمتين فوق ونقطة لتحت. */
private fun DrawScope.drawHeart(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w / 2f, h * 0.95f)
        cubicTo(w * 0.05f, h * 0.55f, w * 0.1f, h * 0.05f, w / 2f, h * 0.32f)
        cubicTo(w * 0.9f, h * 0.05f, w * 0.95f, h * 0.55f, w / 2f, h * 0.95f)
        close()
    }
    drawPath(path, color)
}

/** علامة + وحدة: بلاصة (نسبة من العرض/الطول)، حجم، شفافية. */
private data class PlusMark(
    val x: Float,
    val y: Float,
    val halfDp: Float,
    val alpha: Float,
    val strokeDp: Float
)

/** 26 علامة + فبلايص عشوائية ثابتة (نفس الـ seed ديما). */
private fun generatePlusMarks(): List<PlusMark> {
    val random = Random(20261111)
    return List(26) {
        PlusMark(
            x = random.nextFloat() * 0.92f + 0.04f,
            y = random.nextFloat() * 0.88f + 0.06f,
            halfDp = random.nextFloat() * 4f + 4f,
            alpha = random.nextFloat() * 0.10f + 0.12f,
            strokeDp = 2f
        )
    }
}

/**
 * شكل موجة ECG: (نسبة x من العرض، الارتفاع بالنسبة للسعة).
 * السالب = لفوق: موجة P صغيرة، Q صغيرة لتحت، R طالعة بزاف،
 * S نازلة، ومن بعد موجة T صغيرة، ثم خط مستقيم حتى الآخر.
 */
private val EcgPoints = listOf(
    0.00f to 0.00f,
    0.08f to 0.00f,
    0.12f to -0.10f,
    0.16f to -0.18f,
    0.20f to -0.10f,
    0.24f to 0.00f,
    0.27f to 0.00f,
    0.29f to 0.12f,
    0.31f to -1.00f,
    0.33f to 0.35f,
    0.35f to 0.00f,
    0.42f to 0.00f,
    0.47f to -0.14f,
    0.52f to -0.22f,
    0.57f to -0.14f,
    0.62f to 0.00f,
    1.00f to 0.00f
)

/**
 * كيبني المسار ديال ECG حتى نسبة [progress] من العرض، وكيرجع
 * المسار مع بلاصة راسو (فين غادي تكون النقطة الحمراء).
 * x مرتبة تصاعدياً، فالقص كيتم غير بقطع آخر مقطع عند الحد.
 */
private fun ecgUpTo(progress: Float, width: Float, midY: Float, amp: Float): Pair<Path, Offset> {
    val path = Path()
    val xLimit = progress.coerceIn(0f, 1f) * width
    var tip = Offset(0f, midY)
    var prevX = 0f
    var prevY = midY
    var started = false
    for ((xFrac, yAmp) in EcgPoints) {
        val x = xFrac * width
        val y = midY + yAmp * amp
        if (x > xLimit) {
            val t = if (x > prevX) (xLimit - prevX) / (x - prevX) else 0f
            val tipY = prevY + (y - prevY) * t
            tip = Offset(xLimit, tipY)
            if (!started) path.moveTo(0f, midY) else path.lineTo(xLimit, tipY)
            return path to tip
        }
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
        tip = Offset(x, y)
        prevX = x
        prevY = y
    }
    return path to tip
}
