package com.firestarterpro.templartv.data.layout

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.firestarterpro.templartv.data.ProviderIds
import com.firestarterpro.templartv.data.profile.ProfileEntity
import kotlinx.coroutines.flow.Flow

enum class RowKind {
    CONTINUE_WATCHING, FAVOURITE_SHOWS, RECENTLY_ADDED, PROVIDER_SHELF, PROVIDER_ROWS,
    PERSONAL_MEDIA, PERSONAL_MUSIC, NEW_UPCOMING, LEAVING_SOON, REVIEW, APPS, CUSTOM,
}

enum class TileSize { SMALL, MEDIUM, LARGE }

/**
 * One home row for one profile: its order, whether it shows, and its options. Every home view
 * (rows, columns, rail and grid, ...) reads this same table, so switching view never loses an edit.
 * A removed row is just a hidden one; the Row Manager can show it again.
 */
@Entity(
    tableName = "row_layout",
    primaryKeys = ["profile_id", "row_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RowLayoutEntry(
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    val kind: RowKind,
    /** Provider id for PROVIDER_SHELF and PROVIDER_ROWS rows, otherwise empty. */
    val provider: String = "",
    /** User's own name for the row, or empty for the built-in name. */
    @ColumnInfo(name = "title_override") val titleOverride: String = "",
    val position: Int = 0,
    val visible: Boolean = true,
    @ColumnInfo(name = "tile_size") val tileSize: TileSize = TileSize.MEDIUM,
    /** Extra per-row options as JSON text, for example {"inline_provider_rows":true}. */
    @ColumnInfo(name = "options_json") val optionsJson: String = "",
    /** Last change, in ms. Used by external sync; [profileId] + [rowId] is the stable id. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

@Dao
interface RowLayoutDao {

    /** Adds a row only if the profile does not already have it, so user edits are never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(rows: List<RowLayoutEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: RowLayoutEntry)

    @Query("SELECT * FROM row_layout WHERE profile_id = :profileId ORDER BY position ASC, row_id ASC")
    fun observe(profileId: String): Flow<List<RowLayoutEntry>>

    @Query("SELECT * FROM row_layout WHERE profile_id = :profileId ORDER BY position ASC, row_id ASC")
    suspend fun all(profileId: String): List<RowLayoutEntry>

    @Query("SELECT * FROM row_layout WHERE profile_id = :profileId AND row_id = :rowId")
    suspend fun get(profileId: String, rowId: String): RowLayoutEntry?

    @Query("UPDATE row_layout SET position = :position, updated_at = :at WHERE profile_id = :profileId AND row_id = :rowId")
    suspend fun setPosition(profileId: String, rowId: String, position: Int, at: Long)

    @Query("SELECT * FROM row_layout WHERE profile_id = :profileId AND updated_at >= :since ORDER BY updated_at ASC, row_id ASC")
    suspend fun changedSince(profileId: String, since: Long): List<RowLayoutEntry>
}

/** The default home rows, in the order set by the plan and the design review. */
object DefaultRows {

    const val CONTINUE_WATCHING = "continue_watching"
    const val FAVOURITE_SHOWS = "favourite_shows"
    const val RECENTLY_ADDED = "recently_added"
    const val PERSONAL_MEDIA = "personal_media"
    const val PERSONAL_MUSIC = "personal_music"
    const val NEW_UPCOMING = "new_upcoming"
    const val LEAVING_SOON = "leaving_soon"
    const val REVIEW = "review"
    const val APPS = "apps"

    fun shelfRowId(provider: String) = "shelf:$provider"
    fun providerRowsId(provider: String) = "provider_rows:$provider"

    /** One shelf per provider. The provider's own rows are optional extra rows, off by default. */
    fun forProfile(profileId: String): List<RowLayoutEntry> {
        fun row(id: String, kind: RowKind, provider: String = "", visible: Boolean = true, size: TileSize = TileSize.MEDIUM) =
            RowLayoutEntry(profileId, id, kind, provider = provider, visible = visible, tileSize = size)

        val ordered = listOf(
            row(APPS, RowKind.APPS),
            row(CONTINUE_WATCHING, RowKind.CONTINUE_WATCHING, size = TileSize.LARGE),
            row(FAVOURITE_SHOWS, RowKind.FAVOURITE_SHOWS, size = TileSize.LARGE),
            row(RECENTLY_ADDED, RowKind.RECENTLY_ADDED),
            row(shelfRowId(ProviderIds.PEACOCK), RowKind.PROVIDER_SHELF, ProviderIds.PEACOCK),
            row(shelfRowId(ProviderIds.PARAMOUNT_PLUS), RowKind.PROVIDER_SHELF, ProviderIds.PARAMOUNT_PLUS),
            row(shelfRowId(ProviderIds.PRIME_VIDEO), RowKind.PROVIDER_SHELF, ProviderIds.PRIME_VIDEO),
            row(PERSONAL_MEDIA, RowKind.PERSONAL_MEDIA),
            row(PERSONAL_MUSIC, RowKind.PERSONAL_MUSIC),
            row(shelfRowId(ProviderIds.MAX), RowKind.PROVIDER_SHELF, ProviderIds.MAX),
            row(shelfRowId(ProviderIds.NETFLIX), RowKind.PROVIDER_SHELF, ProviderIds.NETFLIX, visible = false),
            row(shelfRowId(ProviderIds.HULU), RowKind.PROVIDER_SHELF, ProviderIds.HULU, visible = false),
            row(NEW_UPCOMING, RowKind.NEW_UPCOMING),
            row(LEAVING_SOON, RowKind.LEAVING_SOON),
            row(REVIEW, RowKind.REVIEW),
        )
        return ordered.mapIndexed { index, r -> r.copy(position = index) }
    }
}
