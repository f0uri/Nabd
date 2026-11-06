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

/**
 * A small square copy of an inline photo, used for the saved accounts: the full
 * avatar is far too big for the encrypted book, so a 128px thumbnail is stored
 * and the account row shows a real picture instead of an initial.
 */
fun thumbnailImage(source: String, size: Int = 128): String? = runCatching {
    if (!validImage(source)) return null
    val bytes = Base64.decode(source.substringAfter(','), Base64.NO_WRAP)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val side = decoded.width.coerceAtMost(decoded.height).coerceAtLeast(1)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, size, size, true)
    val out = ByteArrayOutputStream()
    var quality = 80
    do { out.reset(); scaled.compress(Bitmap.CompressFormat.JPEG, quality, out); quality -= 12 } while (out.size() > 32 * 1024 && quality >= 25)
    val result = "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
    if (scaled !== square) scaled.recycle()
    if (square !== decoded) square.recycle()
    decoded.recycle()
    result.takeIf { validImage(it) }
}.getOrNull()
