package com.firestarterpro.templartv.data.watchhistory

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.FlagGate
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.data.shelf.ShelfDraft
import com.firestarterpro.templartv.data.shelf.ShelfRepository
import com.firestarterpro.templartv.data.shelf.ShelfSource
import kotlinx.coroutines.flow.Flow

/** What an observer (MediaSession, provider rows, Jellyfin, a Templar launch, ...) saw. */
data class PlaybackObservation(
    val profileId: String,
    val provider: String,
    val title: String,
    val source: EvidenceSource,
    val observedAt: Long,
    val packageName: String = "",
    val contentType: String = "",
    val seriesTitle: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val providerContentId: String = "",
    val tmdbId: Long = 0,
    val imdbId: String = "",
    val jellyfinItemRef: String = "",
    val positionMs: Long = 0,
    val runtimeMs: Long = 0,
    /** "playing", "paused", "stopped", ... as the observer reports it. */
    val playbackState: String = "playing",
    /** Overrides [EvidenceSource.baseConfidence] for this signal, for example a partial title match. */
    val sourceConfidence: Float? = null,
    /** Short detail for the evidence row. Never credentials or tokens. */
    val detail: String = "",
    val artworkRef: String = "",
    val deepLink: String = "",
    /** Weaker supporting signals for the same viewing (for example "opened from Templar"). Stored as evidence. */
    val corroboration: List<Corroboration> = emptyList(),
)

/** A supporting signal that rides along with an observation. [detail] must be stable so repeats are not stored twice. */
data class Corroboration(val source: EvidenceSource, val confidence: Float, val detail: String)

/**
 * Local watch history with evidence and confidence.
 *
 * Observations of the same title within [SESSION_GAP_MS] extend one session. Every distinct signal
 * is kept as evidence; the match confidence is recomputed from all of it. Below
 * [Confidence.CONFIRM_AT] a session waits in the review queue instead of becoming verified history.
 * Only confirmed sessions reach the provider shelves. Nothing is ever deleted.
 */
