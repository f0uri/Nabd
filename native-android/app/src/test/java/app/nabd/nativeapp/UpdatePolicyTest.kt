package app.nabd.nativeapp

import app.nabd.nativeapp.data.sha256
import app.nabd.nativeapp.updates.UpdatePolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val bytes = "not an actual installable APK".toByteArray()
    private fun manifest() = JSONObject().put("schema", 1).put("packageName", "app.nabd.social").put("versionCode", 5).put("versionName", "2.0.1").put("notes", JSONArray(listOf("تحسينات")))
        .put("apks", JSONObject().put("universal", JSONObject().put("url", "https://github.com/f0uri/Nabd/releases/download/test/nabd.apk").put("sha256", sha256(bytes)).put("size", bytes.size)))
    @Test fun olderAndEqualVersionsNeverOfferDowngrade() {
        assertNull(UpdatePolicy.parse(manifest(), 5, listOf("arm64-v8a")))
        assertNull(UpdatePolicy.parse(manifest(), 9, listOf("armeabi-v7a")))
    }
    @Test fun exactBytesMatchAndTamperingFails() {
        val release = UpdatePolicy.parse(manifest(), 4, listOf("arm64-v8a"))!!
        assertTrue(UpdatePolicy.verifies(release, bytes))
        assertFalse(UpdatePolicy.verifies(release, "tampered".toByteArray()))
        assertFalse(UpdatePolicy.verifies(release, bytes.copyOf(bytes.size - 1)))
    }
    @Test fun foreignPackageOrDownloadOriginAreRejected() {
        assertTrue(runCatching { UpdatePolicy.parse(manifest().put("packageName", "other.app"), 4, emptyList()) }.isFailure)
        listOf("http://github.com/f0uri/Nabd/releases/download/test/nabd.apk", "https://evil.example/nabd.apk", "https://github.com/other/app/releases/download/test/nabd.apk", "https://github.com@evil.example/nabd.apk").forEach { url ->
            val m = manifest(); m.getJSONObject("apks").getJSONObject("universal").put("url", url)
            assertTrue(url, runCatching { UpdatePolicy.parse(m, 4, emptyList()) }.isFailure)
        }
    }
    @Test fun invalidHashSizeAndRedirectHostsFailClosed() {
        val m = manifest(); m.getJSONObject("apks").getJSONObject("universal").put("size", UpdatePolicy.MAX_APK_BYTES + 1)
        assertTrue(runCatching { UpdatePolicy.parse(m, 4, emptyList()) }.isFailure)
        assertFalse(UpdatePolicy.allowedDownloadHost("api.telegram.org"))
        assertFalse(UpdatePolicy.allowedDownloadHost("github.com.evil.example"))
        assertTrue(UpdatePolicy.allowedDownloadHost("release-assets.githubusercontent.com"))
    }
}
