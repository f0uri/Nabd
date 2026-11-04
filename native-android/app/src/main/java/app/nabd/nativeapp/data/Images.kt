package app.nabd.nativeapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

suspend fun inlineImage(context: Context, uri: Uri): Pair<String, Double> = withContext(Dispatchers.IO) {
    require(uri.scheme in listOf("content", "file")) { "VALIDATION" }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth in 1..50000 && bounds.outHeight in 1..50000) { "VALIDATION" }
    var sample = 1
    while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("VALIDATION")
    try {
        val bytes = ByteArrayOutputStream()
        var quality = 85
        do { bytes.reset(); bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes); quality -= 10 } while (bytes.size() > 128 * 1024 && quality >= 15)
        require(bytes.size() <= 128 * 1024) { "TELEGRAM_IMAGE_TOO_LARGE" }
        "data:image/jpeg;base64,${Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)}" to (bitmap.width.toDouble() / bitmap.height)
    } finally { bitmap.recycle() }
}
