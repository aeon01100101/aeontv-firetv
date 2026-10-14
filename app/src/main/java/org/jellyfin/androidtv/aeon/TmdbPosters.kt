package org.jellyfin.androidtv.aeon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Official poster art the way Jellyfin gets it: from TMDB (The Movie
 * Database) — the same provider the AeonTV/Jellyfin server's TMDB plugin
 * uses, including its built-in project key fallback, so posters work with
 * zero setup. Matches are scored like Jellyfin does: title (leading
 * articles ignored) plus release-year closeness; anything not confidently
 * matched keeps the archive.org thumbnail. Resolved posters are remembered
 * per item in filesDir/poster_map.json and the images themselves live in
 * the shared Coil disk cache (see OriginalThumbnails).
 */
object TmdbPosters {
    // The built-in project key the Jellyfin TMDB plugin ships with
    // (MediaBrowser.Providers TmdbUtils.ApiKey) — a public constant in the
    // open-source code, used the same way here.
    private const val API_KEY = "4219e299c89411838049ab0dab19ebd5"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class Candidate(
        val normalizedTitle: String,
        val year: Int?,
        val mediaType: String,
        val posterPath: String,
    )

    suspend fun posterUrl(context: Context, identifier: String, title: String, year: Int?): String? =
        withContext(Dispatchers.IO) {
            val map = loadMap(context)
            map.optString(identifier).takeIf { it.isNotBlank() }?.let { return@withContext it }
            val found = search(title, year) ?: return@withContext null
            map.put(identifier, found)
            saveMap(context, map)
            found
        }

    private fun search(title: String, year: Int?): String? {
        val query = URLEncoder.encode(title.replace(Regex("\\s+"), " ").trim(), "UTF-8")
        val url = "https://api.themoviedb.org/3/search/multi?api_key=$API_KEY&query=$query&include_adult=false"
        val body = try {
            val request = Request.Builder().url(url).header("User-Agent", "AeonTV").build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val results = JSONObject(body).optJSONArray("results") ?: return null
        val target = normalize(title)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until results.length()) {
            val hit = results.optJSONObject(i) ?: continue
            val posterPath = hit.optString("poster_path").takeIf { it.isNotBlank() } ?: continue
            val mediaType = hit.optString("media_type")
            if (mediaType != "movie" && mediaType != "tv") continue
            val name = hit.optString("title").ifBlank { hit.optString("name") }
            if (name.isBlank()) continue
            val date = hit.optString("release_date").ifBlank { hit.optString("first_air_date") }
            candidates += Candidate(
                normalizedTitle = normalize(name),
                year = date.take(4).toIntOrNull(),
                mediaType = mediaType,
                posterPath = posterPath,
            )
        }

        fun titleScore(c: Candidate): Int = when {
            c.normalizedTitle == target -> 0
            target.startsWith(c.normalizedTitle) || c.normalizedTitle.startsWith(target) -> 1
            else -> 3
        }

        fun confident(c: Candidate): Boolean {
            val ts = titleScore(c)
            val delta = if (year != null && c.year != null) abs(year - c.year) else null
            return when {
                ts == 0 && (delta == null || delta <= 3) -> true
                ts == 1 && c.mediaType == "tv" -> true // episode titles extend the series name
                ts == 1 && delta != null && delta <= 3 -> true
                else -> false
            }
        }

        val best = candidates
            .filter { confident(it) }
            .minByOrNull { c ->
                val delta = if (year != null && c.year != null) abs(year - c.year) else 50
                titleScore(c) * 1000 + delta
            }
            ?: return null
        return IMAGE_BASE + best.posterPath
    }

    private fun normalize(title: String): String {
        var t = title.lowercase().trim()
        t = t.replace(Regex("^(the|a|an)\\s+"), "")
        t = t.replace(Regex("[^a-z0-9 ]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun mapFile(context: Context): File = File(context.filesDir, "poster_map.json")

    private fun loadMap(context: Context): JSONObject {
        val file = mapFile(context)
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun saveMap(context: Context, map: JSONObject) {
        try {
            mapFile(context).writeText(map.toString())
        } catch (e: Exception) {
            // Cache is best-effort; a failed write just means a re-search next launch.
        }
    }
}
