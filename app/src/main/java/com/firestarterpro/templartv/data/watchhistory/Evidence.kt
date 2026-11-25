package com.firestarterpro.templartv.data.watchhistory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a title match stands. Only CONFIRMED is treated as verified history. */
enum class ReviewState { CONFIRMED, NEEDS_REVIEW, DISMISSED }

/**
 * Where a piece of evidence came from. [baseConfidence] is the starting trust for that source and
 * can be overridden per observation. Order follows the plan: exact server events, MediaSession,
 * provider rows, Templar's own launch, focused tile, playback controls, OCR (opt-in), pattern
 * matching. MANUAL is the user's own word and always wins.
 */
enum class EvidenceSource(val baseConfidence: Float) {
    JELLYFIN_EVENT(0.98f),
    MEDIA_SESSION(0.90f),
    PROVIDER_ROW(0.85f),
    TEMPLAR_LAUNCH(0.85f),
    FOCUSED_TILE(0.60f),
    PLAYBACK_CONTROLS(0.60f),
    OCR(0.55f),
    PATTERN(0.35f),
    MANUAL(1.0f),
}

/** One signal that supports the match for a watch-history session. */
@Entity(
    tableName = "watch_evidence",
    foreignKeys = [
        ForeignKey(
            entity = WatchHistoryEntry::class,
            parentColumns = ["id"],
            childColumns = ["watch_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["watch_id"], name = "index_watch_evidence_watch_id")],
)
data class WatchEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "watch_id") val watchId: Long,
    /** [EvidenceSource] name. Stored as text so a newer source never breaks an older reader. */
    val source: String,
    /** How much this one signal is trusted, 0.0 to 1.0. */
    val confidence: Float,
    /** Short human-readable detail, for example the MediaSession title. Never credentials. */
    val detail: String = "",
    @ColumnInfo(name = "observed_at") val observedAt: Long,
)

/** A possible title for a session, kept instead of forcing an uncertain match. */
@Entity(
    tableName = "match_candidates",
    foreignKeys = [
        ForeignKey(
            entity = WatchHistoryEntry::class,
            parentColumns = ["id"],
            childColumns = ["watch_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["watch_id"], name = "index_match_candidates_watch_id")],
)
data class MatchCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "watch_id") val watchId: Long,
    val title: String,
    @ColumnInfo(name = "content_type") val contentType: String = "",
    @ColumnInfo(name = "tmdb_id") val tmdbId: Long = 0,
    @ColumnInfo(name = "imdb_id") val imdbId: String = "",
    val confidence: Float = 0f,
    val source: String = "",
    /** True for the candidate the user (or a confirmed match) picked. */
    val chosen: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
)

/** Combines evidence into one score. Pure, so it is unit-tested without a database. */
object Confidence {

    /** At or above this a session counts as confirmed history. Below it, it goes to review. */
    const val CONFIRM_AT = 0.80f

    /** A session is "watched" at or above this completion percentage. */
    const val WATCHED_AT_PERCENT = 90

    /**
     * Strongest signal per source first (repeated ticks of the same MediaSession are one voice, not
     * many), then a noisy-OR across different sources, so independent agreement raises confidence
     * and one weak source never reaches certainty alone. Any MANUAL signal at 1.0 settles it.
     */
    fun combine(signals: List<Pair<String, Float>>): Float {
        if (signals.isEmpty()) return 0f
        val bestPerSource = signals.groupBy({ it.first }, { it.second.coerceIn(0f, 1f) })
            .mapValues { (_, v) -> v.max() }
        if (bestPerSource[EvidenceSource.MANUAL.name] == 1.0f) return 1.0f
        var miss = 1.0
        for (c in bestPerSource.values) miss *= (1.0 - c)
        return (1.0 - miss).toFloat().coerceIn(0f, 1f)
    }

    fun stateFor(confidence: Float, manuallyConfirmed: Boolean = false): ReviewState =
        if (manuallyConfirmed || confidence >= CONFIRM_AT) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW
}
