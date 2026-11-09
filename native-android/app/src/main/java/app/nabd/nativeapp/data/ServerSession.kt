package app.nabd.nativeapp.data

import android.content.Context

/**
 * الجلسة ديال Google — مشفرة فـ Android keystore، بنفس الطريقة ديال
 * جلسة التليجرام. كتحفظ علامة محلية (`telegram:<sub>` ديال Google)،
 * **ماشي** JWT ديال سيرڤور: ما كاين حتى سيرڤور ديال نبض.
 *
 * هادي **ماشي** هي جلسة النسخة: النسخة كتفضل هي اللي فيها بيانات التطبيق،
 * والعلامة غير كتكمّلها. كتتحيد مع الخروج وتعطيل الحساب وحذفو.
 */
class ServerSession(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences("nabd.server.session.$namespace", Context.MODE_PRIVATE)
    private val crypto = KeystoreAes("nabd.server.session.$namespace")

    @get:Synchronized @set:Synchronized
    var token: String?
        get() {
            val encoded = preferences.getString("encryptedToken", null) ?: return null
            return crypto.decrypt(encoded)
        }
        set(value) {
            if (value == null) { preferences.edit().remove("encryptedToken").commit(); return }
            check(preferences.edit().putString("encryptedToken", crypto.encrypt(value)).commit()) { "SESSION_STORAGE_ERROR" }
        }
}
