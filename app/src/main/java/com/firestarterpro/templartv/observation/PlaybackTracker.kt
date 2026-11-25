package com.firestarterpro.templartv.observation

import com.firestarterpro.templartv.data.DedupeKey
import com.firestarterpro.templartv.data.watchhistory.Corroboration
import com.firestarterpro.templartv.data.watchhistory.EvidenceSource
import com.firestarterpro.templartv.data.watchhistory.PlaybackObservation

enum class PlayState(val label: String) {
    PLAYING("playing"), PAUSED("paused"), BUFFERING("buffering"), STOPPED("stopped"),
}

/** One look at one app's media session. Built by [MediaSessionObserver]; plain data so tests can fake it. */
data class SessionSnapshot(
    /** Package plus the session's tag, so two sessions from one app never mix. */
    val sessionKey: String,
    val packageName: String,
    val provider: String,
    val state: PlayState,
    val metadata: RawMetadata?,
    /** Position already adjusted for time passed since the app last reported it. */
    val positionMs: Long,
    val at: Long,
)

/** What the tracker decided. The observer only needs to act on [Record]. */
sealed interface TrackerEvent {
    data class Record(val observation: PlaybackObservation) : TrackerEvent
    /** Ignored. [reason] is a code, never a title. */
    data class Ignored(val sessionKey: String, val reason: String) : TrackerEvent
}

data class TrackerConfig(
    /** A title must play this long before it counts as watched, so autoplay previews do not make shelf tiles. */
    val minPlayMs: Long = 45_000,
    /** Or already be this far in (resuming an episode), which previews never are. */
    val minResumePositionMs: Long = 180_000,
    /** While playing, the saved position is refreshed this often. */
    val heartbeatMs: Long = 60_000,
    /** Gaps longer than this between looks are not counted as playing time. */
    val tickCapMs: Long = 60_000,
    /** A launch from Templar this recent before playback starts is extra evidence. */
    val launchWindowMs: Long = 15 * 60_000,
)

/**
 * Decides when a media session becomes a recorded viewing. Pure: time comes in with each snapshot
 * and the launch lookup is a function, so every rule is tested without Android.
 *
 * Rules:
 * - Ads, the app's own name and very short items are ignored ([MediaMetadataParser]).
 * - A title counts only after [TrackerConfig.minPlayMs] of play, or when it starts far in.
 * - Once counted, the position is saved every heartbeat while playing, on pause or stop, when the
 *   next title starts and when the session ends, so Continue Watching has a real position.
 * - If the app was opened from Templar just before, that is added as weaker supporting evidence.
 */
