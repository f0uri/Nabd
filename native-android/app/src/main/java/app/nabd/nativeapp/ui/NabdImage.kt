package app.nabd.nativeapp.ui

import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.nabd.nativeapp.data.MediaFiles
import app.nabd.nativeapp.data.validImage
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ImageData(val source: String, val data: Any)
/** Coil 2 does not decode data: URIs itself. Decode the existing vault's inline
 * photos on IO into bytes, rather than passing an unsupported String model.
 * Loading/errors always retain a visible fallback, never an empty grey circle.
 */
@Composable internal fun NabdImage(source: String?, description: String, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit, fallback: @Composable () -> Unit = { Icon(Icons.Outlined.Image, null, tint = Muted) }) {
    var loaded by remember(source) { mutableStateOf(false) }
    val decoded by produceState<ImageData?>(null, source) {
        value = null
        if (source != null) value = withContext(Dispatchers.IO) {
            runCatching {
                ImageData(source, when {
                    source.startsWith("data:") -> {
                        require(validImage(source)); Base64.decode(source.substringAfter(','), Base64.NO_WRAP)
                    }
                    // A picture that travels as a Telegram document of its own.
                    MediaFiles.isReference(source) -> MediaFiles.file(source) ?: error("MEDIA_UNAVAILABLE")
                    else -> source
                })
            }.getOrNull()
        }
    }
    Box(modifier.semantics { stateDescription = if (loaded) "image:loaded" else "image:fallback" }, contentAlignment = Alignment.Center) {
        if (!loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() }
        decoded?.takeIf { it.source == source }?.let { image -> AsyncImage(image.data, description, Modifier.fillMaxSize(),
            contentScale = contentScale, onSuccess = { loaded = true }, onError = { loaded = false }) }
    }
}
