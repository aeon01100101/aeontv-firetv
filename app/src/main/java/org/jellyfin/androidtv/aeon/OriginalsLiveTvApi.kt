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
import org.jellyfin.androidtv.aeon.GuideProgram
import org.jellyfin.androidtv.aeon.LiveChannel
import org.jellyfin.androidtv.aeon.LiveTvApi
import org.jellyfin.androidtv.aeon.LiveTvData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * One playlist found on our archive: an .m3u (or .m3u8) file inside one of
 * our own archive.org items, plus the XMLTV guide (.xml) sitting in the
 * same item, when there is one.
 */
data class OriginalsPlaylistSource(
    val identifier: String,
    val itemTitle: String,
    val playlistFile: String,
    val guideFile: String?,
) {
    val playlistUrl: String
        get() = "https://archive.org/download/$identifier/${Uri.encode(playlistFile, "/")}"
    val guideUrl: String?
        get() = guideFile?.let { "https://archive.org/download/$identifier/${Uri.encode(it, "/")}" }
}

/**
 * Originals Live TV, fully automatic: every time it opens, the app scans
 * our own archive.org uploads for playlist files (.m3u / .m3u8). Any
 * playlist found — in any item, old or newly uploaded — is downloaded,
 * parsed, and its channels merged into the lineup on their own. No app
 * update, no manual step: uploading a new playlist item to the archive is
 * all it takes. Guides (.xml next to the playlist, XMLTV format) are
 * picked up the same way. Everything is cached, so the last known lineup
 * still opens offline.
 */
object OriginalsLiveTvApi {
    private const val UPLOADER_QUERY = "uploader:\"rockcrusherg122209xpfusion@gmail.com\""

    // This item already feeds the Other media Live TV (Isaiah's placement:
    // the broadcaster channels aren't Originals), so auto-detection skips
    // it here to keep the two doors from duplicating each other.
    private const val EXCLUDED_IDENTIFIER = "aeontv-livetv"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class SourceResult(
        val source: OriginalsPlaylistSource,
        val channels: List<LiveChannel>,
        val programs: Map<String, List<GuideProgram>>,
        val fromCache: Boolean,
    )

    suspend fun load(context: Context): LiveTvData = withContext(Dispatchers.IO) {
        val cacheRoot = File(context.cacheDir, "originals_livetv").apply { mkdirs() }
        val sourcesFile = File(cacheRoot, "sources.json")

        var fromCache = false
        val sources: List<OriginalsPlaylistSource> = try {
            discoverSources().also { saveSources(sourcesFile, it) }
        } catch (e: Exception) {
            fromCache = true
            loadSources(sourcesFile) ?: throw e
        }
        if (sources.isEmpty()) {
            return@withContext LiveTvData(emptyList(), emptyMap(), fromCache)
        }

        val results = coroutineScope {
            sources.map { source -> async { loadSource(cacheRoot, source) } }.awaitAll()
        }

        var usedCache = fromCache
        val seenUrls = mutableSetOf<String>()
        val channels = mutableListOf<LiveChannel>()
        val programs = mutableMapOf<String, List<GuideProgram>>()
        for (result in results) {
            if (result.fromCache) usedCache = true
            // Namespace channel ids per source item so guides from
            // different playlists can't collide.
            val ns = result.source.identifier + ":"
            for (channel in result.channels) {
                if (seenUrls.add(channel.url)) {
                    channels += channel.copy(id = ns + channel.id)
                }
            }
            for ((channelId, list) in result.programs) {
                programs[ns + channelId] = list
            }
        }
        return@withContext LiveTvData(channels, programs, usedCache)
    }

    /** All playlist sources currently on the account, newest items first. */
    private suspend fun discoverSources(): List<OriginalsPlaylistSource> = withContext(Dispatchers.IO) {
        val url = StringBuilder("https://archive.org/advancedsearch.php?q=")
            .append(URLEncoder.encode(UPLOADER_QUERY, "UTF-8"))
            .append("&fl%5B%5D=identifier&fl%5B%5D=title")
            .append("&sort%5B%5D=date+desc")
            .append("&rows=50&page=1&output=json")
            .toString()
        val body = get(url) ?: throw IllegalStateException("Couldn't reach archive.org")
        val docs = JSONObject(body)
            .optJSONObject("response")
            ?.optJSONArray("docs")
            ?: return@withContext emptyList()

        val items = mutableListOf<Pair<String, String>>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = doc.optString("identifier")
            if (identifier.isBlank() || identifier == EXCLUDED_IDENTIFIER) continue
            items += identifier to doc.optString("title").ifBlank { identifier }
        }

