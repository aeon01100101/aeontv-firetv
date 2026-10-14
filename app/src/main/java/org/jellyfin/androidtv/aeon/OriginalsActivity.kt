package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.aeon.AeonUi.PosterHolder
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.errorView
import org.jellyfin.androidtv.aeon.AeonUi.listRow
import org.jellyfin.androidtv.aeon.AeonUi.loadThumb
import org.jellyfin.androidtv.aeon.AeonUi.loadingView
import org.jellyfin.androidtv.aeon.AeonUi.menuCard
import org.jellyfin.androidtv.aeon.AeonUi.posterGrid
import org.jellyfin.androidtv.aeon.AeonUi.posterHolder
import org.jellyfin.androidtv.aeon.AeonUi.resetAeonFocus
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.aeon.AeonUi.toast

/**
 * "Originally from AeonTV": Isaiah's own archive.org uploads, with official
 * TMDB posters (Jellyfin-style matching) where one is found. Opens a single
 * file straight away; .sphere files play through the sphere pipeline.
 */
class OriginalsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var imageLoader: ImageLoader

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageLoader = OriginalThumbnails.get(this)
        showLoading()
        scope.launch {
            val entries = try {
                withContext(Dispatchers.IO) { OriginalsApi.fetchOriginals(applicationContext) }
            } catch (e: Exception) {
                showError("Couldn't reach archive.org.\n${e.message ?: ""}")
                return@launch
            }
            if (entries.isEmpty()) {
                showError("Nothing playable on the archive yet.")
                return@launch
            }
            showGrid(entries)
            OriginalThumbnails.prefetch(applicationContext, entries.map { it.posterUrl })
        }
    }

    private fun showLoading() {
        val root = screenRoot()
        root.addView(loadingView("Loading originals…"))
        setContentView(root)
    }

    private fun showError(message: String) {
        val root = screenRoot()
        root.addView(titleView("Originally from AeonTV", 56))
        root.addView(errorView(message, "Retry") { recreate() })
        setContentView(root)
    }

    private fun showGrid(entries: List<OriginalEntry>) {
        val root = screenRoot()
        root.addView(titleView("Originally from AeonTV", 56))
        root.addView(subtitleView("${entries.size} shows and movies", 24))
        root.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12),
            )
        })
        root.addView(
            listRow("Live TV", "Channels from our own playlists") {
                OriginalsLiveTvActivity.start(this)
            },
        )
        val grid = posterGrid(4)
        grid.adapter = OriginalsAdapter(entries)
        root.addView(grid)
        setContentView(root)
    }

    private inner class OriginalsAdapter(
        private val entries: List<OriginalEntry>,
    ) : RecyclerView.Adapter<PosterHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterHolder =
            with(this@OriginalsActivity) {
                posterHolder(parent) { position ->
                    if (position in entries.indices) openEntry(entries[position])
                }
            }

        override fun onBindViewHolder(holder: PosterHolder, position: Int) {
            val entry = entries[position]
            holder.root.tag = position
            holder.image.loadThumb(entry.posterUrl, imageLoader)
            holder.label.text = entry.item.title
            holder.year.text = entry.item.year ?: ""
            holder.root.resetAeonFocus(dimIdle = true)
        }

        override fun getItemCount(): Int = entries.size
    }

    private fun openEntry(entry: OriginalEntry) {
        toast("Loading…")
        scope.launch {
            val files = try {
                withContext(Dispatchers.IO) {
                    OriginalsApi.loadPlayableFiles(entry.item.identifier)
                }
            } catch (e: Exception) {
                toast("Couldn't list the files")
                return@launch
            }
            when {
                files.isEmpty() -> toast("Nothing playable in this item")
                files.size == 1 -> playFile(entry.item.identifier, entry.item.title, files[0])
                else -> OriginalFilesActivity.start(
                    this@OriginalsActivity,
                    entry.item.identifier,
                    entry.item.title,
                )
            }
        }
    }

    private fun playFile(identifier: String, title: String, file: OriginalFile) {
        scope.launch {
            if (file.isSphere) {
                try {
                    val local = withContext(Dispatchers.IO) {
                        OriginalsApi.downloadSphere(applicationContext, identifier, file.name)
                    }
                    SphereActivity.start(this@OriginalsActivity, Uri.fromFile(local))
                } catch (e: Exception) {
                    toast("Couldn't open the .sphere file")
                }
            } else {
                TvPlayerActivity.start(this@OriginalsActivity, file.streamUrl(identifier), title)
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OriginalsActivity::class.java))
        }
    }
}
