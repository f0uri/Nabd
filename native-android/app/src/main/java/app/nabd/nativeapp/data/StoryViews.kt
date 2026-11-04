package app.nabd.nativeapp.data

import android.content.Context

/** Which stories this device already watched. It is the device's own view state,
 * never a server-side number, and it only greys out the ring for this user. */
class StoryViews(context: Context, namespace: String) {
    private val prefs = context.getSharedPreferences("nabd-stories-$namespace", Context.MODE_PRIVATE)
    fun read(): Set<String> = (prefs.getStringSet("seen", emptySet()) ?: emptySet()).filter(::uid).toSet()
    fun add(id: String) { if (uid(id)) prefs.edit().putStringSet("seen", HashSet(read() + id)).apply() }
}