        val perItem = coroutineScope {
            items.map { (identifier, title) ->
                async(Dispatchers.IO) { scanItem(identifier, title) }
            }.awaitAll()
        }
        return@withContext perItem.flatten()
    }

    /** Playlists (+ guide) inside one archive item, if it has any. */
    private fun scanItem(identifier: String, title: String): List<OriginalsPlaylistSource> {
        val body = get("https://archive.org/metadata/$identifier") ?: return emptyList()
        val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
        val playlists = mutableListOf<String>()
        val guides = mutableListOf<String>()
        for (i in 0 until files.length()) {
            val name = files.optJSONObject(i)?.optString("name") ?: continue
            val lower = name.lowercase()
            when {
                lower.endsWith(".m3u") || lower.endsWith(".m3u8") -> playlists += name
                lower.endsWith(".xml") &&
                    !lower.endsWith("_meta.xml") &&
                    !lower.endsWith("_files.xml") -> guides += name
            }
        }
        if (playlists.isEmpty()) return emptyList()
        playlists.sortWith(
            compareByDescending<String> { it.contains("livetv", ignoreCase = true) }
                .thenBy { it.lowercase() },
        )
        val guide = guides.minWithOrNull(
            compareByDescending<String> {
                it.contains("guide", ignoreCase = true) || it.contains("epg", ignoreCase = true)
            }.thenBy { it.lowercase() },
        )
        return playlists.map { OriginalsPlaylistSource(identifier, title, it, guide) }
    }

    private fun loadSource(cacheRoot: File, source: OriginalsPlaylistSource): SourceResult {
        val dir = File(cacheRoot, source.identifier).apply { mkdirs() }
        val playlistCache = File(dir, "playlist.m3u")
        val guideCache = File(dir, "guide.xml")
        var fromCache = false

        val m3uText: String = try {
            fetch(source.playlistUrl).also { playlistCache.writeText(it) }
        } catch (e: Exception) {
            if (playlistCache.exists()) {
                fromCache = true
                playlistCache.readText()
            } else {
                return SourceResult(source, emptyList(), emptyMap(), fromCache = false)
            }
        }

        var programs: Map<String, List<GuideProgram>> = emptyMap()
        val guideUrl = source.guideUrl
        if (guideUrl != null) {
            val guideText: String? = try {
                fetch(guideUrl).also { guideCache.writeText(it) }
            } catch (e: Exception) {
                if (guideCache.exists()) {
                    fromCache = true
                    guideCache.readText()
                } else {
                    null
                }
            }
            if (guideText != null) {
                programs = try {
                    LiveTvApi.parseGuide(guideText)
                } catch (e: Exception) {
                    emptyMap()
                }
            }
        }

        val channels = try {
            LiveTvApi.parseM3u(m3uText)
        } catch (e: Exception) {
            emptyList()
        }
        return SourceResult(source, channels, programs, fromCache)
    }

    private fun saveSources(file: File, sources: List<OriginalsPlaylistSource>) {
        try {
            val array = JSONArray()
            for (source in sources) {
                array.put(
                    JSONObject()
                        .put("identifier", source.identifier)
                        .put("title", source.itemTitle)
                        .put("playlist", source.playlistFile)
                        .put("guide", source.guideFile ?: JSONObject.NULL),
                )
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            // Best-effort: losing the offline source list just means the
            // next open needs the network again.
        }
    }

    private fun loadSources(file: File): List<OriginalsPlaylistSource>? {
        if (!file.exists()) return null
        return try {
            val array = JSONArray(file.readText())
            val sources = mutableListOf<OriginalsPlaylistSource>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                sources += OriginalsPlaylistSource(
                    identifier = obj.optString("identifier"),
                    itemTitle = obj.optString("title"),
                    playlistFile = obj.optString("playlist"),
                    guideFile = obj.optString("guide").takeIf { it.isNotBlank() },
                )
            }
            sources
        } catch (e: Exception) {
            null
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AeonTV")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            return response.body?.string() ?: throw IllegalStateException("Empty response for $url")
        }
    }

    private fun get(url: String): String? = try {
        fetch(url)
    } catch (e: Exception) {
        null
    }
}
