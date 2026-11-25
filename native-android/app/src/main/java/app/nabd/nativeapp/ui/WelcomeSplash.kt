package app.nabd.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A brief, one-shot greeting: the ECG resolves into the user's avatar, then reveals the feed underneath. */
@Composable
fun WelcomeSplash(provider: String, name: String, avatar: String? = null, onContinue: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "welcome-pulse")
    val pulse by motion.animateFloat(.96f, 1.04f, infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing)), label = "pulse")
    val shimmer by motion.animateFloat(.62f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing)), label = "halo")
    var traceDrawn by remember { mutableStateOf(false) }
    val lineProgress by animateFloatAsState(if (traceDrawn) 1f else 0f, animationSpec = tween(920, easing = FastOutSlowInEasing), label = "ecg-draw")
    var leaving by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (leaving) 0f else 1f, tween(260), label = "splash-fade")
    val scale by animateFloatAsState(if (leaving) 1.025f else 1f, tween(260), label = "splash-reveal")
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isPinterest = provider == "pinterest"
    val sourceName = if (isPinterest) "Pinterest" else "Gmail"
    val sourceTint = if (isPinterest) Color(0xFFE60023) else Color(0xFF75A9FF)
    val greeting = name.trim().takeIf(String::isNotEmpty)?.let { "مرحبا، $it" } ?: "مرحبا بيك"

    fun finish() {
        if (leaving) return
        leaving = true
        scope.launch { delay(240); onContinue() }
    }
    LaunchedEffect(provider) { delay(40); traceDrawn = true; delay(1_240); finish() }

    Box(Modifier.fillMaxSize().background(Color(0xFF070A14))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val center = Offset(w * .5f, h * .43f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF173A76).copy(alpha = .72f), Color(0xFF10162C).copy(alpha = .25f), Color.Transparent), center, w * .78f), w * .78f, center)
            drawCircle(Brush.radialGradient(listOf(sourceTint.copy(alpha = .12f), Color.Transparent), Offset(w * .92f, h * .18f), w * .42f), w * .42f, Offset(w * .92f, h * .18f))

            // A single clean trace. It draws once, then becomes the reveal line into the feed.
            val ecg = Path().apply {
                moveTo(-w * .08f, h * .43f); lineTo(w * .30f, h * .43f)
                lineTo(w * .38f, h * .43f); lineTo(w * .42f, h * .39f); lineTo(w * .455f, h * .49f)
                lineTo(w * .49f, h * .30f); lineTo(w * .53f, h * .47f); lineTo(w * .57f, h * .43f)
                lineTo(w * 1.08f, h * .43f)
            }
            drawPath(ecg, Color.White.copy(alpha = .10f), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
            clipRect(right = w * lineProgress) {
                drawPath(ecg, Color(0xFF63D9FF).copy(alpha = .78f), style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(Color.White.copy(alpha = .26f), 1.3.dp.toPx(), Offset(w * .18f, h * .20f))
            drawCircle(Color.White.copy(alpha = .18f), 1.dp.toPx(), Offset(w * .82f, h * .73f))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)
            .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Text("نبض", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Favorite, null, tint = Color(0xFF73D9FF), modifier = Modifier.size(18.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0xFF63C8FF).copy(alpha = .10f * shimmer), radius = size.minDimension * .49f * pulse, center = center)
                        drawCircle(Color.White.copy(alpha = .22f), radius = size.minDimension * .39f, center = center, style = Stroke(width = 1.dp.toPx()))
                        drawArc(Brush.sweepGradient(listOf(Color(0xFF61D8FF), Color(0xFF6479FF), sourceTint, Color(0xFF61D8FF))),
                            startAngle = -90f, sweepAngle = 260f, useCenter = false,
                            topLeft = Offset(size.width * .10f, size.height * .10f), size = androidx.compose.ui.geometry.Size(size.width * .80f, size.height * .80f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Box(Modifier.size(90.dp).shadow(20.dp, CircleShape, spotColor = Color(0xFF289DFF), ambientColor = Color(0xFF289DFF))
                        .clip(CircleShape).background(Color(0xFF101A2E)).border(2.dp, Color.White.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) {
                        if (avatar != null) NabdImage(avatar, name.ifBlank { "صورة الحساب" }, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) {
                            Icon(Icons.Outlined.Favorite, "نبض", tint = Color(0xFF73D9FF), modifier = Modifier.size(35.dp))
                        } else Icon(Icons.Outlined.Favorite, "نبض", tint = Color(0xFF73D9FF), modifier = Modifier.size(35.dp))
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 5.dp).size(26.dp).clip(CircleShape)
                        .background(Color(0xFF111A2D)).border(1.dp, Color.White.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Verified, null, tint = sourceTint, modifier = Modifier.size(16.dp))
                    }
                }
                Text(greeting, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 37.sp)
                Text("نبضك رجع.", color = Color(0xFFB8C7DF), fontSize = 15.sp, textAlign = TextAlign.Center)
                Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .075f)).border(1.dp, Color.White.copy(alpha = .11f), CircleShape)
                    .padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text("دخول بـ $sourceName", color = Color.White.copy(alpha = .9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF138AFF), Color(0xFF514DFF), Color(0xFF17BFD0))))
                    .clickable(role = Role.Button, onClick = { view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); finish() })
                    .padding(horizontal = 23.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("دخول للفيد", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(11.dp))
                Text("المس للتخطي", color = Color.White.copy(alpha = .44f), fontSize = 10.sp)
            }
        }
    }
}
