package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import coil3.request.ImageRequest
import coil3.request.target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.aeon.AeonUi.detailRoot
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.errorView
import org.jellyfin.androidtv.aeon.AeonUi.listRow
import org.jellyfin.androidtv.aeon.AeonUi.loadingView
import org.jellyfin.androidtv.aeon.AeonUi.menuCard
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.aeon.AeonUi.toast
import java.io.File
import java.util.zip.ZipFile

/**
 * Opens a .sphere file (Albish video format):
 * - Script form: an Albish manifest whose `video` is a stream URL.
 * - Package form: a ZIP with `sphere.alb` + the video inside; the video is
 *   extracted to the app cache and played locally.
 * Shows the manifest's title/poster/description/chapters, then plays.
 * (TV port of the phone SphereActivity; same resolution semantics.)
 */
class SphereActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceUri: Uri? = intent?.data
            ?: @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_URI)
        val root = screenRoot()
        root.addView(loadingView("Reading .sphere file…"))
        setContentView(root)

        scope.launch {
            val state = if (sourceUri == null) {
                SphereState.Error("No .sphere file was given.")
            } else {
                try {
                    resolveSphere(sourceUri)
                } catch (e: Exception) {
                    SphereState.Error(e.message ?: "Couldn't read this .sphere file.")
                }
            }
            root.removeAllViews()
            when (state) {
                is SphereState.Error -> {
                    root.addView(titleView(".sphere"))
                    root.addView(errorView("Not a playable .sphere", "Back") { finish() })
                }
                is SphereState.Ready -> root.addView(buildDetail(state))
            }
        }
    }

    private fun buildDetail(state: SphereState.Ready): View {
        val manifest = state.manifest
        fun play(startSeconds: Long = 0) {
            TvPlayerActivity.start(
                context = this,
                streamUrl = state.playTarget,
                title = manifest.title ?: "Sphere",
                startPositionMs = startSeconds * 1000,
            )
        }

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(48)
            setPadding(p, dp(40), p, dp(32))
        }
        column.addView(titleView(".sphere", 32))
        column.addView(titleView(manifest.title ?: "", 48))
        val meta = listOfNotNull(manifest.year, manifest.creator).joinToString(" · ")
        if (meta.isNotBlank()) column.addView(subtitleView(meta, 24))
        if (!manifest.description.isNullOrBlank()) {
            column.addView(subtitleView(manifest.description, 22))
        }
        column.addView(menuCard("▶  Play", manifest.title ?: "") { play() })
        if (manifest.chapters.isNotEmpty()) {
            column.addView(titleView("Chapters", 32))
            for (chapter in manifest.chapters) {
                column.addView(
                    listRow(chapter.name, formatTimestamp(chapter.atSeconds)) {
                        play(chapter.atSeconds)
                    },
                )
            }
        }
        scroll.addView(column)
        return with(this) {
            detailRoot(scroll, state.posterData, OriginalThumbnails.get(this@SphereActivity))
        }
    }

    private sealed interface SphereState {
        data class Error(val message: String) : SphereState
        data class Ready(
            val manifest: SphereManifest,
            val playTarget: String,
            val posterData: Any?,
        ) : SphereState
    }

    /**
     * Copy the source into the cache, sniff the form, parse the manifest and
     * work out the playable target (stream URL for scripts, extracted file
     * for packages). Same semantics as the phone resolver.
     */
    private suspend fun resolveSphere(uri: Uri): SphereState = withContext(Dispatchers.IO) {
        val cacheDir = File(cacheDir, "sphere").apply { mkdirs() }
        val localCopy = File(cacheDir, "opened-${System.currentTimeMillis()}.sphere")
        contentResolver.openInputStream(uri)?.use { input ->
            localCopy.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Couldn't open the file.")

        val isPackage = localCopy.inputStream().use { stream ->
            val magic = ByteArray(2)
            stream.read(magic) == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        }

        if (!isPackage) {
            val manifest = SphereManifestParser.parse(localCopy.readText(Charsets.UTF_8))
            if (!manifest.isPlayable) throw IllegalStateException("The manifest needs a literal title and video.")
            val video = manifest.video!!
            if (!video.startsWith("http://") && !video.startsWith("https://")) {
                throw IllegalStateException("A Sphere Script's video must be an http(s) stream URL.")
            }
            return@withContext SphereState.Ready(
                manifest = manifest,
                playTarget = video,
                posterData = manifest.poster,
            )
        }

        ZipFile(localCopy).use { zip ->
            val manifestEntry = zip.getEntry("sphere.alb")
                ?: throw IllegalStateException("This package has no sphere.alb at its root.")
            val manifestText = zip.getInputStream(manifestEntry).readBytes().toString(Charsets.UTF_8)
            val manifest = SphereManifestParser.parse(manifestText)
            if (!manifest.isPlayable) throw IllegalStateException("The manifest needs a literal title and video.")
            val videoName = manifest.video!!.substringAfterLast('/')
            val videoEntry = zip.getEntry(manifest.video)
                ?: throw IllegalStateException("The video '${manifest.video}' is not inside the package.")
            val videoFile = File(cacheDir, "${System.currentTimeMillis()}-$videoName")
            zip.getInputStream(videoEntry).use { input ->
                videoFile.outputStream().use { output -> input.copyTo(output) }
            }
            val posterFile = manifest.poster?.let { posterName ->
                zip.getEntry(posterName)?.let { posterEntry ->
                    val file = File(cacheDir, "${System.currentTimeMillis()}-${posterName.substringAfterLast('/')}")
                    zip.getInputStream(posterEntry).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file
                }
            }
            SphereState.Ready(
                manifest = manifest,
                playTarget = Uri.fromFile(videoFile).toString(),
                posterData = posterFile ?: manifest.poster,
            )
        }
    }

    private fun formatTimestamp(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    companion object {
        private const val EXTRA_URI = "sphere_uri"

        fun start(context: Context, uri: Uri) {
            context.startActivity(
                Intent(context, SphereActivity::class.java).apply {
                    putExtra(EXTRA_URI, uri)
                },
            )
        }
    }
}
