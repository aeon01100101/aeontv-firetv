package org.jellyfin.androidtv.aeon

import android.content.Context

/**
 * Persisted stream-quality preference for the AeonTV TV screens.
 *
 * "110p ultra-low" is Isaiah's automatic low-bandwidth mode for slow
 * connections: the player caps streams at a 110p ceiling (see
 * TvPlayerActivity) and the file pickers prefer the smallest playable
 * derivative (see ArchiveApi.loadFiles / OriginalsApi.loadPlayableFiles).
 * Off = full quality, today's behavior exactly.
 */
object StreamPrefs {
    private const val FILE = "aeon_stream"
    private const val KEY_ULTRA_LOW = "ultra_low_110p"

    fun isUltraLow(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ULTRA_LOW, false)

    fun setUltraLow(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ULTRA_LOW, enabled)
            .apply()
    }
}
