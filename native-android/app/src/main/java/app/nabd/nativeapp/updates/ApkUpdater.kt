package app.nabd.nativeapp.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.nabd.nativeapp.BuildConfig
import app.nabd.nativeapp.data.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ApkUpdater(private val context: Context) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    @Volatile private var downloadCall: Call? = null
    private val directory get() = File(context.cacheDir, "native-updates").apply { mkdirs() }
    fun cancel() { downloadCall?.cancel() }
    private fun response(url: String, downloading: Boolean = false): Response {
        var next = url
        repeat(6) {
            val request = Request.Builder().url(next).header("Accept", "application/vnd.github+json").header("User-Agent", "Nabd-Android/${BuildConfig.VERSION_NAME}").build()
            val host = request.url.host
            require(request.url.isHttps && request.url.username.isEmpty() && request.url.password.isEmpty() && request.url.port == 443 && (host == "api.github.com" || UpdatePolicy.allowedDownloadHost(host))) { "INVALID_UPDATE_URL" }
            val call = client.newCall(request)
            downloadCall = call
            val result = call.execute()
            if (result.code in listOf(301, 302, 303, 307, 308)) {
                val destination = result.header("Location")?.let { result.request.url.resolve(it) }
                result.close()
                require(destination != null && destination.isHttps && UpdatePolicy.allowedDownloadHost(destination.host)) { "INVALID_UPDATE_URL" }
                next = destination.toString()
            } else {
                if (!result.isSuccessful) { result.close(); error("UPDATE_NETWORK_ERROR") }
                return result
            }
        }
        error("UPDATE_NETWORK_ERROR")
    }
    suspend fun check(): ApkRelease? = withContext(Dispatchers.IO) {
        val releases = response("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases?per_page=20").use { res ->
            val bytes = readBounded(res.body!!.byteStream(), 1024 * 1024)
            require(bytes.size <= 1024 * 1024) { "INVALID_UPDATE" }
            JSONArray(bytes.toString(Charsets.UTF_8))
        }
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val assets = release.getJSONArray("assets")
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.optString("name") != "nabd-native-update.json") continue
                val url = asset.getString("browser_download_url")
                require(Uri.parse(url).let { it.scheme == "https" && it.host == "github.com" && it.path?.startsWith("/f0uri/Nabd/releases/download/") == true }) { "INVALID_UPDATE_URL" }
                val manifest = response(url).use { res ->
                    val bytes = readBounded(res.body!!.byteStream(), 65536)
                    require(bytes.size <= 65536) { "INVALID_UPDATE" }
                    JSONObject(bytes.toString(Charsets.UTF_8))
                }
                val candidate = UpdatePolicy.parse(manifest, BuildConfig.VERSION_CODE.toLong(), Build.SUPPORTED_ABIS.toList())
                if (candidate != null) return@withContext candidate
            }
        }
        null
    }
    suspend fun download(release: ApkRelease, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val part = File(directory, "${release.versionCode}.part"); val apk = File(directory, "${release.versionCode}.apk")
        part.delete(); apk.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            response(release.url, true).use { res ->
                val body = res.body!!
                require(body.contentLength() == -1L || body.contentLength() == release.size) { "INVALID_APK" }
                body.byteStream().use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L; var lastProgress = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        require(total <= release.size && total <= UpdatePolicy.MAX_APK_BYTES) { "INVALID_APK" }
                        output.write(buffer, 0, count); digest.update(buffer, 0, count)
                        if (System.currentTimeMillis() - lastProgress > 100 || total == release.size) { progress(total, release.size); lastProgress = System.currentTimeMillis() }
                    }
                    require(total == release.size) { "INVALID_APK" }
                } }
            }
            require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == release.sha256) { "INVALID_APK" }
            verifyArchive(part, release.versionCode)
            currentCoroutineContext().ensureActive()
            check(part.renameTo(apk)) { "UPDATE_STORAGE_ERROR" }
            apk
        } finally { part.delete(); downloadCall = null }
    }
    @Suppress("DEPRECATION")
    fun verifyArchive(file: File, version: Long) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("INVALID_APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && code == version && code > BuildConfig.VERSION_CODE) { "INVALID_APK" }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> {
            val list = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return list.orEmpty().map { signature -> app.nabd.nativeapp.data.sha256(signature.toByteArray()) }.toSet()
        }
        val expected = signatures(installed)
        require(expected.isNotEmpty() && signatures(archive) == expected) { "INVALID_APK_SIGNATURE" }
    }
    /** User initiated only. Unknown-source permission and OS installation are never bypassed. */
    fun installIntent(file: File, release: ApkRelease): Intent {
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.name == "${release.versionCode}.apk") { "INVALID_APK" }
        // Revalidate immediately before granting another app access to the file.
        require(file.length() == release.size && file.inputStream().use { input ->
            val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
            hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        } == release.sha256) { "INVALID_APK" }
        verifyArchive(file, release.versionCode)
        if (!context.packageManager.canRequestPackageInstalls()) return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.native.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
