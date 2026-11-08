package app.nabd.nativeapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

/** Placeholder grey used while the real vault data is still loading. */
val ShimmerBase = Color(0xFFE2E8F0)

@Composable internal fun ShimmerBlock(modifier: Modifier, radius: Int = 24) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer-shift")
    Box(modifier.clip(RoundedCornerShape(radius.dp)).drawBehind {
        drawRect(color = ShimmerBase)
        val width = size.width
        drawRect(brush = Brush.linearGradient(listOf(Color.Transparent, GlassEdge, Color.Transparent),
            start = Offset(shift * width, 0f), end = Offset(shift * width + width * .55f, size.height)))
    })
}

/** Loading skeleton with the same geometry as a real post card, never a white screen. */
@Composable internal fun ShimmerFeed(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().testTag("feed-loading"), contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ShimmerBlock(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp), 22) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBlock(Modifier.size(68.dp), 34)
                ShimmerBlock(Modifier.size(width = 46.dp, height = 10.dp), 5)
            } }
        } }
        items(3) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(CardFill).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBlock(Modifier.size(40.dp), 20)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ShimmerBlock(Modifier.size(width = 120.dp, height = 12.dp), 6); ShimmerBlock(Modifier.size(width = 70.dp, height = 10.dp), 5) }
                }
                ShimmerBlock(Modifier.fillMaxWidth().height(14.dp), 7)
                ShimmerBlock(Modifier.fillMaxWidth().height(14.dp), 7)
                ShimmerBlock(Modifier.fillMaxWidth().height(150.dp), 18)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { ShimmerBlock(Modifier.weight(1f).height(22.dp), 11) } }
            }
        }
    }
}

/** The profile placeholders: cover, ringed avatar, counters and the posts grid. */
@Composable internal fun ProfileShimmer(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBlock(Modifier.fillMaxWidth().height(150.dp))
        Column(Modifier.fillMaxWidth().offset(y = (-46).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ShimmerBlock(Modifier.size(104.dp), radius = 52)
            ShimmerBlock(Modifier.padding(top = 10.dp).width(140.dp).height(18.dp), radius = 9)
            ShimmerBlock(Modifier.padding(top = 6.dp).width(90.dp).height(12.dp), radius = 6)
        }
        ShimmerBlock(Modifier.fillMaxWidth().height(72.dp))
        ShimmerBlock(Modifier.fillMaxWidth().height(46.dp), radius = 23)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { ShimmerBlock(Modifier.weight(1f).aspectRatio(1f), radius = 14) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { ShimmerBlock(Modifier.weight(1f).aspectRatio(1f), radius = 14) }
        }
    }
}
