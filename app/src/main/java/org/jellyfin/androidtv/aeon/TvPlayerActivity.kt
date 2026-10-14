package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
 */
class TvPlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

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
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

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
        })
        exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
        exoPlayer.prepare()
        val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        if (startPositionMs > 0) exoPlayer.seekTo(startPositionMs)
        exoPlayer.playWhenReady = true
        playerView.player = exoPlayer
        player = exoPlayer
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

        fun start(context: Context, streamUrl: String, title: String, startPositionMs: Long = 0L) {
            context.startActivity(
                Intent(context, TvPlayerActivity::class.java).apply {
                    putExtra(EXTRA_STREAM_URL, streamUrl)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_START_POSITION_MS, startPositionMs)
                },
            )
        }
    }
}
