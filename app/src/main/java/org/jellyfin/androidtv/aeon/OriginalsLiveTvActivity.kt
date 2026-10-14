package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.aeon.AeonUi.accentButton
import org.jellyfin.androidtv.aeon.AeonUi.errorView
import org.jellyfin.androidtv.aeon.AeonUi.loadingView
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView

/**
 * Originals Live TV: channels from playlists auto-detected on our own
 * archive.org uploads. Uploading a new item with an .m3u in it adds its
 * channels here on its own — no app update needed.
 */
class OriginalsLiveTvActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
    }

    private fun load() {
        val root = screenRoot()
        root.addView(loadingView("Looking for playlists on the archive…"))
        setContentView(root)
        scope.launch {
            val data = try {
                withContext(Dispatchers.IO) { OriginalsLiveTvApi.load(applicationContext) }
            } catch (e: Exception) {
                val error = screenRoot()
                error.addView(titleView("Live TV", 56))
                error.addView(
                    errorView(
                        "Couldn't check the archive for playlists.\n" +
                            "Originals Live TV finds its channels in playlists uploaded " +
                            "to our archive.org account.\n${e.message ?: ""}",
                        "Retry",
                    ) { load() },
                )
                setContentView(error)
                return@launch
            }
            if (data.channels.isEmpty()) {
                val empty = screenRoot()
                empty.addView(titleView("No AeonTV playlists yet", 44))
                empty.addView(
                    subtitleView(
                        "Upload an archive.org item containing an .m3u playlist file " +
                            "(and an optional XMLTV guide .xml) to the AeonTV account, and " +
                            "its channels will appear here automatically — no app update needed.",
                        24,
                    ),
                )
                empty.addView(with(this@OriginalsLiveTvActivity) { accentButton("Check again") { load() } })
                setContentView(empty)
                return@launch
            }
            val view = screenRoot()
            view.addView(titleView("Live TV", 56))
            val sub = "${data.channels.size} channels"
            view.addView(
                subtitleView(
                    if (data.fromCache) "$sub — offline, showing the saved list" else sub,
                    24,
                ),
            )
            view.addView(
                LiveTvList.build(this@OriginalsLiveTvActivity, data) { channel ->
                    TvPlayerActivity.start(this@OriginalsLiveTvActivity, channel.url, channel.name)
                },
            )
            setContentView(view)
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OriginalsLiveTvActivity::class.java))
        }
    }
}
