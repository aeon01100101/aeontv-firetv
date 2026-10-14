package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.jellyfin.androidtv.aeon.AeonUi.menuCard
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.R

/**
 * The AeonTV front door: two choices, no servers.
 * - Originally from AeonTV: Isaiah's own archive.org uploads.
 * - Other media: the public archive.org catalog + our live TV.
 */
class AeonHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = screenRoot()
        root.addView(titleView(getString(R.string.aeon_home_title), 64))
        root.addView(subtitleView("What do you want to watch?", 26))
        root.addView(
            menuCard(
                getString(R.string.aeon_home_door_originals),
                getString(R.string.aeon_home_door_originals_sub),
            ) { OriginalsActivity.start(this) },
        )
        root.addView(
            menuCard(
                getString(R.string.aeon_home_door_other),
                getString(R.string.aeon_home_door_other_sub),
            ) { OtherMediaActivity.start(this) },
        )
        setContentView(root)
        root.getChildAt(2).requestFocus()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AeonHomeActivity::class.java))
        }
    }
}
