package com.firestarterpro.templartv.data.favourites

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.util.UUID
import com.firestarterpro.templartv.data.profile.ProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * A favourite show. Separate from favourite apps and from watch history. The user orders the list;
 * removed favourites stay in the table with [removedAt] set and can be restored.
 */
@Entity(
    tableName = "favourite_shows",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "dedupe_key"], unique = true, name = "index_favourite_shows_profile_id_dedupe_key"),
        Index(value = ["sync_id"], unique = true, name = "index_favourite_shows_sync_id"),
        Index(value = ["profile_id", "updated_at"], name = "index_favourite_shows_profile_id_updated_at"),
    ],
)
data class FavouriteShow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id that never changes and is the same on every device. External clients use this, not [id]. */
    @ColumnInfo(name = "sync_id") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    val title: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Long = 0,
    @ColumnInfo(name = "imdb_id") val imdbId: String = "",
    @ColumnInfo(name = "tvdb_id") val tvdbId: String = "",
    /** Provider to open first, or empty to let search rank subscribed providers. */
    @ColumnInfo(name = "preferred_provider") val preferredProvider: String = "",
    @ColumnInfo(name = "artwork_ref") val artworkRef: String = "",
    val position: Int = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    @ColumnInfo(name = "removed_at") val removedAt: Long? = null,
)

@Dao
interface FavouriteShowDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(show: FavouriteShow): Long

    @Update
    suspend fun update(show: FavouriteShow)

    @Query("SELECT * FROM favourite_shows WHERE profile_id = :profileId AND dedupe_key = :key")
    suspend fun findByKey(profileId: String, key: String): FavouriteShow?

    @Query("SELECT MIN(position) FROM favourite_shows WHERE profile_id = :profileId")
    suspend fun minPosition(profileId: String): Int?

    @Query("SELECT * FROM favourite_shows WHERE profile_id = :profileId AND removed_at IS NULL ORDER BY position ASC, id DESC")
    fun observe(profileId: String): Flow<List<FavouriteShow>>

    @Query("SELECT * FROM favourite_shows WHERE profile_id = :profileId AND removed_at IS NULL ORDER BY position ASC, id DESC")
    suspend fun current(profileId: String): List<FavouriteShow>

    @Query("SELECT * FROM favourite_shows WHERE profile_id = :profileId AND removed_at IS NOT NULL ORDER BY removed_at DESC")
    fun observeRemoved(profileId: String): Flow<List<FavouriteShow>>

    @Query("UPDATE favourite_shows SET position = :position, updated_at = :at WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int, at: Long)

    @Query("UPDATE favourite_shows SET removed_at = :at, updated_at = :at WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    @Query("UPDATE favourite_shows SET removed_at = NULL, updated_at = :at WHERE id = :id")
    suspend fun restore(id: Long, at: Long)

    @Query("UPDATE favourite_shows SET preferred_provider = :provider, updated_at = :at WHERE id = :id")
    suspend fun setPreferredProvider(id: Long, provider: String, at: Long)

    @Query("SELECT * FROM favourite_shows WHERE id = :id")
    suspend fun getById(id: Long): FavouriteShow?

    @Query("SELECT * FROM favourite_shows WHERE sync_id = :syncId")
    suspend fun getBySync(syncId: String): FavouriteShow?

    /** Everything that changed at or after [since], removed shows included, for external sync. */
    @Query("SELECT * FROM favourite_shows WHERE profile_id = :profileId AND updated_at >= :since ORDER BY updated_at ASC, id ASC")
    suspend fun changedSince(profileId: String, since: Long): List<FavouriteShow>
}
