package org.jellyfin.androidtv.aeon

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.androidtv.aeon.ArchiveItem
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * One playable file inside an "Originally from AeonTV" archive item:
 * either an .mp4 video or a .sphere package/script.
 */
data class OriginalFile(
    val name: String,
    val sizeBytes: Long,
    val isSphere: Boolean,
) {
    fun streamUrl(identifier: String): String =
        "https://archive.org/download/$identifier/${Uri.encode(name, "/")}"

    val sizeLabel: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> String.format("%.1f GB", sizeBytes / 1e9)
            sizeBytes >= 1_000_000 -> String.format("%.1f MB", sizeBytes / 1e6)
            sizeBytes >= 1_000 -> String.format("%.0f KB", sizeBytes / 1e3)
            else -> "$sizeBytes B"
        }
}

data class OriginalEntry(
    val item: ArchiveItem,
    val files: List<OriginalFile>,
    /** Official poster (TMDB, Jellyfin-style) when matched; else the archive.org thumbnail. */
    val posterUrl: String = item.thumbnailUrl,
)

/**
 * "Originally from AeonTV": content uploaded to our own archive.org
 * account. Only items that actually contain an .mp4 or a .sphere file
 * are shown — ISOs, text files and everything else stay hidden.
 */
object OriginalsApi {
    // The account's uploads (the uploader field on archive.org is the account email)
    private const val UPLOADER_QUERY = "uploader:\"rockcrusherg122209xpfusion@gmail.com\""

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchOriginals(context: Context): List<OriginalEntry> {
        val uploads = fetchUploads()
        if (uploads.isEmpty()) return emptyList()
        val appContext = context.applicationContext
        return coroutineScope {
            uploads.map { item ->
                async(Dispatchers.IO) {
                    val files = loadPlayableFiles(item.identifier)
                    if (files.isEmpty()) {
                        null
                    } else {
                        val poster = TmdbPosters.posterUrl(
                            appContext,
                            item.identifier,
                            item.title,
                            item.year?.toIntOrNull(),
                        )
                        OriginalEntry(item, files, poster ?: item.thumbnailUrl)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun fetchUploads(): List<ArchiveItem> = withContext(Dispatchers.IO) {
        val url = StringBuilder("https://archive.org/advancedsearch.php?q=")
            .append(URLEncoder.encode(UPLOADER_QUERY, "UTF-8"))
            .append("&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year")
            .append("&fl%5B%5D=creator&fl%5B%5D=description&fl%5B%5D=downloads")
            .append("&sort%5B%5D=date+desc")
            .append("&rows=50&page=1&output=json")
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
                    creator = null,
                    description = doc.optString("description").ifBlank { null },
                    downloads = doc.optLong("downloads"),
                ),
            )
        }
        return@withContext items
    }

    /** Files of [identifier] that AeonTV can play as originals: .mp4 or .sphere only. */
    suspend fun loadPlayableFiles(identifier: String): List<OriginalFile> = withContext(Dispatchers.IO) {
        val body = get("https://archive.org/metadata/$identifier") ?: return@withContext emptyList()
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext emptyList()
        val mp4s = mutableListOf<Pair<OriginalFile, String>>()
        val spheres = mutableListOf<OriginalFile>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            val format = file.optString("format")
            if (format.contains("Thumbnail", ignoreCase = true)) continue
            if (format.contains("Metadata", ignoreCase = true)) continue
            val lower = name.lowercase()
            val size = file.optString("size").toLongOrNull() ?: 0L
            when {
                lower.endsWith(".mp4") -> mp4s += OriginalFile(name, size, isSphere = false) to format
                lower.endsWith(".sphere") -> spheres += OriginalFile(name, size, isSphere = true)
            }
        }
        val sortedMp4s = mp4s.sortedWith(
            compareByDescending<Pair<OriginalFile, String>> { it.first.name.endsWith("_512kb.mp4", ignoreCase = true) }
                .thenByDescending { it.second.contains("h.264", ignoreCase = true) }
                .thenBy { it.first.sizeBytes },
        ).map { it.first }
        return@withContext sortedMp4s + spheres.sortedBy { it.name }
    }

    /** Download a .sphere file from an item into the cache so the Sphere player can open it. */
    suspend fun downloadSphere(context: Context, identifier: String, fileName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "originals").apply { mkdirs() }
            val target = File(dir, fileName)
            if (target.exists() && target.length() > 0) return@withContext target
            val url = "https://archive.org/download/$identifier/${Uri.encode(fileName, "/")}"
            val request = Request.Builder().url(url).header("User-Agent", "AeonTV").build()
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
}
