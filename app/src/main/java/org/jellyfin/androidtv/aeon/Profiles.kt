package org.jellyfin.androidtv.aeon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * One entry in a profile's Continue Watching list.
 */
data class ContinueEntry(
    val key: String,
    val kind: String,
    val identifier: String,
    val fileKey: String,
    val title: String,
    val posterUrl: String?,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/**
 * One entry in a profile's favorites.
 */
data class FavoriteEntry(
    val kind: String,
    val identifier: String,
    val title: String,
    val posterUrl: String?,
)

/**
 * One local TV profile. Everything lives on the device in
 * filesDir/profiles.json — no server, no account anywhere else.
 *
 * PINs are never stored in plaintext: only a salted SHA-256 hash is kept.
 * There is no master PIN and no backdoor: a forgotten PIN can only be reset
 * by the owner from Manage profiles (Isaiah's rule).
 */
data class Profile(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val pinSalt: String?,
    val pinHash: String?,
    val isOwner: Boolean,
    val createdAt: Long,
    val ultraLow110p: Boolean,
    val favorites: List<FavoriteEntry>,
    val continueWatching: List<ContinueEntry>,
)

/**
 * Process-lifetime session state. Resets if the app process dies, which is
 * the safe direction: owner privileges never survive a restart.
 */
object SessionState {
    var ownerAuthed: Boolean = false
}

/**
 * Local profile storage and PIN handling.
 */
object Profiles {
    private const val PREFS = "aeon_profiles"
    private const val KEY_ACTIVE = "active_profile_id"
    private const val CONTINUE_MAX = 20

    private fun file(context: Context): File = File(context.filesDir, "profiles.json")

    private fun activePrefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activeId(context: Context): String? =
        activePrefs(context).getString(KEY_ACTIVE, null)

    fun setActiveId(context: Context, id: String) {
        activePrefs(context).edit().putString(KEY_ACTIVE, id).apply()
    }

    fun clearActiveId(context: Context) {
        activePrefs(context).edit().remove(KEY_ACTIVE).apply()
    }

    fun active(context: Context): Profile? {
        val id = activeId(context) ?: return null
        return load(context).firstOrNull { it.id == id }
    }

    fun hasProfiles(context: Context): Boolean = load(context).isNotEmpty()

    fun owner(context: Context): Profile? = load(context).firstOrNull { it.isOwner }

    /** Load all profiles; a missing or corrupt file means "no profiles" (first-run). */
    fun load(context: Context): List<Profile> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("profiles") ?: return emptyList()
            val out = mutableListOf<Profile>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                out += Profile(
                    id = id,
                    name = o.optString("name").ifBlank { "Profile" },
                    colorIndex = o.optInt("colorIndex", 0),
                    pinSalt = o.optString("pinSalt").takeIf { it.isNotBlank() },
                    pinHash = o.optString("pinHash").takeIf { it.isNotBlank() },
                    isOwner = o.optBoolean("isOwner", false),
                    createdAt = o.optLong("createdAt", 0L),
                    ultraLow110p = o.optBoolean("ultraLow110p", false),
                    favorites = parseFavorites(o.optJSONArray("favorites")),
                    continueWatching = parseContinue(o.optJSONArray("continueWatching")),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, profiles: List<Profile>) {
        val root = JSONObject()
        val arr = JSONArray()
        for (p in profiles) {
            val o = JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("colorIndex", p.colorIndex)
                .put("isOwner", p.isOwner)
                .put("createdAt", p.createdAt)
                .put("ultraLow110p", p.ultraLow110p)
            if (p.pinSalt != null && p.pinHash != null) {
                o.put("pinSalt", p.pinSalt).put("pinHash", p.pinHash)
            }
            val favs = JSONArray()
            for (fv in p.favorites) {
                favs.put(
                    JSONObject()
                        .put("kind", fv.kind)
                        .put("identifier", fv.identifier)
                        .put("title", fv.title)
                        .put("posterUrl", fv.posterUrl ?: JSONObject.NULL),
                )
            }
            o.put("favorites", favs)
            val cont = JSONArray()
            for (c in p.continueWatching) {
                cont.put(
                    JSONObject()
                        .put("key", c.key)
                        .put("kind", c.kind)
                        .put("identifier", c.identifier)
                        .put("fileKey", c.fileKey)
                        .put("title", c.title)
                        .put("posterUrl", c.posterUrl ?: JSONObject.NULL)
                        .put("streamUrl", c.streamUrl)
                        .put("positionMs", c.positionMs)
                        .put("durationMs", c.durationMs)
                        .put("updatedAt", c.updatedAt),
                )
            }
            o.put("continueWatching", cont)
            arr.put(o)
        }
        root.put("profiles", arr)
        try {
            file(context).writeText(root.toString())
        } catch (e: Exception) {
            // Best-effort: a failed write is just lost this time.
        }
    }

    private fun parseFavorites(arr: JSONArray?): List<FavoriteEntry> {
        if (arr == null) return emptyList()
        val out = mutableListOf<FavoriteEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val identifier = o.optString("identifier")
            if (identifier.isBlank()) continue
            out += FavoriteEntry(
                kind = o.optString("kind", "archive"),
                identifier = identifier,
                title = o.optString("title").ifBlank { identifier },
                posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    private fun parseContinue(arr: JSONArray?): List<ContinueEntry> {
        if (arr == null) return emptyList()
        val out = mutableListOf<ContinueEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("key")
            if (key.isBlank()) continue
            out += ContinueEntry(
                key = key,
                kind = o.optString("kind", "archive"),
                identifier = o.optString("identifier"),
                fileKey = o.optString("fileKey"),
                title = o.optString("title").ifBlank { o.optString("identifier") },
                posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() },
                streamUrl = o.optString("streamUrl"),
                positionMs = o.optLong("positionMs", 0L),
                durationMs = o.optLong("durationMs", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
        return out
    }

    // region PINs

    /** PINs are 4-6 digits. */
    fun isValidPin(pin: String): Boolean =
        pin.length in 4..6 && pin.all { it.isDigit() }

    private fun hashPin(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(profile: Profile, pin: String): Boolean {
        val salt = profile.pinSalt ?: return false
        val hash = profile.pinHash ?: return false
        return hashPin(salt, pin) == hash
    }

    // endregion

    // region CRUD

    fun addProfile(
        context: Context,
        name: String,
        colorIndex: Int,
        pin: String?,
        isOwner: Boolean,
        ultraLow110p: Boolean,
    ): Profile {
        val salt = if (pin != null) UUID.randomUUID().toString() else null
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            colorIndex = colorIndex,
            pinSalt = salt,
            pinHash = if (pin != null && salt != null) hashPin(salt, pin) else null,
            isOwner = isOwner,
            createdAt = System.currentTimeMillis(),
            ultraLow110p = ultraLow110p,
            favorites = emptyList(),
            continueWatching = emptyList(),
        )
        save(context, load(context) + profile)
        return profile
    }

    fun updateProfile(context: Context, id: String, transform: (Profile) -> Profile) {
        save(context, load(context).map { if (it.id == id) transform(it) else it })
    }

    fun setPin(context: Context, id: String, pin: String?) {
        updateProfile(context, id) { p ->
            if (pin == null) {
                p.copy(pinSalt = null, pinHash = null)
            } else {
                val salt = UUID.randomUUID().toString()
                p.copy(pinSalt = salt, pinHash = hashPin(salt, pin))
            }
        }
    }

    /** The owner profile can never be deleted. Returns false if refused. */
    fun deleteProfile(context: Context, id: String): Boolean {
        val profiles = load(context)
        val target = profiles.firstOrNull { it.id == id } ?: return false
        if (target.isOwner) return false
        save(context, profiles.filterNot { it.id == id })
        if (activeId(context) == id) clearActiveId(context)
        return true
    }

    // endregion

    // region per-profile state

    fun continueEntry(context: Context, profileId: String, key: String): ContinueEntry? =
        load(context).firstOrNull { it.id == profileId }
            ?.continueWatching?.firstOrNull { it.key == key }

    fun recordContinue(context: Context, profileId: String, entry: ContinueEntry) {
        updateProfile(context, profileId) { p ->
            val rest = p.continueWatching.filterNot { it.key == entry.key }
            p.copy(continueWatching = (listOf(entry) + rest).take(CONTINUE_MAX))
        }
    }

    fun removeContinue(context: Context, profileId: String, key: String) {
        updateProfile(context, profileId) { p ->
            p.copy(continueWatching = p.continueWatching.filterNot { it.key == key })
        }
    }

    fun isFavorite(profile: Profile, kind: String, identifier: String): Boolean =
        profile.favorites.any { it.kind == kind && it.identifier == identifier }

    fun toggleFavorite(context: Context, profileId: String, fav: FavoriteEntry): Boolean {
        var added = false
        updateProfile(context, profileId) { p ->
            if (p.favorites.any { it.kind == fav.kind && it.identifier == fav.identifier }) {
                p.copy(favorites = p.favorites.filterNot { it.kind == fav.kind && it.identifier == fav.identifier })
            } else {
                added = true
                p.copy(favorites = p.favorites + fav)
            }
        }
        return added
    }

    // endregion
}
