package com.firestarterpro.templartv.data.shelf

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import com.firestarterpro.templartv.data.profile.ProfileEntity
import com.firestarterpro.templartv.data.watchhistory.WatchHistoryEntry

/** Who put an item on a shelf. A MANUAL add is the user's own statement and counts as confirmed. */
enum class ShelfSource { PROVIDER_ROW, MEDIA_SESSION, TEMPLAR_LAUNCH, MANUAL }

/** How far to trust [ShelfEntry.progressMs]: provider-reported, Templar's estimate, or not known. */
enum class ProgressKind { EXACT, ESTIMATED, UNKNOWN }

/**
 * One tile on a provider's "recently played" shelf. One tile per series (see DedupeKey). The user
 * can reorder, pin, hide, mark watched, change provider and remove it; removed tiles stay in the
 * table with [removedAt] set so they can be restored.
 */
@Entity(
    tableName = "shelf_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WatchHistoryEntry::class,
            parentColumns = ["id"],
            childColumns = ["watch_history_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "provider", "dedupe_key"], unique = true, name = "index_shelf_entries_profile_id_provider_dedupe_key"),
        Index(value = ["watch_history_id"], name = "index_shelf_entries_watch_history_id"),
        Index(value = ["sync_id"], unique = true, name = "index_shelf_entries_sync_id"),
        Index(value = ["profile_id", "updated_at"], name = "index_shelf_entries_profile_id_updated_at"),
    ],
)
data class ShelfEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id that never changes and is the same on every device. External clients use this, not [id]. */
    @ColumnInfo(name = "sync_id") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "profile_id") val profileId: String,
    val provider: String,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    val title: String,
    @ColumnInfo(name = "series_title") val seriesTitle: String = "",
    @ColumnInfo(name = "content_type") val contentType: String = "",
    @ColumnInfo(name = "season_number") val seasonNumber: Int = 0,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int = 0,
    @ColumnInfo(name = "episode_title") val episodeTitle: String = "",
    @ColumnInfo(name = "provider_content_id") val providerContentId: String = "",
    /** A deep link Templar may open; empty means open the provider app. */
    @ColumnInfo(name = "deep_link") val deepLink: String = "",
    @ColumnInfo(name = "package_name") val packageName: String = "",
    @ColumnInfo(name = "tmdb_id") val tmdbId: Long = 0,
    @ColumnInfo(name = "imdb_id") val imdbId: String = "",
    @ColumnInfo(name = "artwork_ref") val artworkRef: String = "",
    @ColumnInfo(name = "progress_ms") val progressMs: Long = 0,
    @ColumnInfo(name = "runtime_ms") val runtimeMs: Long = 0,
    @ColumnInfo(name = "progress_kind") val progressKind: ProgressKind = ProgressKind.UNKNOWN,
    val source: ShelfSource,
    /** The history session this tile came from, if any. Cleared (not deleted) if that row goes. */
    @ColumnInfo(name = "watch_history_id") val watchHistoryId: Long? = null,
    /** Lower is first. Pinned tiles always show before unpinned ones. */
    val position: Int = 0,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val watched: Boolean = false,
    @ColumnInfo(name = "user_confirmed") val userConfirmed: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    @ColumnInfo(name = "removed_at") val removedAt: Long? = null,
)
