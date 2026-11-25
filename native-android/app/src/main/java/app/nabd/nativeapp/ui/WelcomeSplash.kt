package app.nabd.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Full-screen, one-shot welcome moment for successful Google or Pinterest sign-in. */
@Composable
fun WelcomeSplash(provider: String, name: String, onContinue: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "welcome-motion")
    val pulse by motion.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(1450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val orbit by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit")
    val shimmer by motion.animateFloat(.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "shimmer")
    val pin = provider == "pinterest"
    val platform = if (pin) "PINTEREST" else "GMAIL"
    val providerTint = if (pin) Color(0xFFE60023) else Color(0xFF69A7FF)
    val greeting = name.trim().takeIf { it.isNotEmpty() }?.let { "مرحبا $it" } ?: "مرحبا بيك"

    Box(Modifier.fillMaxSize().background(Color(0xFF070B18)).testTag("welcome-splash")) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(Brush.radialGradient(listOf(Color(0xFF123E89).copy(alpha = .60f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(w * .50f, h * .38f), radius = w * .76f), radius = w * .76f, center = androidx.compose.ui.geometry.Offset(w * .50f, h * .38f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF071A3C).copy(alpha = .70f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(w * .08f, h * .84f), radius = w * .58f), radius = w * .58f, center = androidx.compose.ui.geometry.Offset(w * .08f, h * .84f))
            // Small, deterministic stars: quiet texture, no image assets or network.
            repeat(46) { i ->
                val x = ((i * 73 + 19) % 997) / 997f * w
                val y = ((i * 137 + 31) % 991) / 991f * h
                val r = if (i % 7 == 0) 1.8f else 1.0f
                drawCircle(Color.White.copy(alpha = if (i % 3 == 0) .42f else .18f), r, androidx.compose.ui.geometry.Offset(x, y))
            }
        }
        // Animated orbital rings and a clean, scalable ECG badge.
        Box(Modifier.align(Alignment.Center).padding(bottom = 112.dp).size(236.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val radius = size.minDimension * .39f
                drawCircle(Color(0xFF61A5FF).copy(alpha = .10f), radius = radius * 1.36f * pulse, center = c)
                drawCircle(Color(0xFF61A5FF).copy(alpha = .20f), radius = radius * pulse, center = c, style = Stroke(width = 1.4.dp.toPx()))
                drawCircle(Color.White.copy(alpha = .12f), radius = radius * 1.21f, center = c, style = Stroke(width = 1.dp.toPx()))
                val orbitRadius = radius * 1.21f
                val angle = orbit * (PI.toFloat() / 180f)
                drawCircle(Color(0xFF75C8FF).copy(alpha = .92f), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(c.x + cos(angle) * orbitRadius, c.y + sin(angle) * orbitRadius))
                drawCircle(providerTint.copy(alpha = shimmer), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(c.x + cos(angle + PI.toFloat()) * orbitRadius, c.y + sin(angle + PI.toFloat()) * orbitRadius))
            }
            Box(Modifier.size(122.dp).shadow(28.dp, CircleShape, spotColor = Color(0xFF168AFF), ambientColor = Color(0xFF168AFF))
                .clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF138AFF), Color(0xFF504BFF), Color(0xFF17C8DD))))
                .border(1.dp, Color.White.copy(alpha = .48f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Color(0xFF09152B).copy(alpha = .84f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Favorite, contentDescription = "نبض", tint = Color.White, modifier = Modifier.size(45.dp))
                    Canvas(Modifier.size(78.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * .16f, size.height * .52f)
                            lineTo(size.width * .34f, size.height * .52f)
                            lineTo(size.width * .43f, size.height * .35f)
                            lineTo(size.width * .55f, size.height * .70f)
                            lineTo(size.width * .66f, size.height * .43f)
                            lineTo(size.width * .73f, size.height * .52f)
                            lineTo(size.width * .86f, size.height * .52f)
                        }
                        drawPath(path, Color(0xFF4CC9FF), style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .08f)).border(1.dp, Color.White.copy(alpha = .13f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Verified, null, tint = providerTint, modifier = Modifier.size(14.dp))
                    Text("تم التحقق من الدخول بـ $platform", color = Color.White.copy(alpha = .90f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        Column(Modifier.align(Alignment.Center).padding(top = 142.dp, start = 28.dp, end = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("نبض", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Text(greeting, color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 39.sp)
            Spacer(Modifier.height(8.dp))
            Text("مساحتك دابا واجدة…\nخلي اللحظة تنبض.", color = Color(0xFFB6C5DF), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
            Spacer(Modifier.height(28.dp))
            // Progress line gently sweeps while the feed prepares underneath.
            Box(Modifier.width(124.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) {
                Box(Modifier.fillMaxWidth(.35f + shimmer * .45f).height(3.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF58D8FF), Color(0xFF657BFF), providerTint))))
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .09f))
                .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onContinue)
                .padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دخول لنبض", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text("A PLACE THAT BEATS WITH YOU", Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp), color = Color.White.copy(alpha = .35f), fontSize = 9.sp, letterSpacing = 2.sp)
    }
}
