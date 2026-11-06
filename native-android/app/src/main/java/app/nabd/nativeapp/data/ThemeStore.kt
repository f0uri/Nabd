package app.nabd.nativeapp.data

import android.content.Context

/**
 * A local mirror of the appearance choice. The real preference lives with the
 * account on the server; this only paints the sign-in screen correctly before
 * that account is loaded.
 */
class ThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("nabd.native.theme", Context.MODE_PRIVATE)
    var value: String?
        get() = preferences.getString("theme", null)
        set(value) { preferences.edit().putString("theme", value).commit() }
}
