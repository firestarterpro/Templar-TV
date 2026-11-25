package com.firestarterpro.templartv.observation

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.TemplarData
import com.firestarterpro.templartv.data.watchhistory.PlaybackObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Watches the media sessions of the streaming apps and records what is playing into the local
 * watch history, which puts confirmed titles on that provider's "recently played" shelf.
 *
 * Reads only what an app chooses to publish for Android's own media controls (title, position,
 * state). It does not record the screen, touch protected video or read anything about accounts.
 * Android only lets an app list other apps' media sessions while its notification listener is
 * enabled, so this is started and stopped by [com.firestarterpro.templartv.service.VistaNotificationListenerService].
 * Without that access [status] is [Status.NO_ACCESS] and nothing is observed.
 *
 * Runs only while [Flag.MEDIA_SESSION_OBSERVATION] and [Flag.WATCH_HISTORY] are on, and follows the
 * flags live. Turning a flag off stops new writes and never deletes anything.
 */
class MediaSessionObserver private constructor(context: Context) {

    enum class Status { STOPPED, OFF, NO_ACCESS, WATCHING }

    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val data by lazy { TemplarData.get(app) }
    private val launches = ProviderLaunchStore(app)
    private val tracker = PlaybackTracker(launchedFromTemplar = launches::launchedWithin)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<PlaybackObservation>(Channel.UNLIMITED)

    private class Watched(val controller: MediaController, val callback: MediaController.Callback, val provider: String)

    private val watched = HashMap<String, Watched>()
    private val lastNote = HashMap<String, String>()
    private var listenerComponent: ComponentName? = null
    private var manager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var flagListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var ticking = false
    private var writerStarted = false

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Call from the notification listener once it is connected. Safe to call again. */
    fun start(listener: ComponentName) {
        handler.post {
            listenerComponent = listener
            if (flagListener == null) {
                flagListener = FeatureFlags.register(app) { handler.post { refresh() } }
            }
            startWriter()
            refresh()
        }
    }

    /** Call when the notification listener disconnects. */
    fun stop() {
        handler.post {
            detach(flush = true)
            flagListener?.let { FeatureFlags.unregister(app, it) }
            flagListener = null
            listenerComponent = null
            _status.value = Status.STOPPED
        }
    }

    private fun enabled() =
        FeatureFlags.isEnabled(app, Flag.MEDIA_SESSION_OBSERVATION) && FeatureFlags.isEnabled(app, Flag.WATCH_HISTORY)

