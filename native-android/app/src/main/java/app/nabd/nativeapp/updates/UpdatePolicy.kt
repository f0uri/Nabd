package app.nabd.nativeapp.updates

import app.nabd.nativeapp.data.sha256
import org.json.JSONObject
import java.net.URI

/** Pure policy, separately testable without Android or live GitHub. */
data class ApkRelease(val versionCode: Long, val versionName: String, val notes: List<String>, val url: String, val sha256: String, val size: Long)
object UpdatePolicy {
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    fun parse(json: JSONObject, installed: Long, supportedAbis: List<String>): ApkRelease? =
        parseForPackage(json, installed, supportedAbis, "app.nabd.social")
    /** Trial-only channel: the trial build (package .design) reads its own manifest. The real app never takes this path. */
    fun parseTrial(json: JSONObject, installed: Long, supportedAbis: List<String>): ApkRelease? =
        parseForPackage(json, installed, supportedAbis, "app.nabd.social.design")
    private fun parseForPackage(json: JSONObject, installed: Long, supportedAbis: List<String>, expectedPackage: String): ApkRelease? {
        require(json.getInt("schema") == 1 && json.getString("packageName") == expectedPackage) { "INVALID_UPDATE" }
        val version = json.getLong("versionCode")
        if (version <= installed) return null
        val apks = json.getJSONObject("apks")
        val key = supportedAbis.firstOrNull { apks.has(it) } ?: "universal"
        require(apks.has(key)) { "NO_COMPATIBLE_APK" }
        val apk = apks.getJSONObject(key); val url = apk.getString("url"); val hash = apk.getString("sha256"); val size = apk.getLong("size")
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null && uri.path.startsWith("/f0uri/Nabd/releases/download/") && uri.path.endsWith(".apk")) { "INVALID_UPDATE_URL" }
        require(hash.matches(Regex("[a-f0-9]{64}")) && size in 1..MAX_APK_BYTES) { "INVALID_UPDATE" }
        val notes = json.getJSONArray("notes").let { array -> (0 until array.length()).take(15).map { array.getString(it).take(500) } }
        return ApkRelease(version, json.getString("versionName").take(80), notes, url, hash, size)
    }
    fun verifies(release: ApkRelease, bytes: ByteArray): Boolean = bytes.size.toLong() == release.size && sha256(bytes) == release.sha256
    fun allowedDownloadHost(host: String) = host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
}
