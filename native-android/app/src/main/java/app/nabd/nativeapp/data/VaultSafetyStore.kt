package app.nabd.nativeapp.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Read-only last-known-good store abstraction, injectable for repository tests. */
interface VaultSnapshotStore {
    data class Entry(val pin: Long, val vault: JSONObject)
    fun save(pin: Long, vault: JSONObject)
    fun read(namespace: String): Entry?
}

/**
 * Encrypted, versioned last-known-good copy of a strictly validated vault.
 * The caller updates it only after remote validation (or confirmed atomic pin).
 * It is a read-only recovery source: callers must never publish it as a write base.
 */
class VaultSafetyStore(context: Context, namespace: String) : VaultSnapshotStore {
    private val file = AtomicFile(File(context.filesDir, "nabd-vault-lkg-${namespace.take(24)}.bin"))
    private val cipher = KeystoreAes("nabd-vault-lkg-${namespace.take(24)}")

    @Synchronized
    override fun save(pin: Long, vault: JSONObject) {
        require(pin > 0)
        val raw = vault.toString()
        val content = JSONObject().put("version", FORMAT_VERSION).put("pin", pin)
            .put("sha256", sha256(raw.toByteArray(Charsets.UTF_8))).put("vault", raw)
        val encrypted = cipher.encrypt(content.toString())
        val output = JSONObject().put("version", FORMAT_VERSION).put("encrypted", encrypted).toString().toByteArray(Charsets.UTF_8)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(output)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) file.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    override fun read(namespace: String): VaultSnapshotStore.Entry? {
        if (!file.baseFile.exists()) return null
        return runCatching {
            val envelope = JSONObject(file.baseFile.readText(Charsets.UTF_8))
            require(envelope.optInt("version") == FORMAT_VERSION)
            val clear = cipher.decrypt(envelope.getString("encrypted")) ?: error("LKG_DECRYPT_FAILED")
            val content = JSONObject(clear)
            require(content.optInt("version") == FORMAT_VERSION)
            val raw = content.getString("vault")
            require(content.getString("sha256") == sha256(raw.toByteArray(Charsets.UTF_8)))
            val validated = VaultProtocol.validate(JSONObject(raw), namespace)
            VaultSnapshotStore.Entry(content.getLong("pin"), validated)
        }.getOrNull()
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
