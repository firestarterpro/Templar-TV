package com.firestarterpro.templartv.jellyfin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Plays movies, episodes and songs from the personal Jellyfin server inside Templar TV, so the
 * Jellyfin app is not needed. Plays the original file (direct stream); a file the Streamer can't
 * decode shows the player's error. The key is sent as a header, never put in the URL.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class JellyfinPlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 4_000
            keepScreenOn = true
        }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        val config = JellyfinConfig(this)
        val urls = intent.getStringArrayExtra(EXTRA_URLS) ?: return finish()
        val titles = intent.getStringArrayExtra(EXTRA_TITLES) ?: arrayOf()
        val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to JellyfinClient.authHeader(config.apiKey)))
            .setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
        player = p
        view.player = p
        p.setMediaItems(
            urls.mapIndexed { i, u ->
                MediaItem.Builder().setUri(u)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(i).orEmpty()).build())
                    .build()
            },
            start,
            0L,
        )
        p.playWhenReady = true
        p.prepare()
        view.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        view.player = null
        player?.release()
        player = null
    }

    companion object {
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_START = "start"

        /** Plays [items] in order, starting at [start]. Folders are skipped. */
        fun play(context: Context, config: JellyfinConfig, items: List<JellyfinItem>, start: JellyfinItem) {
            val playable = items.filter { !it.isFolder }
            val startIndex = playable.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
            val intent = Intent(context, JellyfinPlayerActivity::class.java)
                .putExtra(EXTRA_URLS, playable.map { JellyfinClient.streamUrl(config, it) }.toTypedArray())
                .putExtra(EXTRA_TITLES, playable.map { it.title }.toTypedArray())
                .putExtra(EXTRA_START, startIndex)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
