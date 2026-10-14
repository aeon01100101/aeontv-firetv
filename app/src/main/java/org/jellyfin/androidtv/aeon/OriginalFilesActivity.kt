package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
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
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.aeon.AeonUi.toast

/**
 * The playable files inside one Originals item. Sphere files list like any
 * other video and open through the sphere pipeline when picked.
 */
class OriginalFilesActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val identifier = intent.getStringExtra(EXTRA_IDENTIFIER).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val root = screenRoot()
        root.addView(loadingView("Loading files…"))
        setContentView(root)

        scope.launch {
            val ultraLow = StreamPrefs.isUltraLow(this@OriginalFilesActivity)
            val files = try {
                withContext(Dispatchers.IO) { OriginalsApi.loadPlayableFiles(identifier, ultraLow) }
            } catch (e: Exception) {
                showError("Couldn't list the files.")
                return@launch
            }
            if (files.isEmpty()) {
                showError("Nothing playable in this item.")
                return@launch
            }
            val list = LinearLayout(this@OriginalFilesActivity).apply {
                orientation = LinearLayout.VERTICAL
                val p = dp(48)
                setPadding(p, dp(40), p, dp(32))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            list.addView(titleView(title, 44))
            val rows = files.map { file ->
                with(this@OriginalFilesActivity) {
                    listRow(file.name, file.sizeLabel) { playFile(identifier, title, file) }
                }.also { list.addView(it) }
            }
            setContentView(
                with(this@OriginalFilesActivity) {
                    detailRoot(
                        list,
                        "https://archive.org/services/img/$identifier",
                        OriginalThumbnails.get(this@OriginalFilesActivity),
                    )
                },
            )
            rows.firstOrNull()?.requestFocus()
        }
    }

    private fun showError(message: String) {
        val root = screenRoot()
        root.addView(errorView(message, "Back") { finish() })
        setContentView(root)
    }

    private fun playFile(identifier: String, title: String, file: OriginalFile) {
        scope.launch {
            if (file.isSphere) {
                try {
                    val local = withContext(Dispatchers.IO) {
                        OriginalsApi.downloadSphere(applicationContext, identifier, file.name)
                    }
                    SphereActivity.start(this@OriginalFilesActivity, Uri.fromFile(local))
                } catch (e: Exception) {
                    toast("Couldn't open the .sphere file")
                }
            } else {
                TvPlayerActivity.start(this@OriginalFilesActivity, file.streamUrl(identifier), title)
            }
        }
    }

    companion object {
        private const val EXTRA_IDENTIFIER = "identifier"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, identifier: String, title: String) {
            context.startActivity(
                Intent(context, OriginalFilesActivity::class.java).apply {
                    putExtra(EXTRA_IDENTIFIER, identifier)
                    putExtra(EXTRA_TITLE, title)
                },
            )
        }
    }
}
