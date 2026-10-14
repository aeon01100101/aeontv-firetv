package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.jellyfin.androidtv.aeon.AeonUi.doorArtGlyph
import org.jellyfin.androidtv.aeon.AeonUi.doorCard
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.footerLine
import org.jellyfin.androidtv.aeon.AeonUi.homeGradient
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView

/**
 * The "Other media" door: the full public archive.org catalog and our own
 * 98-channel live TV.
 */
class OtherMediaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = homeGradient()
            val p = dp(48)
            setPadding(p, dp(32), p, dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(titleView("Other media", 56))
        root.addView(subtitleView("Everything else to watch", 24))
        val browse = doorCard(
            "Browse the Archive",
            "Movies and TV from all of archive.org",
            doorArtGlyph("▶"),
        ) { ArchiveActivity.start(this) }
        val live = doorCard(
            "Watch Live TV",
            "Our own channels, playing now",
            doorArtGlyph("●", 0xFFFF5252.toInt()),
        ) { LiveTvActivity.start(this) }
        root.addView(browse)
        root.addView(live)
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        })
        root.addView(footerLine())
        setContentView(root)
        browse.requestFocus()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, OtherMediaActivity::class.java))
        }
    }
}
