package com.firestarterpro.templartv.data.shelf

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ShelfEntry): Long

    @Update
    suspend fun update(entry: ShelfEntry)

    @Query("SELECT * FROM shelf_entries WHERE id = :id")
    suspend fun get(id: Long): ShelfEntry?

    @Query("SELECT * FROM shelf_entries WHERE profile_id = :profileId AND provider = :provider AND dedupe_key = :key")
    suspend fun findByKey(profileId: String, provider: String, key: String): ShelfEntry?

    @Query("SELECT MIN(position) FROM shelf_entries WHERE profile_id = :profileId AND provider = :provider")
    suspend fun minPosition(profileId: String, provider: String): Int?

    /** What a shelf shows: not removed, not hidden, pinned first, then the user's order. */
    @Query(
        """SELECT * FROM shelf_entries
           WHERE profile_id = :profileId AND provider = :provider AND removed_at IS NULL AND hidden = 0
           ORDER BY pinned DESC, position ASC, id DESC LIMIT :limit""",
    )
    fun observeShelf(profileId: String, provider: String, limit: Int): Flow<List<ShelfEntry>>

    @Query(
        """SELECT * FROM shelf_entries
           WHERE profile_id = :profileId AND provider = :provider AND removed_at IS NULL AND hidden = 0
           ORDER BY pinned DESC, position ASC, id DESC""",
    )
    suspend fun visible(profileId: String, provider: String): List<ShelfEntry>

    /** Everything not removed, including hidden tiles, for the shelf editor. */
    @Query(
        """SELECT * FROM shelf_entries
           WHERE profile_id = :profileId AND provider = :provider AND removed_at IS NULL
           ORDER BY pinned DESC, position ASC, id DESC""",
    )
    fun observeEditable(profileId: String, provider: String): Flow<List<ShelfEntry>>

    /** For Continue Watching across providers: unfinished, shown, most recent first. */
    @Query(
        """SELECT * FROM shelf_entries
           WHERE profile_id = :profileId AND removed_at IS NULL AND hidden = 0 AND watched = 0 AND progress_ms > 0
           ORDER BY updated_at DESC LIMIT :limit""",
    )
    fun observeContinue(profileId: String, limit: Int): Flow<List<ShelfEntry>>

    @Query("SELECT * FROM shelf_entries WHERE profile_id = :profileId AND removed_at IS NOT NULL ORDER BY removed_at DESC")
    fun observeRemoved(profileId: String): Flow<List<ShelfEntry>>

    @Query("UPDATE shelf_entries SET position = :position, updated_at = :at WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int, at: Long)

    @Query("SELECT * FROM shelf_entries WHERE sync_id = :syncId")
    suspend fun getBySync(syncId: String): ShelfEntry?

    /** Everything that changed at or after [since], removed tiles included, for external sync. */
    @Query("SELECT * FROM shelf_entries WHERE profile_id = :profileId AND updated_at >= :since ORDER BY updated_at ASC, id ASC")
    suspend fun changedSince(profileId: String, since: Long): List<ShelfEntry>

    @Query("UPDATE shelf_entries SET hidden = :hidden, updated_at = :at WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean, at: Long)

    @Query("UPDATE shelf_entries SET pinned = :pinned, updated_at = :at WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, at: Long)

    @Query("UPDATE shelf_entries SET watched = :watched, updated_at = :at WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean, at: Long)

    @Query("UPDATE shelf_entries SET removed_at = :at, updated_at = :at WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    @Query("UPDATE shelf_entries SET removed_at = NULL, updated_at = :at WHERE id = :id")
    suspend fun restore(id: Long, at: Long)

    @Query("UPDATE shelf_entries SET provider = :provider, updated_at = :at WHERE id = :id")
    suspend fun setProvider(id: Long, provider: String, at: Long)
}
