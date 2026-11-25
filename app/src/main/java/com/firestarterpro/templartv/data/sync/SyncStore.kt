package com.firestarterpro.templartv.data.sync

/**
 * What an outside client (the Templar Tablet companion server) is allowed to do to the TV's data.
 * Plain Kotlin types only: no Room, no Android. The companion API codes against this interface and
 * [TemplarSyncStore] is the real implementation.
 *
 * Rules every method follows:
 *  - The TV is the authority. It stamps `updatedAt` with its own clock; clients never send one.
 *    The last write the TV receives wins. Calls are idempotent, so a client can safely retry.
 *  - Ids are stable strings: `syncId` (a UUID the client may pick for a new item) or `rowId`.
 *  - Nothing is hard-deleted. `softDelete*` sets `removedAt` and the item appears in "Recently
 *    removed" on the TV; `restore*` clears it. Removed items come back from `*Since` with `removedAt` set,
 *    so a client learns about removals.
 *  - `*Since(profileId, since)` returns everything with `updatedAt >= since` (ms), oldest first.
 *    Use [SyncChanges.asOf] as the next `since`. Re-seeing an item is harmless: apply by id.
 *  - Order is an explicit integer `position`, lower first (pinned shelf entries show before unpinned).
 *    Clients change order only with `reorder*`, sending ids in the wanted order. Ids left out keep
 *    their relative order after the listed ones.
 *  - A write the TV refuses (feature flag off, unknown id, blank title) returns null or false.
 */
interface SyncStore {

    suspend fun profiles(): List<SyncProfile>

    // Favourite shows
    suspend fun favouritesSince(profileId: String, since: Long): SyncChanges<SyncFavourite>
    /** Creates the favourite (using [SyncFavourite.syncId] if new) or updates its title, ids, provider and artwork. */
    suspend fun upsertFavourite(favourite: SyncFavourite): SyncFavourite?
    suspend fun reorderFavourites(profileId: String, orderedSyncIds: List<String>): Boolean
    suspend fun softDeleteFavourite(syncId: String): Boolean
    suspend fun restoreFavourite(syncId: String): Boolean

    // Shelf entries (the tiles on a provider's "recently played" shelf)
    suspend fun shelfEntriesSince(profileId: String, since: Long): SyncChanges<SyncShelfEntry>
    /**
     * Creates the entry on [SyncShelfEntry.provider]'s shelf (source MANUAL) or updates an existing one:
     * title, links, artwork, pinned, hidden, watched, and the provider (which moves it to that shelf).
     */
    suspend fun upsertShelfEntry(entry: SyncShelfEntry): SyncShelfEntry?
    suspend fun reorderShelf(profileId: String, provider: String, orderedSyncIds: List<String>): Boolean
    suspend fun softDeleteShelfEntry(syncId: String): Boolean
    suspend fun restoreShelfEntry(syncId: String): Boolean

    // Home rows (a row is also a "shelf" in the Row Manager). Removing a row hides it.
    suspend fun rowsSince(profileId: String, since: Long): SyncChanges<SyncRow>
    /** Changes visible, title and tile size of an existing row. Order only through [reorderRows]. */
    suspend fun upsertRow(row: SyncRow): SyncRow?
    suspend fun reorderRows(profileId: String, orderedRowIds: List<String>): Boolean
}

/** [asOf] is the TV's clock when the read started. Pass it back as the next `since`. */
data class SyncChanges<T>(val items: List<T>, val asOf: Long)

data class SyncProfile(val id: String, val name: String, val archived: Boolean)

data class SyncFavourite(
    val syncId: String,
    val profileId: String,
    val title: String,
    val tmdbId: Long = 0,
    val imdbId: String = "",
    val tvdbId: String = "",
    /** Provider id (see ProviderIds) or empty. */
    val preferredProvider: String = "",
    /** Poster URL or reference. The TV stores the reference only, never the image. */
    val artworkRef: String = "",
    /** Read-only for clients; set the order with reorderFavourites. */
    val position: Int = 0,
    val updatedAt: Long = 0,
    val removedAt: Long? = null,
)

/**
 * [provider] is a provider id (peacock, paramount_plus, prime_video, max, netflix, hulu) or "jellyfin".
 * For a Jellyfin item put its item id in [externalId] with provider "jellyfin"; for a provider title put
 * the provider's own content id there. [source] says who put it there: MANUAL for a client pin,
 * PROVIDER_ROW, MEDIA_SESSION or TEMPLAR_LAUNCH for what the TV observed.
 */
data class SyncShelfEntry(
    val syncId: String,
    val profileId: String,
    val provider: String,
    val title: String,
    val seriesTitle: String = "",
    val contentType: String = "",
    val externalId: String = "",
    val tmdbId: Long = 0,
    val imdbId: String = "",
    val deepLink: String = "",
    val artworkRef: String = "",
    val source: String = "MANUAL",
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val watched: Boolean = false,
    val position: Int = 0,
    val updatedAt: Long = 0,
    val removedAt: Long? = null,
)

data class SyncRow(
    val profileId: String,
    /** Stable row id, for example "continue_watching" or "shelf:peacock". */
    val rowId: String,
    val kind: String,
    val provider: String = "",
    val titleOverride: String = "",
    val visible: Boolean = true,
    /** SMALL, MEDIUM or LARGE. */
    val tileSize: String = "MEDIUM",
    val position: Int = 0,
    val updatedAt: Long = 0,
)
