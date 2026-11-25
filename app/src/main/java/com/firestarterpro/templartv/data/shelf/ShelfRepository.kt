package com.firestarterpro.templartv.data.shelf

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.DedupeKey
import com.firestarterpro.templartv.data.FlagGate
import com.firestarterpro.templartv.data.Ordering
import kotlinx.coroutines.flow.Flow

/** What a caller knows about a title when it puts it on a shelf. */
data class ShelfDraft(
    val title: String,
    val seriesTitle: String = "",
    val contentType: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val providerContentId: String = "",
    val deepLink: String = "",
    val packageName: String = "",
    val tmdbId: Long = 0,
    val imdbId: String = "",
    val artworkRef: String = "",
    val progressMs: Long = 0,
    val runtimeMs: Long = 0,
    val progressKind: ProgressKind = ProgressKind.UNKNOWN,
    val watched: Boolean = false,
    val watchHistoryId: Long? = null,
)

/**
 * The per-provider "recently played" shelves.
 *
 * Automatic writes ([record]) need [Flag.PROVIDER_RECENT_SHELVES]; the user's own edits need
 * [Flag.SHELF_EDITING]. A flag that is off makes the call return null or false and change nothing.
 * Nothing is deleted: removing a tile hides it in "Recently removed" and [restore] brings it back.
 */
