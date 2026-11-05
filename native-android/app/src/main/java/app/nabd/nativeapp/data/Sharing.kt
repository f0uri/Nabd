package app.nabd.nativeapp.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun shareIntent(context: Context, post: FeedPost): Intent = withContext(Dispatchers.IO) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "${post.author.name} · نبض\n${post.text}")
    post.image?.let { image ->
        require(validImage(image)) { "VALIDATION" }
        val mime = image.substringAfter("data:").substringBefore(';')
        val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val folder = File(context.cacheDir, "shared-media").apply { mkdirs() }
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000 }?.forEach { it.delete() }
        val output = File(folder, "${digest(image)}.$extension")
        if (!output.exists()) output.writeBytes(Base64.decode(image.substringAfter(','), Base64.NO_WRAP))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.native.updates", output)
        intent.setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("صورة منشور نبض", uri)
    }
    // Opening a chooser is not proof a share completed: never fake shares counts.
    Intent.createChooser(intent, "مشاركة المنشور")
}

/** Plain invite text behind the instant copy button. */
fun inviteLink(code: String): String = if (code.isBlank()) "نبض" else "انضم ليا على نبض:\nnabd://invite/$code"

/** Real invite link. The code is the account's own vault-side inviteCode. */
fun inviteIntent(context: Context, code: String): Intent = Intent(Intent.ACTION_SEND).setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, if (code.isBlank()) "نبض" else "انضم ليا على نبض:\nnabd://invite/$code")
    .let { Intent.createChooser(it, "دعوة صديق") }
