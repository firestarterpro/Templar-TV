package com.firestarterpro.templartv.data.watchhistory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Nothing here deletes history. "Remove" sets [WatchHistoryEntry.removedAt] and can be undone.
 * Every list is scoped to a profile so histories never mix.
 */
@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WatchHistoryEntry): Long

    @Update
    suspend fun update(entry: WatchHistoryEntry)

    @Query("SELECT * FROM watch_history WHERE id = :id")
    suspend fun get(id: Long): WatchHistoryEntry?

    /** The latest session of the same title that ended recently enough to be the same sitting. */
    @Query(
        """SELECT * FROM watch_history
           WHERE profile_id = :profileId AND provider = :provider AND canonical_title = :title
             AND season_number = :season AND episode_number = :episode
             AND removed_at IS NULL AND stop_timestamp >= :since
           ORDER BY stop_timestamp DESC LIMIT 1""",
    )
    suspend fun findOpenSession(
        profileId: String, provider: String, title: String, season: Int, episode: Int, since: Long,
    ): WatchHistoryEntry?

    @Query(
        """SELECT * FROM watch_history
           WHERE profile_id = :profileId AND removed_at IS NULL AND review_state = 'CONFIRMED'
           ORDER BY stop_timestamp DESC LIMIT :limit""",
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<WatchHistoryEntry>>

    @Query(
        """SELECT * FROM watch_history
           WHERE profile_id = :profileId AND provider = :provider AND removed_at IS NULL
             AND review_state = 'CONFIRMED'
           ORDER BY stop_timestamp DESC LIMIT :limit""",
    )
    fun observeByProvider(profileId: String, provider: String, limit: Int): Flow<List<WatchHistoryEntry>>

    /** Sessions that need a person to look at them. */
    @Query(
        """SELECT * FROM watch_history
           WHERE profile_id = :profileId AND removed_at IS NULL AND review_state = 'NEEDS_REVIEW'
           ORDER BY stop_timestamp DESC""",
    )
    fun observeReviewQueue(profileId: String): Flow<List<WatchHistoryEntry>>

    @Query(
        """SELECT * FROM watch_history
           WHERE profile_id = :profileId AND removed_at IS NOT NULL
           ORDER BY removed_at DESC""",
    )
    fun observeRemoved(profileId: String): Flow<List<WatchHistoryEntry>>

    @Query("UPDATE watch_history SET removed_at = :at, updated_timestamp = :at WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    @Query("UPDATE watch_history SET removed_at = NULL, updated_timestamp = :at WHERE id = :id")
    suspend fun restore(id: Long, at: Long)

    @Query(
        """UPDATE watch_history SET review_state = :state, match_confidence = :confidence,
           evidence_source = :source, updated_timestamp = :at WHERE id = :id""",
    )
    suspend fun setMatch(id: Long, state: ReviewState, confidence: Float, source: String, at: Long)

    // Evidence and candidates

    @Insert
    suspend fun insertEvidence(evidence: WatchEvidenceEntity): Long

    @Query("SELECT * FROM watch_evidence WHERE watch_id = :watchId ORDER BY observed_at ASC, id ASC")
    suspend fun evidenceFor(watchId: Long): List<WatchEvidenceEntity>

    @Query("SELECT * FROM watch_evidence WHERE watch_id = :watchId ORDER BY observed_at ASC, id ASC")
    fun observeEvidence(watchId: Long): Flow<List<WatchEvidenceEntity>>

    @Query("SELECT COUNT(*) FROM watch_evidence WHERE watch_id = :watchId AND source = :source AND detail = :detail")
    suspend fun countEvidence(watchId: Long, source: String, detail: String): Int

    @Insert
    suspend fun insertCandidate(candidate: MatchCandidateEntity): Long

    @Query("SELECT * FROM match_candidates WHERE watch_id = :watchId ORDER BY confidence DESC, id ASC")
    fun observeCandidates(watchId: Long): Flow<List<MatchCandidateEntity>>

    @Query("UPDATE match_candidates SET chosen = (id = :candidateId) WHERE watch_id = :watchId")
    suspend fun chooseCandidate(watchId: Long, candidateId: Long)
}