class ShelfRepository(
    private val db: RoomDatabase,
    private val dao: ShelfDao,
    private val gate: FlagGate = FlagGate.AllOn,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeShelf(profileId: String, provider: String, limit: Int = 20): Flow<List<ShelfEntry>> =
        dao.observeShelf(profileId, provider, limit)

    fun observeEditable(profileId: String, provider: String): Flow<List<ShelfEntry>> =
        dao.observeEditable(profileId, provider)

    fun observeContinue(profileId: String, limit: Int = 20): Flow<List<ShelfEntry>> =
        dao.observeContinue(profileId, limit)

    fun observeRemoved(profileId: String): Flow<List<ShelfEntry>> = dao.observeRemoved(profileId)

    /**
     * Puts a title at the front of a provider's shelf, or refreshes it if it is already there.
     * A tile the user removed stays removed when it is only seen again by observation. A manual add
     * or a launch from Templar is the user choosing it, so those bring it back.
     * Returns the shelf entry id, or null when nothing was written.
     */
    suspend fun record(profileId: String, provider: String, source: ShelfSource, draft: ShelfDraft): Long? {
        if (!gate.isOn(Flag.PROVIDER_RECENT_SHELVES)) return null
        if (draft.title.isBlank() && draft.seriesTitle.isBlank()) return null
        val now = clock()
        val key = DedupeKey.forShelf(draft.seriesTitle, draft.providerContentId, draft.title)
        val userChose = source == ShelfSource.MANUAL || source == ShelfSource.TEMPLAR_LAUNCH
        return db.withTransaction {
            val existing = dao.findByKey(profileId, provider, key)
            if (existing == null) {
                dao.insert(
                    ShelfEntry(
                        profileId = profileId, provider = provider, dedupeKey = key,
                        title = draft.title.ifBlank { draft.seriesTitle },
                        seriesTitle = draft.seriesTitle, contentType = draft.contentType,
                        seasonNumber = draft.seasonNumber, episodeNumber = draft.episodeNumber,
                        episodeTitle = draft.episodeTitle, providerContentId = draft.providerContentId,
                        deepLink = draft.deepLink, packageName = draft.packageName,
                        tmdbId = draft.tmdbId, imdbId = draft.imdbId, artworkRef = draft.artworkRef,
                        progressMs = draft.progressMs, runtimeMs = draft.runtimeMs,
                        progressKind = draft.progressKind, source = source,
                        watchHistoryId = draft.watchHistoryId,
                        position = topPosition(profileId, provider),
                        watched = draft.watched, userConfirmed = source == ShelfSource.MANUAL,
                        addedAt = now, updatedAt = now,
                    ),
                )
            } else if (existing.removedAt != null && !userChose) {
                null
            } else {
                val sameEpisode = existing.seasonNumber == draft.seasonNumber && existing.episodeNumber == draft.episodeNumber
                dao.update(
                    existing.copy(
                        title = draft.title.ifBlank { existing.title },
                        contentType = draft.contentType.ifBlank { existing.contentType },
                        seasonNumber = draft.seasonNumber, episodeNumber = draft.episodeNumber,
                        episodeTitle = draft.episodeTitle,
                        providerContentId = draft.providerContentId.ifBlank { existing.providerContentId },
                        deepLink = draft.deepLink.ifBlank { existing.deepLink },
                        packageName = draft.packageName.ifBlank { existing.packageName },
                        tmdbId = if (draft.tmdbId > 0) draft.tmdbId else existing.tmdbId,
                        imdbId = draft.imdbId.ifBlank { existing.imdbId },
                        artworkRef = draft.artworkRef.ifBlank { existing.artworkRef },
                        progressMs = draft.progressMs, runtimeMs = draft.runtimeMs,
                        progressKind = draft.progressKind,
                        watchHistoryId = draft.watchHistoryId ?: existing.watchHistoryId,
                        // A different episode in progress means the series is being watched again.
                        watched = draft.watched || (existing.watched && sameEpisode),
                        userConfirmed = existing.userConfirmed || source == ShelfSource.MANUAL,
                        position = if (existing.pinned) existing.position else topPosition(profileId, provider),
                        removedAt = if (userChose) null else existing.removedAt,
                        updatedAt = now,
                    ),
                )
                existing.id
            }
        }
    }

    /** The user adds a show to a shelf by hand ("Add a show"). Counts as confirmed. */
    suspend fun addManual(profileId: String, provider: String, draft: ShelfDraft): Long? {
        if (!gate.isOn(Flag.SHELF_EDITING)) return null
        return record(profileId, provider, ShelfSource.MANUAL, draft)
    }

    /** Moves a visible tile to [toIndex] in what the shelf shows. Returns false if nothing moved. */
    suspend fun move(profileId: String, provider: String, id: Long, toIndex: Int): Boolean {
        if (!gate.isOn(Flag.SHELF_EDITING)) return false
        val now = clock()
        return db.withTransaction {
            val ids = dao.visible(profileId, provider).map { it.id }
            if (id !in ids) return@withTransaction false
            Ordering.moveTo(ids, id, toIndex).forEachIndexed { index, entryId -> dao.setPosition(entryId, index, now) }
            true
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean): Boolean = edit { dao.setPinned(id, pinned, clock()) }
    suspend fun setHidden(id: Long, hidden: Boolean): Boolean = edit { dao.setHidden(id, hidden, clock()) }
    suspend fun setWatched(id: Long, watched: Boolean): Boolean = edit { dao.setWatched(id, watched, clock()) }

    /** Takes a tile off its shelf. It moves to "Recently removed" and can be restored. */
    suspend fun remove(id: Long): Boolean = edit { dao.markRemoved(id, clock()) }

    suspend fun restore(id: Long): Boolean = edit { dao.restore(id, clock()) }

    /**
     * Moves a tile to another provider's shelf. If that shelf already has the same show, the
     * existing tile is kept (and brought back if it was removed) and this one is removed instead.
     * Returns the id of the tile that now holds the show, or null.
     */
    suspend fun changeProvider(id: Long, newProvider: String): Long? {
        if (!gate.isOn(Flag.SHELF_EDITING)) return null
        val now = clock()
        return db.withTransaction {
            val entry = dao.get(id) ?: return@withTransaction null
            if (entry.provider == newProvider) return@withTransaction id
            val clash = dao.findByKey(entry.profileId, newProvider, entry.dedupeKey)
            if (clash == null) {
                val top = topPosition(entry.profileId, newProvider)
                dao.setProvider(id, newProvider, now)
                dao.setPosition(id, top, now)
                id
            } else {
                if (clash.removedAt != null) dao.restore(clash.id, now)
                dao.markRemoved(id, now)
                clash.id
            }
        }
    }

    /** Looks a tile up by its stable id, for external clients. */
    suspend fun getBySyncId(syncId: String): ShelfEntry? = dao.getBySync(syncId)

    /** Tiles changed at or after [since] (ms), including removed ones, oldest first. */
    suspend fun changedSince(profileId: String, since: Long): List<ShelfEntry> = dao.changedSince(profileId, since)

    private suspend fun edit(block: suspend () -> Unit): Boolean {
        if (!gate.isOn(Flag.SHELF_EDITING)) return false
        block()
        return true
    }

    private suspend fun topPosition(profileId: String, provider: String): Int =
        (dao.minPosition(profileId, provider) ?: 0) - 1
}
