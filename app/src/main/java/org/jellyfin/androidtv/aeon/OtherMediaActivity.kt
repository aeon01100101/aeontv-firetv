package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.jellyfin.androidtv.aeon.AeonUi.menuCard
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView

/**
 * The "Other media" door: the full public archive.org catalog and our own
 * 98-channel live TV.
 */
class OtherMediaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = screenRoot()
        root.addView(titleView("Other media", 56))
        root.addView(subtitleView("Everything else to watch", 24))
        root.addView(
            menuCard("Browse the Archive", "Movies and TV from all of archive.org") {
                ArchiveActivity.start(this)
            },
        )
        root.addView(
            menuCard("Watch Live TV", "Our own channels, playing now") {
                LiveTvActivity.start(this)
            },
        )
        setContentView(root)
        root.getChildAt(2).requestFocus()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OtherMediaActivity::class.java))
        }
    }
}