class WatchHistoryRepository(
    private val db: RoomDatabase,
    private val dao: WatchHistoryDao,
    private val shelves: ShelfRepository? = null,
    private val gate: FlagGate = FlagGate.AllOn,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeRecent(profileId: String, limit: Int = 20): Flow<List<WatchHistoryEntry>> = dao.observeRecent(profileId, limit)
    fun observeByProvider(profileId: String, provider: String, limit: Int = 20): Flow<List<WatchHistoryEntry>> =
        dao.observeByProvider(profileId, provider, limit)
    fun observeReviewQueue(profileId: String): Flow<List<WatchHistoryEntry>> = dao.observeReviewQueue(profileId)
    fun observeRemoved(profileId: String): Flow<List<WatchHistoryEntry>> = dao.observeRemoved(profileId)
    fun observeEvidence(watchId: Long): Flow<List<WatchEvidenceEntity>> = dao.observeEvidence(watchId)
    fun observeCandidates(watchId: Long): Flow<List<MatchCandidateEntity>> = dao.observeCandidates(watchId)

    /** Records one observation. Returns the session id, or null if watch history is off or the title is blank. */
    suspend fun record(obs: PlaybackObservation): Long? {
        if (!gate.isOn(Flag.WATCH_HISTORY) || obs.title.isBlank()) return null
        val signal = (obs.sourceConfidence ?: obs.source.baseConfidence).coerceIn(0f, 1f)
        val watchId = db.withTransaction {
            val open = dao.findOpenSession(
                obs.profileId, obs.provider, obs.title, obs.seasonNumber, obs.episodeNumber,
                obs.observedAt - SESSION_GAP_MS,
            )
            val id = if (open == null) dao.insert(newSession(obs)) else { dao.update(extend(open, obs)); open.id.toLong() }
            if (dao.countEvidence(id, obs.source.name, obs.detail) == 0) {
                dao.insertEvidence(WatchEvidenceEntity(watchId = id, source = obs.source.name, confidence = signal, detail = obs.detail, observedAt = obs.observedAt))
            }
            for (c in obs.corroboration) {
                if (dao.countEvidence(id, c.source.name, c.detail) == 0) {
                    dao.insertEvidence(WatchEvidenceEntity(watchId = id, source = c.source.name, confidence = c.confidence.coerceIn(0f, 1f), detail = c.detail, observedAt = obs.observedAt))
                }
            }
            applyConfidence(id, obs.observedAt)
            id
        }
        putOnShelf(watchId, obs)
        return watchId
    }

    /** The user says this session is the right title. Counts as the strongest evidence. */
    suspend fun confirm(watchId: Long): Boolean {
        if (!gate.isOn(Flag.WATCH_HISTORY)) return false
        val now = clock()
        return db.withTransaction {
            val entry = dao.get(watchId) ?: return@withTransaction false
            dao.insertEvidence(WatchEvidenceEntity(watchId = watchId, source = EvidenceSource.MANUAL.name, confidence = 1f, detail = "confirmed by user", observedAt = now))
            dao.update(entry.copy(manualCorrection = true, updatedTimestamp = now))
            applyConfidence(watchId, now)
            true
        }.also { if (it) putConfirmedOnShelf(watchId) }
    }

    /** The user fixes the title or ids of a session. Also confirms it. */
    suspend fun correct(watchId: Long, title: String, tmdbId: Long = 0, imdbId: String = ""): Boolean {
        if (!gate.isOn(Flag.WATCH_HISTORY) || title.isBlank()) return false
        val now = clock()
        return db.withTransaction {
            val entry = dao.get(watchId) ?: return@withTransaction false
            dao.update(
                entry.copy(
                    canonicalTitle = title.trim(),
                    // The corrected name is the show's name, so the series follows it.
                    seriesTitle = if (entry.seriesTitle.isNotBlank()) title.trim() else entry.seriesTitle,
                    tmdbId = if (tmdbId > 0) tmdbId else entry.tmdbId,
                    imdbId = imdbId.ifBlank { entry.imdbId },
                    manualCorrection = true, updatedTimestamp = now,
                ),
            )
            dao.insertEvidence(WatchEvidenceEntity(watchId = watchId, source = EvidenceSource.MANUAL.name, confidence = 1f, detail = "corrected by user", observedAt = now))
            applyConfidence(watchId, now)
            true
        }.also { if (it) putConfirmedOnShelf(watchId) }
    }

    /** The user says "not a real viewing". Kept (never deleted) but out of the queue and of history. */
    suspend fun dismiss(watchId: Long): Boolean {
        if (!gate.isOn(Flag.WATCH_HISTORY)) return false
        val entry = dao.get(watchId) ?: return false
        dao.setMatch(watchId, ReviewState.DISMISSED, entry.matchConfidence, entry.evidenceSource, clock())
        return true
    }

    suspend fun addCandidate(candidate: MatchCandidateEntity): Long? {
        if (!gate.isOn(Flag.WATCH_HISTORY)) return null
        return dao.insertCandidate(candidate.copy(createdAt = candidate.createdAt.takeIf { it > 0 } ?: clock()))
    }

    /** The user picks one of the candidate matches. That candidate's title and ids replace the session's. */
    suspend fun chooseCandidate(watchId: Long, candidate: MatchCandidateEntity): Boolean {
        if (!gate.isOn(Flag.WATCH_HISTORY) || candidate.watchId != watchId) return false
        db.withTransaction { dao.chooseCandidate(watchId, candidate.id) }
        return correct(watchId, candidate.title, candidate.tmdbId, candidate.imdbId)
    }

    suspend fun remove(watchId: Long): Boolean {
        if (!gate.isOn(Flag.WATCH_HISTORY)) return false
        dao.markRemoved(watchId, clock())
        return true
    }

    suspend fun restore(watchId: Long): Boolean {
        if (!gate.isOn(Flag.WATCH_HISTORY)) return false
        dao.restore(watchId, clock())
        return true
    }

    private suspend fun applyConfidence(watchId: Long, at: Long) {
        val entry = dao.get(watchId) ?: return
        val evidence = dao.evidenceFor(watchId)
        val confidence = Confidence.combine(evidence.map { it.source to it.confidence })
        val strongest = evidence.maxByOrNull { it.confidence }?.source ?: entry.evidenceSource
        val state = if (entry.reviewState == ReviewState.DISMISSED) ReviewState.DISMISSED
        else Confidence.stateFor(confidence, entry.manualCorrection)
        dao.setMatch(watchId, state, confidence, strongest, at)
    }

    /**
     * The user confirmed or corrected a session from the review queue. It now counts as history, so
     * it reaches the provider shelf even though no new observation arrives. The user chose it, so
     * a tile they had removed comes back (MANUAL), as with any manual add.
     */
    private suspend fun putConfirmedOnShelf(watchId: Long) {
        val entry = dao.get(watchId) ?: return
        // The position came from the app or Jellyfin if either ever reported it, whatever else confirmed the title.
        val seen = dao.evidenceFor(watchId).map { it.source }.toSet()
        val reporter = listOf(EvidenceSource.JELLYFIN_EVENT, EvidenceSource.MEDIA_SESSION).firstOrNull { it.name in seen }
        putEntryOnShelf(entry, ShelfSource.MANUAL, deepLink = "", artworkOverride = "", kindSource = reporter)
    }

    private suspend fun putOnShelf(watchId: Long, obs: PlaybackObservation) {
        val entry = dao.get(watchId) ?: return
        val source = when (obs.source) {
            EvidenceSource.TEMPLAR_LAUNCH -> ShelfSource.TEMPLAR_LAUNCH
            EvidenceSource.PROVIDER_ROW -> ShelfSource.PROVIDER_ROW
            EvidenceSource.MANUAL -> ShelfSource.MANUAL
            else -> ShelfSource.MEDIA_SESSION
        }
        putEntryOnShelf(entry, source, obs.deepLink, obs.artworkRef, obs.source)
    }

    private suspend fun putEntryOnShelf(
        entry: WatchHistoryEntry, source: ShelfSource, deepLink: String, artworkOverride: String, kindSource: EvidenceSource?,
    ) {
        val repo = shelves ?: return
        if (entry.reviewState != ReviewState.CONFIRMED || entry.removedAt != null) return
        val watchId = entry.id.toLong()
        repo.record(
            entry.profileId, entry.provider, source,
            ShelfDraft(
                title = entry.canonicalTitle, seriesTitle = entry.seriesTitle, contentType = entry.contentType,
                seasonNumber = entry.seasonNumber, episodeNumber = entry.episodeNumber, episodeTitle = entry.episodeTitle,
                providerContentId = entry.providerContentId, deepLink = deepLink, packageName = entry.packageName,
                tmdbId = entry.tmdbId, imdbId = entry.imdbId, artworkRef = artworkOverride.ifBlank { entry.artworkReference },
                progressMs = entry.playbackPosition, runtimeMs = entry.mediaRuntime,
                progressKind = progressKind(entry.playbackPosition, kindSource ?: strongestSource(entry)), watched = entry.watchedStatus, watchHistoryId = watchId,
            ),
        )
    }

    private fun strongestSource(entry: WatchHistoryEntry): EvidenceSource? =
        EvidenceSource.entries.firstOrNull { it.name == entry.evidenceSource }

    /** A position is exact only when an app (MediaSession) or Jellyfin reported it; otherwise it is Templar's estimate. */
    private fun progressKind(positionMs: Long, source: EvidenceSource?) = when {
        positionMs <= 0 -> ProgressKind.UNKNOWN
        source == EvidenceSource.JELLYFIN_EVENT || source == EvidenceSource.MEDIA_SESSION -> ProgressKind.EXACT
        else -> ProgressKind.ESTIMATED
    }

    private fun newSession(obs: PlaybackObservation): WatchHistoryEntry {
        val completion = completion(obs.positionMs, obs.runtimeMs, 0)
        return WatchHistoryEntry(
            profileId = obs.profileId, provider = obs.provider, packageName = obs.packageName,
            contentType = obs.contentType, canonicalTitle = obs.title, seriesTitle = obs.seriesTitle,
            seasonNumber = obs.seasonNumber, episodeNumber = obs.episodeNumber, episodeTitle = obs.episodeTitle,
            providerContentId = obs.providerContentId, tmdbId = obs.tmdbId, imdbId = obs.imdbId,
            jellyfinItemRef = obs.jellyfinItemRef,
            startTimestamp = obs.observedAt, stopTimestamp = obs.observedAt,
            playbackPosition = obs.positionMs, mediaRuntime = obs.runtimeMs,
            completionPercentage = completion, playbackState = obs.playbackState,
            watchedStatus = completion >= Confidence.WATCHED_AT_PERCENT,
            evidenceSource = obs.source.name, evidenceDetails = obs.detail,
            artworkReference = obs.artworkRef, metadataSource = obs.source.name,
            createdTimestamp = obs.observedAt, updatedTimestamp = obs.observedAt,
            reviewState = ReviewState.NEEDS_REVIEW,
        )
    }

    private fun extend(open: WatchHistoryEntry, obs: PlaybackObservation): WatchHistoryEntry {
        val completion = completion(obs.positionMs, obs.runtimeMs, open.completionPercentage)
        val tick = (obs.observedAt - open.stopTimestamp).coerceIn(0L, TICK_CAP_MS)
        return open.copy(
            stopTimestamp = maxOf(open.stopTimestamp, obs.observedAt),
            durationWatched = open.durationWatched + if (obs.playbackState == "playing") tick else 0L,
            playbackPosition = if (obs.positionMs > 0) obs.positionMs else open.playbackPosition,
            mediaRuntime = if (obs.runtimeMs > 0) obs.runtimeMs else open.mediaRuntime,
            completionPercentage = completion,
            playbackState = obs.playbackState,
            watchedStatus = open.watchedStatus || completion >= Confidence.WATCHED_AT_PERCENT,
            tmdbId = if (obs.tmdbId > 0) obs.tmdbId else open.tmdbId,
            imdbId = obs.imdbId.ifBlank { open.imdbId },
            jellyfinItemRef = obs.jellyfinItemRef.ifBlank { open.jellyfinItemRef },
            updatedTimestamp = obs.observedAt,
        )
    }

    private fun completion(position: Long, runtime: Long, previous: Int): Int =
        if (runtime > 0 && position > 0) maxOf(previous, ((position * 100) / runtime).toInt().coerceIn(0, 100)) else previous

    companion object {
        /** Observations of the same title closer together than this are one sitting. */
        const val SESSION_GAP_MS = 30 * 60 * 1000L
        private const val TICK_CAP_MS = 60 * 1000L
    }
}
