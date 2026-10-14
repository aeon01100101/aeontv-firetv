package org.jellyfin.androidtv.aeon

import android.content.Context

/**
 * Stream-quality preference ("110p ultra-low") for the AeonTV TV screens.
 *
 * The setting lives on the ACTIVE PROFILE when one exists; otherwise it
 * falls back to the legacy global preference (so installs that predate
 * profiles keep their setting). The first profile created inherits the
 * global value at creation time.
 */
object StreamPrefs {
    private const val FILE = "aeon_stream"
    private const val KEY_ULTRA_LOW = "ultra_low_110p"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The global (pre-profiles) value. */
    fun globalIsUltraLow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ULTRA_LOW, false)

    fun isUltraLow(context: Context): Boolean =
        Profiles.active(context)?.ultraLow110p ?: globalIsUltraLow(context)

    fun setUltraLow(context: Context, enabled: Boolean) {
        val profile = Profiles.active(context)
        if (profile != null) {
            Profiles.updateProfile(context, profile.id) { it.copy(ultraLow110p = enabled) }
        } else {
            prefs(context).edit().putBoolean(KEY_ULTRA_LOW, enabled).apply()
        }
    }
}
