package com.firestarterpro.templartv.data.sync

import com.firestarterpro.templartv.data.favourites.FavouriteShow
import com.firestarterpro.templartv.data.favourites.FavouriteShowRepository
import com.firestarterpro.templartv.data.layout.RowLayoutEntry
import com.firestarterpro.templartv.data.layout.RowLayoutRepository
import com.firestarterpro.templartv.data.layout.TileSize
import com.firestarterpro.templartv.data.profile.ProfileDao
import com.firestarterpro.templartv.data.shelf.ShelfDraft
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.data.shelf.ShelfRepository
import kotlinx.coroutines.flow.first

/** The real [SyncStore]: every write goes through the same repositories (and feature flags) as the TV's own UI. */
class TemplarSyncStore(
    private val profileDao: ProfileDao,
    private val favourites: FavouriteShowRepository,
    private val shelves: ShelfRepository,
    private val rows: RowLayoutRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : SyncStore {

    override suspend fun profiles(): List<SyncProfile> =
        profileDao.observeActive().first().map { SyncProfile(it.id, it.name, it.archived) }

    // Favourites

    override suspend fun favouritesSince(profileId: String, since: Long): SyncChanges<SyncFavourite> {
        val asOf = clock()
        return SyncChanges(favourites.changedSince(profileId, since).map { it.toSync() }, asOf)
    }

    override suspend fun upsertFavourite(favourite: SyncFavourite): SyncFavourite? {
        val existing = favourites.getBySyncId(favourite.syncId)
        val id = if (existing == null) {
            favourites.add(
                favourite.profileId, favourite.title, favourite.tmdbId, favourite.imdbId, favourite.tvdbId,
                favourite.preferredProvider, favourite.artworkRef, favourite.syncId,
            ) ?: return null
        } else {
            if (existing.profileId != favourite.profileId) return null
            val ok = favourites.updateDetails(
                existing.id, favourite.title, favourite.tmdbId, favourite.imdbId, favourite.tvdbId,
                favourite.preferredProvider, favourite.artworkRef,
            )
            if (!ok) return null
            existing.id
        }
        return favourites.getById(id)?.toSync()
    }

    override suspend fun reorderFavourites(profileId: String, orderedSyncIds: List<String>) =
        favourites.reorder(profileId, orderedSyncIds)

    override suspend fun softDeleteFavourite(syncId: String): Boolean =
        favourites.getBySyncId(syncId)?.let { favourites.remove(it.id) } ?: false

    override suspend fun restoreFavourite(syncId: String): Boolean =
        favourites.getBySyncId(syncId)?.let { favourites.restore(it.id) } ?: false

    // Shelf entries

    override suspend fun shelfEntriesSince(profileId: String, since: Long): SyncChanges<SyncShelfEntry> {
        val asOf = clock()
        return SyncChanges(shelves.changedSince(profileId, since).map { it.toSync() }, asOf)
    }

    override suspend fun upsertShelfEntry(entry: SyncShelfEntry): SyncShelfEntry? {
        val draft = ShelfDraft(
            title = entry.title, seriesTitle = entry.seriesTitle, contentType = entry.contentType,
            providerContentId = entry.externalId, deepLink = entry.deepLink, tmdbId = entry.tmdbId,
            imdbId = entry.imdbId, artworkRef = entry.artworkRef,
        )
        val existing = shelves.getBySyncId(entry.syncId)
        var id: Long
        if (existing == null) {
            id = shelves.addManual(entry.profileId, entry.provider, draft, entry.syncId) ?: return null
        } else {
            if (existing.profileId != entry.profileId) return null
            if (!shelves.updateDetails(existing.id, draft)) return null
            id = existing.id
            if (existing.provider != entry.provider) id = shelves.changeProvider(existing.id, entry.provider) ?: return null
        }
        val now = shelves.getById(id) ?: return null
        if (now.pinned != entry.pinned) shelves.setPinned(id, entry.pinned)
        if (now.hidden != entry.hidden) shelves.setHidden(id, entry.hidden)
        if (now.watched != entry.watched) shelves.setWatched(id, entry.watched)
        return shelves.getById(id)?.toSync()
    }

    override suspend fun reorderShelf(profileId: String, provider: String, orderedSyncIds: List<String>) =
        shelves.reorder(profileId, provider, orderedSyncIds)

    override suspend fun softDeleteShelfEntry(syncId: String): Boolean =
        shelves.getBySyncId(syncId)?.let { shelves.remove(it.id) } ?: false

    override suspend fun restoreShelfEntry(syncId: String): Boolean =
        shelves.getBySyncId(syncId)?.let { shelves.restore(it.id) } ?: false

    // Rows

    override suspend fun rowsSince(profileId: String, since: Long): SyncChanges<SyncRow> {
        val asOf = clock()
        return SyncChanges(rows.changedSince(profileId, since).map { it.toSync() }, asOf)
    }

    override suspend fun upsertRow(row: SyncRow): SyncRow? {
        val existing = rows.get(row.profileId, row.rowId) ?: return null
        val size = runCatching { TileSize.valueOf(row.tileSize) }.getOrDefault(existing.tileSize)
        if (existing.visible != row.visible && !rows.setVisible(row.profileId, row.rowId, row.visible)) return null
        if (existing.titleOverride != row.titleOverride && !rows.rename(row.profileId, row.rowId, row.titleOverride)) return null
        if (existing.tileSize != size && !rows.setTileSize(row.profileId, row.rowId, size)) return null
        return rows.get(row.profileId, row.rowId)?.toSync()
    }

    override suspend fun reorderRows(profileId: String, orderedRowIds: List<String>) =
        rows.reorder(profileId, orderedRowIds)

    private fun FavouriteShow.toSync() = SyncFavourite(
        syncId, profileId, title, tmdbId, imdbId, tvdbId, preferredProvider, artworkRef, position, updatedAt, removedAt,
    )

    private fun ShelfEntry.toSync() = SyncShelfEntry(
        syncId, profileId, provider, title, seriesTitle, contentType, providerContentId, tmdbId, imdbId,
        deepLink, artworkRef, source.name, pinned, hidden, watched, position, updatedAt, removedAt,
    )

    private fun RowLayoutEntry.toSync() = SyncRow(
        profileId, rowId, kind.name, provider, titleOverride, visible, tileSize.name, position, updatedAt,
    )
}
