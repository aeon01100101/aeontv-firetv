package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import org.jellyfin.androidtv.aeon.AeonUi.toast

/**
 * Full-screen Media3 player for the AeonTV TV screens (archive videos,
 * live channels, sphere targets). Same extras contract as the phone
 * ArchivePlayerActivity.
 *
 * Tracked playback (archive/originals videos with identity extras) records
 * progress into the active profile's Continue Watching — every ~15s, on
 * pause, and on stop — and resumes where it left off. Entries are removed
 * once playback passes 95%. Live TV and sphere playback are untracked.
 */
class TvPlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            recordProgress()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
        }
        setContentView(playerView)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
        if (isTracked()) {
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, PROGRESS_INTERVAL_MS)
        }
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        recordProgress()
        releasePlayer()
        super.onStop()
    }

    private fun isTracked(): Boolean =
        intent.getStringExtra(EXTRA_KIND) != null &&
            intent.getStringExtra(EXTRA_IDENTIFIER) != null &&
            intent.getStringExtra(EXTRA_FILE_KEY) != null

    private fun continueKey(): String =
        intent.getStringExtra(EXTRA_KIND).orEmpty() + "\u0001" +
            intent.getStringExtra(EXTRA_IDENTIFIER).orEmpty() + "\u0001" +
            intent.getStringExtra(EXTRA_FILE_KEY).orEmpty()

    private fun initializePlayer() {
        if (player != null) return
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        if (streamUrl.isNullOrBlank()) {
            toast("No stream to play")
            finish()
            return
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AeonTV")
            .setAllowCrossProtocolRedirects(true)
        val playerBuilder = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
        if (StreamPrefs.isUltraLow(this)) {
            // 110p ultra-low mode: cap rendition choice at a 110p ceiling so
            // even adaptive HLS streams pick the lowest they offer.
            val trackSelector = DefaultTrackSelector(this)
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSize(196, 110)
                    .setMaxVideoBitrate(250_000)
                    .setForceLowestBitrate(true),
            )
            playerBuilder.setTrackSelector(trackSelector)
        }
        val exoPlayer = playerBuilder.build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                toast("Couldn't play this file")
                finish()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) recordProgress()
            }
        })
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        val explicitStart = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        when {
            explicitStart > 0 -> exoPlayer.seekTo(explicitStart)
            isTracked() -> {
                val entry = Profiles.activeId(this)
                    ?.let { Profiles.continueEntry(this, it, continueKey()) }
                if (entry != null && entry.positionMs > 0) exoPlayer.seekTo(entry.positionMs)
            }
        }
        exoPlayer.playWhenReady = true
        playerView.player = exoPlayer
        player = exoPlayer
    }

    private fun recordProgress() {
        if (!isTracked()) return
        val exoPlayer = player ?: return
        val profileId = Profiles.activeId(this) ?: return
        val duration = exoPlayer.duration
        val position = exoPlayer.currentPosition
        if (duration <= 0 || position <= 0) return
        val key = continueKey()
        if (position.toDouble() / duration >= COMPLETE_FRACTION) {
            Profiles.removeContinue(this, profileId, key)
            return
        }
        Profiles.recordContinue(
            this,
            profileId,
            ContinueEntry(
                key = key,
                kind = intent.getStringExtra(EXTRA_KIND).orEmpty(),
                identifier = intent.getStringExtra(EXTRA_IDENTIFIER).orEmpty(),
                fileKey = intent.getStringExtra(EXTRA_FILE_KEY).orEmpty(),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                posterUrl = intent.getStringExtra(EXTRA_POSTER),
                streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty(),
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    companion object {
        private const val EXTRA_STREAM_URL = "stream_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_START_POSITION_MS = "start_position_ms"
        private const val EXTRA_KIND = "track_kind"
        private const val EXTRA_IDENTIFIER = "track_identifier"
        private const val EXTRA_FILE_KEY = "track_file_key"
        private const val EXTRA_POSTER = "track_poster"
        private const val PROGRESS_INTERVAL_MS = 15_000L
        private const val COMPLETE_FRACTION = 0.95

        fun start(context: Context, streamUrl: String, title: String, startPositionMs: Long = 0L) {
            context.startActivity(
                Intent(context, TvPlayerActivity::class.java).apply {
                    putExtra(EXTRA_STREAM_URL, streamUrl)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_START_POSITION_MS, startPositionMs)
                },
            )
        }

        /**
         * Tracked playback: records progress into the active profile's
         * Continue Watching and resumes from it when no explicit start
         * position is given.
         */
        fun startTracked(
            context: Context,
            kind: String,
            identifier: String,
            fileKey: String,
            title: String,
            posterUrl: String?,
            streamUrl: String,
            startPositionMs: Long = 0L,
        ) {
            context.startActivity(
                Intent(context, TvPlayerActivity::class.java).apply {
                    putExtra(EXTRA_STREAM_URL, streamUrl)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_START_POSITION_MS, startPositionMs)
                    putExtra(EXTRA_KIND, kind)
                    putExtra(EXTRA_IDENTIFIER, identifier)
                    putExtra(EXTRA_FILE_KEY, fileKey)
                    putExtra(EXTRA_POSTER, posterUrl)
                },
            )
        }
    }
}
