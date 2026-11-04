package app.nabd.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nabd.nativeapp.data.Person
import app.nabd.nativeapp.data.isAdminHandle

/** Royal red of the owner seal; the blue tone is reserved for a future real verification system. */
val SealRed = Color(0xFFE11D48)
/** Blue verification badge granted by the official account, distinct from the owner seal. */
val VerifiedBlue = Color(0xFF0095F6)

private const val SEAL_PATH = "M11.96 0.97 14.43 2.83 17.5 2.45 18.67 5.31 21.53 6.48 21.14 9.54 23.01 12.02 21.14 14.49 21.53 17.55 18.67 18.72 17.5 21.58 14.43 21.2 11.96 23.06 9.49 21.2 6.42 21.58 5.25 18.72 2.39 17.55 2.78 14.49 0.91 12.02 2.78 9.54 2.39 6.48 5.25 5.31 6.42 2.45 9.49 2.83"
private const val CHECK_PATH = "M9.66 11.04 11.22 12.6 14.54 9.11"

/** Parses "x y x y ..." coordinate pairs into vector path nodes. */
private fun poly(data: String, close: Boolean): List<PathNode> {
    val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(data).map { it.value.toFloat() }.toList()
    val nodes = ArrayList<PathNode>(numbers.size / 2 + 1)
    nodes.add(PathNode.MoveTo(numbers[0], numbers[1]))
    var index = 2
    while (index + 1 < numbers.size) { nodes.add(PathNode.LineTo(numbers[index], numbers[index + 1])); index += 2 }
    if (close) nodes.add(PathNode.Close)
    return nodes
}

private fun sealVector(tint: Color): ImageVector = ImageVector.Builder("NabdOwnerSeal", 24.dp, 24.dp, 24f, 24f)
    .addPath(pathData = poly(SEAL_PATH, true), fill = SolidColor(tint))
    .addPath(pathData = poly(CHECK_PATH, false), stroke = SolidColor(Color.White), strokeLineWidth = 2.3f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    .build()

/** 12-point scalloped owner seal with a white check inside it. */
@Composable fun VerifiedSeal(size: Dp = 15.dp, tint: Color = SealRed, label: String = "حساب صاحب التطبيق") {
    val vector = remember(tint) { sealVector(tint) }
    Icon(vector, label, modifier = Modifier.size(size), tint = Color.Unspecified)
}

/** Name row that shows the seal whenever the account carries the owner handle. */
@Composable internal fun PersonName(person: Person, modifier: Modifier = Modifier, size: Int = 15, color: Color = TextMain, weight: FontWeight = FontWeight.Black, badge: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(person.name, color = color, fontSize = size.sp, lineHeight = (size + 3).sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isAdminHandle(person.username)) {
            VerifiedSeal(size = (size - 1).dp)
            if (badge) Text("حساب رسمي", color = Color.White, fontSize = (size - 6).coerceAtLeast(9).sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SealRed).padding(horizontal = 5.dp, vertical = 1.dp))
        } else if (person.verified) VerifiedSeal(size = (size - 1).dp, tint = VerifiedBlue, label = "حساب موثّق")
    }
}

/** Animates between real values only; it never invents counts. */
@Composable internal fun RollingNumber(value: Int, fontSize: Int = 18, color: Color = TextMain) {
    val animated by androidx.compose.animation.core.animateIntAsState(targetValue = value,
        animationSpec = androidx.compose.animation.core.tween(600), label = "rolling-$value-$fontSize")
    Text(animated.toString(), color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Black)
}
