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
import org.jellyfin.androidtv.aeon.AeonUi.errorView
import org.jellyfin.androidtv.aeon.AeonUi.loadingView
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView

/**
 * Our own live TV: the 98 channels from our livetv.m3u + guide.xml on
 * archive.org, downloaded fresh every open (last copy kept for offline).
 */
class LiveTvActivity : Activity() {
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
        root.addView(loadingView("Loading channels…"))
        setContentView(root)
        scope.launch {
            val data = try {
                withContext(Dispatchers.IO) { LiveTvApi.load(applicationContext) }
            } catch (e: Exception) {
                val error = screenRoot()
                error.addView(titleView("Live TV", 56))
                error.addView(
                    errorView(
                        "Couldn't load the channels.\n${e.message ?: ""}",
                        "Retry",
                    ) { load() },
                )
                setContentView(error)
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
                LiveTvList.build(this@LiveTvActivity, data) { channel ->
                    TvPlayerActivity.start(this@LiveTvActivity, channel.url, channel.name)
                },
            )
            setContentView(view)
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LiveTvActivity::class.java))
        }
    }
}