class PlaybackTracker(
    private val config: TrackerConfig = TrackerConfig(),
    /** Was [packageName] opened from Templar within the window before [at]? */
    private val launchedFromTemplar: (packageName: String, at: Long, windowMs: Long) -> Boolean = { _, _, _ -> false },
) {
    private class Track(
        val key: String,
        val firstSeenAt: Long,
        var lastAt: Long,
        var lastState: PlayState,
        var playedMs: Long = 0,
        var qualified: Boolean = false,
        var lastRecordAt: Long = 0,
        var last: SessionSnapshot,
        var parsed: ParseResult.Parsed,
        var corroborate: Boolean = false,
    )

    private val tracks = HashMap<String, Track>()

    fun onSnapshot(profileId: String, s: SessionSnapshot): List<TrackerEvent> {
        val meta = s.metadata
        if (meta == null) {
            // Peacock clears its metadata the moment it stops, but the stop position is still in
            // the playback state. Keep the last known title and take the newer position from it.
            tracks[s.sessionKey]?.let { t -> if (s.positionMs > 0) t.last = t.last.copy(positionMs = s.positionMs) }
            return finish(profileId, s.sessionKey, s.at, "no metadata")
        }
        val parsed = when (val r = MediaMetadataParser.parse(meta, ProviderCatalog.displayName(s.provider), ProviderCatalog.titleIsEpisodeNameOnly(s.provider))) {
            is ParseResult.Skipped -> {
                // An ad or placeholder in the middle of a show must not end the show's track, and
                // its time must not count as play. The track simply waits for the show to resume.
                tracks[s.sessionKey]?.let { it.lastAt = s.at; it.lastState = PlayState.BUFFERING }
                return listOf(TrackerEvent.Ignored(s.sessionKey, r.reason.name.lowercase()))
            }
            is ParseResult.Parsed -> r
        }
        val key = trackKey(parsed)
        val out = ArrayList<TrackerEvent>(2)
        var track = tracks[s.sessionKey]
        if (track != null && track.key != key) {
            // The next title started: save the last position of the one that ended.
            out += closeOut(profileId, track, s.at)
            tracks.remove(s.sessionKey)
            track = null
        }
        if (track == null) {
            track = Track(
                key = key, firstSeenAt = s.at, lastAt = s.at, lastState = s.state, last = s, parsed = parsed,
                corroborate = launchedFromTemplar(s.packageName, s.at, config.launchWindowMs),
            )
            tracks[s.sessionKey] = track
        }

        if (track.lastState == PlayState.PLAYING && s.state == PlayState.PLAYING) {
            track.playedMs += (s.at - track.lastAt).coerceIn(0, config.tickCapMs)
        }
        val stateChanged = track.lastState != s.state
        track.lastAt = s.at
        track.lastState = s.state
        track.last = s
        track.parsed = parsed

        // Resuming far in counts only while actually playing or paused. A stopped session keeps its
        // old position (Peacock shows the last episode's 43 minutes at idle), which is not a viewing.
        val startsFarIn = (s.state == PlayState.PLAYING || s.state == PlayState.PAUSED) &&
            s.positionMs >= config.minResumePositionMs
        val justQualified = !track.qualified && (track.playedMs >= config.minPlayMs || startsFarIn)
        if (justQualified) track.qualified = true
        if (!track.qualified) {
            out += TrackerEvent.Ignored(s.sessionKey, "waiting: not played long enough yet")
            return out
        }
        val heartbeatDue = s.state == PlayState.PLAYING && s.at - track.lastRecordAt >= config.heartbeatMs
        // A qualified track was already recorded when it qualified, so any state change is a new record.
        if (justQualified || heartbeatDue || stateChanged) {
            out += TrackerEvent.Record(observation(profileId, track, s.at, s.state))
            track.lastRecordAt = s.at
        }
        return out
    }

    /** The session went away (app closed, or no longer in the list). Saves the final position. */
    fun onSessionEnded(profileId: String, sessionKey: String, at: Long): List<TrackerEvent> =
        finish(profileId, sessionKey, at, "session ended")

    /** Keys of sessions currently being followed. */
    fun activeKeys(): Set<String> = tracks.keys.toSet()

    private fun finish(profileId: String, sessionKey: String, at: Long, reason: String): List<TrackerEvent> {
        val track = tracks.remove(sessionKey) ?: return listOf(TrackerEvent.Ignored(sessionKey, reason))
        return closeOut(profileId, track, at) + TrackerEvent.Ignored(sessionKey, reason)
    }

    private fun closeOut(profileId: String, t: Track, at: Long): List<TrackerEvent> =
        if (t.qualified) listOf(TrackerEvent.Record(observation(profileId, t, at, PlayState.STOPPED))) else emptyList()

    private fun observation(profileId: String, t: Track, at: Long, state: PlayState): PlaybackObservation {
        val p = t.parsed
        val s = t.last
        val signal = if (p.confidence >= 1f) null else p.confidence
        return PlaybackObservation(
            profileId = profileId, provider = s.provider, title = p.title,
            source = EvidenceSource.MEDIA_SESSION, observedAt = at,
            packageName = s.packageName, contentType = p.contentType, seriesTitle = p.seriesTitle,
            seasonNumber = p.seasonNumber, episodeNumber = p.episodeNumber, episodeTitle = p.episodeTitle,
            providerContentId = p.providerContentId, positionMs = s.positionMs, runtimeMs = p.runtimeMs,
            playbackState = state.label, sourceConfidence = signal,
            detail = "MediaSession (${p.fields})", artworkRef = p.artworkRef,
            corroboration = if (t.corroborate) {
                listOf(Corroboration(EvidenceSource.TEMPLAR_LAUNCH, LAUNCH_HINT_CONFIDENCE, "opened from Templar"))
            } else emptyList(),
        )
    }

    private fun trackKey(p: ParseResult.Parsed) =
        DedupeKey.normalize(p.title) + "|" + p.seasonNumber + "|" + p.episodeNumber + "|" + DedupeKey.normalize(p.episodeTitle)

    companion object {
        /** Weaker than a MediaSession reading: the app was opened from Templar, not that this title was chosen. */
        const val LAUNCH_HINT_CONFIDENCE = 0.5f
    }
}
