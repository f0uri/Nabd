package app.nabd.nativeapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nabd.nativeapp.R
import kotlinx.coroutines.delay

/** أحمر القلب: نفس أحمر اللوغو ولون الحساب الافتراضي. */
private val HeartRed = Color(0xFFE8264A)

/** مدة شاشة الترحيب قبل ما يمشي للفيد. */
private const val SPLASH_DURATION_MS = 2500L

/**
 * شاشة الترحيب من بعد إنشاء حساب جديد بـ Google (2.9.17).
 *
 * لوغو نبض + قلب أحمر كانبض فالوسط، ومن بعد ~2.5 ثواني كيمشي للفيد
 * بلا ما يضغط المستخدم حتى حاجة. ما كاين حتى زر وحتى نص — غير
 * اللوغو والقلب. كتبان غير مرة وحدة ملي كيتصاوب الحساب؛ الدخول
 * العادي (وحتى الدخول بـ Google بحساب موجود) ما كيشوفهاش.
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
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        onDone()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
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
