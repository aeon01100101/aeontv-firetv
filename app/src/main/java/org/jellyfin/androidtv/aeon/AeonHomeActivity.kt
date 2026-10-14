package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import org.jellyfin.androidtv.aeon.AeonUi.applyAeonFocus
import org.jellyfin.androidtv.aeon.AeonUi.avatarView
import org.jellyfin.androidtv.aeon.AeonUi.doorArtGlyph
import org.jellyfin.androidtv.aeon.AeonUi.doorArtLogo
import org.jellyfin.androidtv.aeon.AeonUi.doorCard
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.footerLine
import org.jellyfin.androidtv.aeon.AeonUi.heroBanner
import org.jellyfin.androidtv.aeon.AeonUi.homeGradient
import org.jellyfin.androidtv.aeon.AeonUi.horizontalRow
import org.jellyfin.androidtv.aeon.AeonUi.loadThumb
import org.jellyfin.androidtv.aeon.AeonUi.posterHolder
import org.jellyfin.androidtv.aeon.AeonUi.PosterHolder
import org.jellyfin.androidtv.aeon.AeonUi.profileColor
import org.jellyfin.androidtv.aeon.AeonUi.resetAeonFocus
import org.jellyfin.androidtv.aeon.AeonUi.subtitleView
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.R

/**
 * The AeonTV front door: two choices, no servers.
 * - Originally from AeonTV: Isaiah's own archive.org uploads.
 * - Other media: the public archive.org catalog + our live TV.
 *
 * Shows the active profile (with switch), the profile's Continue Watching
 * and Favorites rows, and the stream-quality toggle.
 */
class AeonHomeActivity : Activity() {
    private lateinit var loader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Profiles exist but nobody is signed in (gate bypassed) → back to the gate.
        if (Profiles.hasProfiles(this) && Profiles.activeId(this) == null) {
            ProfileGateActivity.start(this)
            finish()
            return
        }
        loader = OriginalThumbnails.get(this)
        val active = Profiles.active(this)

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = homeGradient()
            val p = dp(48)
            setPadding(p, dp(24), p, dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        root.addView(heroBanner())
        if (active != null) root.addView(profileChip(active))
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
        if (active != null) {
            if (active.continueWatching.isNotEmpty()) {
                root.addView(continueWatchingSection(active.continueWatching))
            }
            if (active.favorites.isNotEmpty()) {
                root.addView(favoritesSection(active.favorites))
            }
        }
        val ultraLow = StreamPrefs.isUltraLow(this)
        val quality = doorCard(
            "Stream quality",
            if (ultraLow) "110p ultra-low — for slow connections" else "Full quality",
            doorArtGlyph("▼"),
        ) {
            StreamPrefs.setUltraLow(this, !StreamPrefs.isUltraLow(this))
            recreate()
        }
        root.addView(quality)
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24),
            )
        })
        root.addView(footerLine())
        scroll.addView(root)
        setContentView(scroll)
        originals.requestFocus()
    }

    private fun profileChip(profile: Profile): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setBackgroundResource(R.drawable.aeon_card)
        setPadding(dp(20), dp(14), dp(28), dp(14))
        addView(avatarView(profileColor(profile.colorIndex), profile.name, 64))
        addView(TextView(this@AeonHomeActivity).apply {
            text = profile.name
            setTextColor(AeonUi.TEXT)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = dp(16) }
        })
        addView(TextView(this@AeonHomeActivity).apply {
            text = "Switch profile"
            setTextColor(AeonUi.SUB)
            textSize = 22f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = dp(16) }
        })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(20) }
        setOnClickListener {
            Profiles.clearActiveId(this@AeonHomeActivity)
            SessionState.ownerAuthed = false
            ProfileGateActivity.start(this@AeonHomeActivity)
            finish()
        }
        applyAeonFocus(dimIdle = false)
    }

    private fun sectionTitle(text: String): TextView =
        with(this) {
            titleView(text, 34)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(16)
                bottomMargin = dp(12)
            }
        }

    private fun posterCellWidth(holder: PosterHolder) {
        holder.root.layoutParams = RecyclerView.LayoutParams(dp(200), RecyclerView.LayoutParams.WRAP_CONTENT)
    }

    private fun continueWatchingSection(entries: List<ContinueEntry>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Continue Watching"))
            val row = with(this@AeonHomeActivity) { horizontalRow() }
            row.adapter = ContinueAdapter(entries)
            addView(row)
        }

    private inner class ContinueAdapter(
        private val entries: List<ContinueEntry>,
    ) : RecyclerView.Adapter<PosterHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterHolder {
            val holder = with(this@AeonHomeActivity) {
                posterHolder(parent) { position ->
                    if (position in entries.indices) play(entries[position])
                }
            }
            posterCellWidth(holder)
            val progress = ProgressBar(
                this@AeonHomeActivity,
                null,
                android.R.attr.progressBarStyleHorizontal,
            ).apply {
                max = 1000
                progressTintList = ColorStateList.valueOf(AeonUi.ACCENT)
                progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF.toInt())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    this@AeonHomeActivity.dp(10),
                    Gravity.BOTTOM,
                )
                tag = "cw_progress"
            }
            holder.root.addView(progress)
            return holder
        }

        override fun onBindViewHolder(holder: PosterHolder, position: Int) {
            val entry = entries[position]
            holder.root.tag = position
            holder.root.resetAeonFocus()
            holder.image.loadThumb(entry.posterUrl, loader)
            holder.label.text = entry.title
            holder.year.text = ""
            val fraction = if (entry.durationMs > 0) {
                (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
            } else 0f
            (holder.root.findViewWithTag<ProgressBar>("cw_progress"))?.progress =
                (fraction * 1000).toInt()
        }

        override fun getItemCount(): Int = entries.size

        private fun play(entry: ContinueEntry) {
            TvPlayerActivity.startTracked(
                this@AeonHomeActivity,
                entry.kind,
                entry.identifier,
                entry.fileKey,
                entry.title,
                entry.posterUrl,
                entry.streamUrl,
                entry.positionMs,
            )
        }
    }

    private fun favoritesSection(favs: List<FavoriteEntry>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("Favorites"))
            val row = with(this@AeonHomeActivity) { horizontalRow() }
            row.adapter = FavoritesAdapter(favs)
            addView(row)
        }

    private inner class FavoritesAdapter(
        private val favs: List<FavoriteEntry>,
    ) : RecyclerView.Adapter<PosterHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterHolder {
            val holder = with(this@AeonHomeActivity) {
                posterHolder(parent) { position ->
                    if (position in favs.indices) open(favs[position])
                }
            }
            posterCellWidth(holder)
            return holder
        }

        override fun onBindViewHolder(holder: PosterHolder, position: Int) {
            val fav = favs[position]
            holder.root.tag = position
            holder.root.resetAeonFocus()
            holder.image.loadThumb(fav.posterUrl, loader)
            holder.label.text = fav.title
            holder.year.text = ""
        }

        override fun getItemCount(): Int = favs.size

        private fun open(fav: FavoriteEntry) {
            if (fav.kind == "archive") {
                ArchiveDetailActivity.start(
                    this@AeonHomeActivity,
                    fav.identifier,
                    fav.title,
                    null,
                    null,
                    null,
                )
            } else {
                OriginalFilesActivity.start(this@AeonHomeActivity, fav.identifier, fav.title)
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AeonHomeActivity::class.java))
        }
    }
}
