package app.nabd.nativeapp.data

import android.content.Context

/** Per-device record of announcements the user closed. The announcement itself
 * stays in the shared vault; this only hides it again on this device. */
class DismissedAnnouncements(context: Context, namespace: String) {
    private val prefs = context.getSharedPreferences("nabd-announcements-$namespace", Context.MODE_PRIVATE)
    fun read(): Set<String> = (prefs.getStringSet("dismissed", emptySet()) ?: emptySet()).filter(::uid).toSet()
    fun add(id: String) { if (uid(id)) prefs.edit().putStringSet("dismissed", HashSet(read() + id)).apply() }
}
