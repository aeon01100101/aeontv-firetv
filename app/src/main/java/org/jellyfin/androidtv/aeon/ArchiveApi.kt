package org.jellyfin.androidtv.aeon

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * A single movie/TV item on archive.org.
 */
data class ArchiveItem(
    val identifier: String,
    val title: String,
    val year: String?,
    val creator: String?,
    val description: String?,
    val downloads: Long,
) {
    val thumbnailUrl: String get() = "https://archive.org/services/img/$identifier"
}

/**
 * A playable video file belonging to an [ArchiveItem].
 */
data class ArchiveFile(
    val name: String,
    val format: String,
    val sizeBytes: Long,
    val lengthSeconds: Double?,
) {
    fun streamUrl(identifier: String): String =
        "https://archive.org/download/$identifier/${Uri.encode(name, "/")}"

    /** Sphere packages are supported silently: they list and play like any other video file. */
    val isSphere: Boolean
        get() = name.endsWith(".sphere", ignoreCase = true)

    val sizeLabel: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> String.format("%.1f GB", sizeBytes / 1e9)
            sizeBytes > 0 -> "${sizeBytes / 1_000_000} MB"
            else -> ""
        }

    val lengthLabel: String
        get() {
            val total = lengthSeconds?.toLong() ?: return ""
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

/**
 * Minimal client for the public archive.org APIs (no key or account required):
 * - Advanced Search: https://archive.org/advancedsearch.php
 * - Metadata: https://archive.org/metadata/<identifier>
 * - Thumbnails: https://archive.org/services/img/<identifier>
 * - Streaming: https://archive.org/download/<identifier>/<file> (redirects to the item's host)
 */
object ArchiveApi {
    const val PAGE_SIZE = 40

    val CATEGORIES: List<Pair<String, String>> = listOf(
        "Featured" to "collection:feature_films AND mediatype:movies",
        "Classic TV" to "collection:classic_tv AND mediatype:movies",
        "Cartoons" to "collection:animationandcartoons AND mediatype:movies",
        "Comedy" to "subject:comedy AND mediatype:movies",
        "Horror" to "subject:horror AND mediatype:movies",
        "Sci-Fi" to "subject:\"science fiction\" AND mediatype:movies",
        "Film Noir" to "subject:\"film noir\" AND mediatype:movies",
        "Silent Era" to "collection:silent_films AND mediatype:movies",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun browse(categoryQuery: String, page: Int): List<ArchiveItem> =
        fetchSearch(categoryQuery, page)

    suspend fun search(text: String, page: Int): List<ArchiveItem> =
        fetchSearch("($text) AND mediatype:movies", page)

    private suspend fun fetchSearch(rawQuery: String, page: Int): List<ArchiveItem> {
        return withContext(Dispatchers.IO) {
            val url = StringBuilder("https://archive.org/advancedsearch.php?q=")
                .append(URLEncoder.encode(rawQuery, "UTF-8"))
                .append("&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year")
                .append("&fl%5B%5D=creator&fl%5B%5D=description&fl%5B%5D=downloads")
                .append("&sort%5B%5D=downloads+desc")
                .append("&rows=$PAGE_SIZE&page=$page&output=json")
                .toString()
            val body = get(url) ?: return@withContext emptyList()
            val docs = JSONObject(body)
                .optJSONObject("response")
                ?.optJSONArray("docs")
                ?: return@withContext emptyList()
            val items = mutableListOf<ArchiveItem>()
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val identifier = doc.optString("identifier")
                if (identifier.isBlank()) continue
                items.add(
                    ArchiveItem(
                        identifier = identifier,
                        title = doc.optString("title").ifBlank { identifier },
                        year = doc.opt("year")?.toString()?.takeIf { it != "null" && it.isNotBlank() },
                        creator = firstTextValue(doc.opt("creator")),
                        description = firstTextValue(doc.opt("description")),
                        downloads = doc.optLong("downloads"),
                    ),
                )
            }
            items
        }
    }

    /**
     * Load the playable video files for an item, best streaming choice first:
     * the 512kb derivative (fast start, low bandwidth), then h.264 MP4s, then
     * the rest. Sphere files inside the item are included as ordinary videos.
     */
    suspend fun loadFiles(identifier: String): List<ArchiveFile> = withContext(Dispatchers.IO) {
        val body = get("https://archive.org/metadata/$identifier") ?: return@withContext emptyList()
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext emptyList()
        val videos = mutableListOf<ArchiveFile>()
        val spheres = mutableListOf<ArchiveFile>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            val format = file.optString("format")
            val lowerName = name.lowercase()
            if (format.contains("Thumbnail", ignoreCase = true)) continue
            if (format.contains("Metadata", ignoreCase = true)) continue
            when {
                lowerName.endsWith(".sphere") -> spheres += ArchiveFile(
                    name = name,
                    format = "Video",
                    sizeBytes = file.optString("size").toLongOrNull() ?: 0L,
                    lengthSeconds = null,
                )
                lowerName.endsWith(".mp4") || lowerName.endsWith(".ogv") || lowerName.endsWith(".mkv") ->
                    videos += ArchiveFile(
                        name = name,
                        format = format,
                        sizeBytes = file.optString("size").toLongOrNull() ?: 0L,
                        lengthSeconds = file.optString("length").toDoubleOrNull(),
                    )
            }
        }
        val sortedVideos = videos.sortedWith(
            compareByDescending<ArchiveFile> { it.name.endsWith("_512kb.mp4", ignoreCase = true) }
                .thenByDescending { it.format.contains("h.264", ignoreCase = true) }
                .thenBy { it.sizeBytes },
        )
        sortedVideos + spheres.sortedBy { it.name }
    }

    /** Download one file of an item into the app cache (used for sphere files, which open locally). */
    suspend fun downloadToCache(context: android.content.Context, identifier: String, fileName: String): java.io.File =
        withContext(Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "archive_files").apply { mkdirs() }
            val target = java.io.File(dir, identifier + "_" + fileName)
            if (target.exists() && target.length() > 0) return@withContext target
            val request = okhttp3.Request.Builder()
                .url("https://archive.org/download/$identifier/${Uri.encode(fileName, "/")}")
                .header("User-Agent", "AeonTV")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty file")
                target.writeBytes(bytes)
            }
            return@withContext target
        }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AeonTV")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun firstTextValue(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }
        is org.json.JSONArray -> if (value.length() > 0) value.optString(0).takeIf { it.isNotBlank() } else null
        else -> null
    }
}
