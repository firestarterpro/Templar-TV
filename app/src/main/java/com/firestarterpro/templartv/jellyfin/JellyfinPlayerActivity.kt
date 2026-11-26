package com.firestarterpro.templartv.jellyfin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Plays movies, episodes and songs from the personal Jellyfin server inside Templar TV, so the
 * Jellyfin app is not needed. Plays the original file (direct stream); a file the Streamer can't
 * decode shows the player's error. The key is sent as a header, never put in the URL.
 * After the TV sleeps it comes back on the same item and position, paused.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class JellyfinPlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    /** Where to pick up after the activity was stopped (TV slept, Home pressed). Null on first start. */
    private var resumeAt: Resume? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 4_000
        }
        setContentView(view)
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_INDEX)) {
            resumeAt = Resume(savedInstanceState.getInt(STATE_INDEX), savedInstanceState.getLong(STATE_POS))
        }
    }

    override fun onStart() {
        super.onStart()
        val config = JellyfinConfig(this)
        val urls = intent.getStringArrayExtra(EXTRA_URLS) ?: return finish()
        val titles = intent.getStringArrayExtra(EXTRA_TITLES) ?: arrayOf()
        val first = intent.getIntExtra(EXTRA_START, 0)
        val (index, position, autoplay) = PlayerResume.startPoint(urls.size, first, resumeAt)
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to JellyfinClient.authHeader(config.apiKey)))
            .setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
        player = p
        view.player = p
        // The screen stays on only while something is actually playing, not while paused.
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                view.keepScreenOn = isPlaying
            }
        })
        p.setMediaItems(
            urls.mapIndexed { i, u ->
                MediaItem.Builder().setUri(u)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(titles.getOrNull(i).orEmpty()).build())
                    .build()
            },
            index,
            position,
        )
        p.playWhenReady = autoplay
        p.prepare()
        if (!autoplay) view.showController()
        view.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        player?.let { resumeAt = Resume(it.currentMediaItemIndex, it.currentPosition.coerceAtLeast(0)) }
        view.keepScreenOn = false
        view.player = null
        player?.release()
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val r = player?.let { Resume(it.currentMediaItemIndex, it.currentPosition.coerceAtLeast(0)) } ?: resumeAt
        if (r != null) {
            outState.putInt(STATE_INDEX, r.index)
            outState.putLong(STATE_POS, r.positionMs)
        }
    }

    companion object {
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_START = "start"
        private const val STATE_INDEX = "resume_index"
        private const val STATE_POS = "resume_pos"

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
