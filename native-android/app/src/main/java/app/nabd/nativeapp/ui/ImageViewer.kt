package app.nabd.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Full-screen image viewer: pinch to zoom, drag down to dismiss, tap to close. */
@Composable internal fun ImageViewer(image: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (closing) 0f else 1f, animationSpec = tween(180), label = "viewer-alpha")
    LaunchedEffect(closing) { if (closing) { delay(190); onClose() } }
    Box(Modifier.fillMaxSize().background(Color(0xF2000000)).testTag("image-viewer")
        .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (drag > 170f) closing = true; drag = 0f },
            onVerticalDrag = { _, amount -> if (scale == 1f) drag = (drag + amount).coerceAtLeast(0f) }) }
        .pointerInput(Unit) { detectTapGestures(onTap = { if (scale == 1f) closing = true }, onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) },
        contentAlignment = Alignment.Center) {
        NabdImage(image, "الصورة بالحجم الكامل", Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale > 1f) offset + pan else Offset.Zero
            } }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y + drag; this.alpha = alpha }, ContentScale.Fit)
        IconButton(onClick = { closing = true }, modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).testTag("viewer-close")) {
            Icon(Icons.Outlined.Close, "إغلاق الصورة", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
