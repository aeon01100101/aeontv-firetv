package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.jellyfin.androidtv.aeon.AeonUi.doorArtGlyph
import org.jellyfin.androidtv.aeon.AeonUi.doorArtLogo
import org.jellyfin.androidtv.aeon.AeonUi.doorCard
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.footerLine
import org.jellyfin.androidtv.aeon.AeonUi.heroBanner
import org.jellyfin.androidtv.aeon.AeonUi.homeGradient
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.R

/**
 * The AeonTV front door: two choices, no servers.
 * - Originally from AeonTV: Isaiah's own archive.org uploads.
 * - Other media: the public archive.org catalog + our live TV.
 */
class AeonHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = homeGradient()
            val p = dp(48)
            setPadding(p, dp(24), p, dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(heroBanner())
        root.addView(subtitleView("What do you want to watch?", 26))
        val originals = doorCard(
            getString(R.string.aeon_home_door_originals),
            getString(R.string.aeon_home_door_originals_sub),
            doorArtLogo(),
        ) { OriginalsActivity.start(this) }
        val other = doorCard(
            getString(R.string.aeon_home_door_other),
            getString(R.string.aeon_home_door_other_sub),
            doorArtGlyph("▶"),
        ) { OtherMediaActivity.start(this) }
        root.addView(originals)
        root.addView(other)
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        })
        root.addView(footerLine())
        setContentView(root)
        originals.requestFocus()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AeonHomeActivity::class.java))
        }
    }
}
