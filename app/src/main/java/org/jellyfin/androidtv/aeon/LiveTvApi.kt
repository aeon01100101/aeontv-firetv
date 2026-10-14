package org.jellyfin.androidtv.aeon

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * One live channel from our own livetv.m3u (hosted on archive.org).
 */
data class LiveChannel(
    val id: String,
    val name: String,
    val group: String,
    val url: String,
)

/**
 * One programme from our guide.xml (XMLTV).
 */
data class GuideProgram(
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?,
)

data class LiveTvData(
    val channels: List<LiveChannel>,
    val programs: Map<String, List<GuideProgram>>,
    val fromCache: Boolean,
) {
    fun currentProgram(channelId: String, nowMs: Long = System.currentTimeMillis()): GuideProgram? =
        programs[channelId]?.firstOrNull { nowMs >= it.startMs && nowMs < it.stopMs }

    fun nextProgram(channelId: String, nowMs: Long = System.currentTimeMillis()): GuideProgram? =
        programs[channelId]?.firstOrNull { it.startMs > nowMs }
}

/**
 * Live TV data source: every time Live TV opens, the app downloads our own
 * livetv.m3u playlist and guide.xml from archive.org, so updating the
 * channel lineup is just replacing those two files — no app update needed.
 * If the download fails, the last saved copy is used instead.
 */
object LiveTvApi {
    const val PLAYLIST_URL = "https://archive.org/download/aeontv-livetv/livetv.m3u"
    const val GUIDE_URL = "https://archive.org/download/aeontv-livetv/guide.xml"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun load(context: Context): LiveTvData = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "livetv").apply { mkdirs() }
        val m3uFile = File(cacheDir, "livetv.m3u")
        val guideFile = File(cacheDir, "guide.xml")

        var fromCache = false
        var m3uText = ""
        var guideText: String? = null
        try {
            m3uText = fetch(PLAYLIST_URL)
            m3uFile.writeText(m3uText)
            guideText = try {
                fetch(GUIDE_URL).also { guideFile.writeText(it) }
            } catch (e: Exception) {
                if (guideFile.exists()) guideFile.readText() else null
            }
        } catch (e: Exception) {
            if (!m3uFile.exists()) throw e
            fromCache = true
            m3uText = m3uFile.readText()
            guideText = if (guideFile.exists()) guideFile.readText() else null
        }

        val channels = parseM3u(m3uText)
        if (channels.isEmpty()) {
            throw IllegalStateException("The channel list is empty")
        }
        val programs = if (guideText != null) parseGuide(guideText) else emptyMap()
        return@withContext LiveTvData(channels, programs, fromCache)
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

    fun parseM3u(text: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingName: String? = null
        val attrRegex = Regex("""([a-zA-Z0-9-]+)="([^"]*)"""")
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pendingAttrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    pendingName = line.substringAfterLast(",").trim()
                }
                line.startsWith("http://") || line.startsWith("https://") -> {
                    val name = pendingName
                    if (!name.isNullOrBlank()) {
                        channels += LiveChannel(
                            id = pendingAttrs["tvg-id"] ?: name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-"),
                            name = name,
                            group = pendingAttrs["group-title"] ?: "Channels",
                            url = line,
                        )
                    }
                    pendingName = null
                    pendingAttrs = emptyMap()
                }
            }
        }
        return channels
    }

    fun parseGuide(xml: String): Map<String, List<GuideProgram>> {
        val programs = mutableListOf<GuideProgram>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var channelId: String? = null
        var startMs = 0L
        var stopMs = 0L
        var title: String? = null
        var desc: String? = null
        var inProgramme = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel")
                        startMs = parseXmltvTime(parser.getAttributeValue(null, "start") ?: "")
                        stopMs = parseXmltvTime(parser.getAttributeValue(null, "stop") ?: "")
                        title = null
                        desc = null
                    }
                    "title" -> if (inProgramme) title = parser.nextText()
                    "desc" -> if (inProgramme) desc = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && inProgramme) {
                    val cid = channelId
                    val t = title
                    if (cid != null && t != null && stopMs > startMs) {
                        programs += GuideProgram(cid, startMs, stopMs, t, desc)
                    }
                    inProgramme = false
                }
            }
            event = parser.next()
        }
        return programs.groupBy { it.channelId }
            .mapValues { (_, list) -> list.sortedBy { it.startMs } }
    }

    private fun parseXmltvTime(raw: String): Long {
        val cleaned = raw.trim()
        if (cleaned.length < 14) return 0L
        val normalized = if (cleaned.length == 14) {
            "$cleaned +0000"
        } else {
            cleaned.substring(0, 14) + " " + cleaned.substring(14).trim()
        }
        return try {
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(normalized)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
