package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import coil3.ImageLoader
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
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.aeon.AeonUi.toast

/**
 * One archive.org item: poster, details and its playable files.
 * Sphere files appear as ordinary videos and open through the sphere
 * pipeline when picked (invisible .sphere support).
 */
class ArchiveDetailActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val identifier = intent.getStringExtra(EXTRA_IDENTIFIER).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val year = intent.getStringExtra(EXTRA_YEAR)
        val creator = intent.getStringExtra(EXTRA_CREATOR)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val loader = OriginalThumbnails.get(this)

        val root = screenRoot()
        root.addView(loadingView("Loading files…"))
        setContentView(root)

        scope.launch {
            val ultraLow = StreamPrefs.isUltraLow(this@ArchiveDetailActivity)
            val files = try {
                withContext(Dispatchers.IO) { ArchiveApi.loadFiles(identifier, ultraLow) }
            } catch (e: Exception) {
                showError("Couldn't list the files.")
                return@launch
            }
            if (files.isEmpty()) {
                showError("No playable video in this item.")
                return@launch
            }
            setContentView(buildDetail(identifier, title, year, creator, description, files, loader))
        }
    }

    private fun buildDetail(
        identifier: String,
        title: String,
        year: String?,
        creator: String?,
        description: String?,
        files: List<ArchiveFile>,
        loader: ImageLoader,
    ): ViewGroup {
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
        column.addView(titleView(title, 48))
        val meta = listOfNotNull(year, creator).joinToString(" · ")
        if (meta.isNotBlank()) column.addView(subtitleView(meta, 24))
        if (!description.isNullOrBlank()) {
            column.addView(subtitleView(description.take(600), 22))
        }
        column.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(16),
            )
        })

        for (file in files) {
            val detail = listOfNotNull(
                file.format.takeIf { it.isNotBlank() },
                file.sizeLabel.takeIf { it.isNotBlank() },
                file.lengthLabel.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            column.addView(
                with(this) {
                    listRow(file.name, detail.ifBlank { null }) {
                        playFile(identifier, title, file)
                    }
                },
            )
        }
        scroll.addView(column)
        return with(this) {
            detailRoot(scroll, "https://archive.org/services/img/$identifier", loader)
        }
    }

    private fun showError(message: String) {
        val root = screenRoot()
        root.addView(errorView(message, "Back") { finish() })
        setContentView(root)
    }

    private fun playFile(identifier: String, title: String, file: ArchiveFile) {
        scope.launch {
            if (file.isSphere) {
                try {
                    val local = withContext(Dispatchers.IO) {
                        ArchiveApi.downloadToCache(applicationContext, identifier, file.name)
                    }
                    SphereActivity.start(this@ArchiveDetailActivity, Uri.fromFile(local))
                } catch (e: Exception) {
                    toast("Couldn't open the .sphere file")
                }
            } else {
                TvPlayerActivity.start(this@ArchiveDetailActivity, file.streamUrl(identifier), title)
            }
        }
    }

    companion object {
        private const val EXTRA_IDENTIFIER = "identifier"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_YEAR = "year"
        private const val EXTRA_CREATOR = "creator"
        private const val EXTRA_DESCRIPTION = "description"

        fun start(
            context: Context,
            identifier: String,
            title: String,
            year: String?,
            creator: String?,
            description: String?,
        ) {
            context.startActivity(
                Intent(context, ArchiveDetailActivity::class.java).apply {
                    putExtra(EXTRA_IDENTIFIER, identifier)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_YEAR, year)
                    putExtra(EXTRA_CREATOR, creator)
                    putExtra(EXTRA_DESCRIPTION, description)
                },
            )
        }
    }
}