    private fun refresh() {
        val component = listenerComponent ?: return
        ObserverDiagnostics.enabled = FeatureFlags.isEnabled(app, Flag.DIAGNOSTICS)
        if (!enabled()) {
            detach(flush = true)
            _status.value = Status.OFF
            return
        }
        if (sessionsListener != null) return
        val mgr = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> handler.post { sync(list.orEmpty()) } }
        try {
            mgr.addOnActiveSessionsChangedListener(listener, component, handler)
            manager = mgr
            sessionsListener = listener
            sync(mgr.getActiveSessions(component))
            _status.value = Status.WATCHING
            ObserverDiagnostics.note("observer", "watching")
            scheduleTick()
        } catch (e: SecurityException) {
            _status.value = Status.NO_ACCESS
            ObserverDiagnostics.note("observer", "no notification access")
        }
    }

    private fun detach(flush: Boolean) {
        sessionsListener?.let { runCatching { manager?.removeOnActiveSessionsChangedListener(it) } }
        sessionsListener = null
        manager = null
        val keys = watched.keys.toList()
        for (key in keys) endWatching(key, flush)
        ticking = false
        handler.removeCallbacks(ticker)
    }

    private fun sync(controllers: List<MediaController>) {
        val seen = HashSet<String>()
        for (c in controllers) {
            val provider = ProviderCatalog.providerFor(c.packageName) ?: continue
            val key = c.packageName + "/" + (c.tag ?: "")
            seen += key
            if (key !in watched) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = process(key)
                    override fun onMetadataChanged(metadata: MediaMetadata?) = process(key)
                    override fun onSessionDestroyed() {
                        handler.post { endWatching(key, flush = true) }
                    }
                }
                runCatching { c.registerCallback(cb, handler) }
                watched[key] = Watched(c, cb, provider)
                ObserverDiagnostics.note(c.packageName, "session found")
            }
            process(key)
        }
        for (key in watched.keys.toList()) if (key !in seen) endWatching(key, flush = true)
    }

    private fun endWatching(key: String, flush: Boolean) {
        val w = watched.remove(key) ?: return
        runCatching { w.controller.unregisterCallback(w.callback) }
        lastNote.remove(key)
        if (flush) handle(tracker.onSessionEnded(profileId(), key, System.currentTimeMillis()))
    }

    private fun process(key: String) {
        val w = watched[key] ?: return
        val snap = snapshot(key, w) ?: return
        handle(tracker.onSnapshot(profileId(), snap))
    }

    private fun handle(events: List<TrackerEvent>) {
        for (e in events) when (e) {
            is TrackerEvent.Record -> {
                writes.trySend(e.observation)
                ObserverDiagnostics.note(e.observation.packageName, "record ${e.observation.playbackState}")
            }
            is TrackerEvent.Ignored -> {
                // Say each reason once per session until it changes, so the log stays readable.
                if (lastNote[e.sessionKey] != e.reason) {
                    lastNote[e.sessionKey] = e.reason
                    ObserverDiagnostics.note(e.sessionKey.substringBefore('/'), "ignored: ${e.reason}")
                }
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!ticking) return
            for (key in watched.keys.toList()) process(key)
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun scheduleTick() {
        if (ticking) return
        ticking = true
        handler.postDelayed(ticker, TICK_MS)
    }

    private fun snapshot(key: String, w: Watched): SessionSnapshot? {
        val c = w.controller
        val ps = runCatching { c.playbackState }.getOrNull()
        val md = runCatching { c.metadata }.getOrNull()
        val state = when (ps?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> PlayState.PLAYING
            PlaybackState.STATE_PAUSED -> PlayState.PAUSED
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlayState.BUFFERING
            else -> PlayState.STOPPED
        }
        var position = ps?.position?.coerceAtLeast(0L) ?: 0L
        if (ps != null && state == PlayState.PLAYING && ps.lastPositionUpdateTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime
            position += (elapsed * ps.playbackSpeed).toLong().coerceAtLeast(0L)
        }
        val raw = md?.let(::toRaw)
        noteShape(key, md, raw)
        return SessionSnapshot(
            sessionKey = key, packageName = c.packageName, provider = w.provider, state = state,
            metadata = raw, positionMs = position, at = System.currentTimeMillis(),
        )
    }

    /** Logs which metadata fields an app fills in (names only), once per change, to learn each provider's shape. */
    private fun noteShape(key: String, md: MediaMetadata?, raw: RawMetadata?) {
        if (md == null || raw == null) return
        val present = buildList {
            if (!raw.title.isNullOrBlank()) add("title")
            if (!raw.displayTitle.isNullOrBlank()) add("display_title")
            if (!raw.displaySubtitle.isNullOrBlank()) add("display_subtitle")
            if (!raw.displayDescription.isNullOrBlank()) add("display_description")
            if (!raw.album.isNullOrBlank()) add("album")
            if (!raw.artist.isNullOrBlank()) add("artist")
            if (!raw.albumArtist.isNullOrBlank()) add("album_artist")
            if (raw.durationMs > 0) add("duration")
            if (!raw.mediaId.isNullOrBlank()) add("media_id")
            if (!raw.artUri.isNullOrBlank()) add("art_uri")
            if (raw.trackNumber > 0) add("track")
            if (raw.discNumber > 0) add("disc")
        }.joinToString(",")
        if (FeatureFlags.isEnabled(app, Flag.DEBUG_METADATA_VALUES)) dumpValues(key, md)
        val code = "fields=$present"
        if (lastNote["shape:$key"] != code) {
            lastNote["shape:$key"] = code
            ObserverDiagnostics.note(key.substringBefore('/'), code)
        }
    }

    /** Debug flag only: every key an app sets, with its value, once per change. */
    private fun dumpValues(key: String, md: MediaMetadata) {
        val line = md.keySet().sorted().joinToString(" | ") { k ->
            val text = md.getString(k)?.takeIf { it.isNotBlank() }
            val num = md.getLong(k).takeIf { it != 0L }
            "$k=${text ?: num ?: "(non-text)"}"
        }
        if (lastNote["values:$key"] != line) {
            lastNote["values:$key"] = line
            ObserverDiagnostics.debugValue(key.substringBefore('/'), "metadata", line)
        }
    }

    private fun toRaw(md: MediaMetadata) = RawMetadata(
        title = md.getString(MediaMetadata.METADATA_KEY_TITLE),
        displayTitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        displaySubtitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
        displayDescription = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION),
        album = md.getString(MediaMetadata.METADATA_KEY_ALBUM),
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST),
        albumArtist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
        durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
        mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
        artUri = md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
        trackNumber = md.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER),
        discNumber = md.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER),
    )

    private fun profileId(): String = data.profiles.activeProfileId.value

    /** One writer, so observations reach the database in the order they happened. */
    private fun startWriter() {
        if (writerStarted) return
        writerStarted = true
        scope.launch {
            runCatching { data.profiles.ensureDefault() }
            for (obs in writes) runCatching { data.history.record(obs) }
        }
    }

    companion object {
        private const val TICK_MS = 15_000L

        @Volatile
        private var instance: MediaSessionObserver? = null

        fun get(context: Context): MediaSessionObserver =
            instance ?: synchronized(this) { instance ?: MediaSessionObserver(context).also { instance = it } }
    }
}
