package com.firestarterpro.templartv.data.favourites

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.DedupeKey
import com.firestarterpro.templartv.data.FlagGate
import com.firestarterpro.templartv.data.Ordering
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The ordered Favourite Shows list. Needs [Flag.FAVOURITE_SHOWS]; when off, writes do nothing. */
class FavouriteShowRepository(
    private val db: RoomDatabase,
    private val dao: FavouriteShowDao,
    private val gate: FlagGate = FlagGate.AllOn,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(profileId: String): Flow<List<FavouriteShow>> = dao.observe(profileId)
    fun observeRemoved(profileId: String): Flow<List<FavouriteShow>> = dao.observeRemoved(profileId)

    /** Adds a show at the front of the list, or brings back one that was removed. Returns its id. */
    suspend fun add(
        profileId: String,
        title: String,
        tmdbId: Long = 0,
        imdbId: String = "",
        tvdbId: String = "",
        preferredProvider: String = "",
        artworkRef: String = "",
        syncId: String? = null,
    ): Long? {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS) || title.isBlank()) return null
        val key = DedupeKey.forFavourite(tmdbId, title)
        val now = clock()
        return db.withTransaction {
            val existing = dao.findByKey(profileId, key)
            val top = (dao.minPosition(profileId) ?: 0) - 1
            if (existing == null) {
                dao.insert(
                    FavouriteShow(
                        syncId = syncId ?: UUID.randomUUID().toString(),
                        profileId = profileId, dedupeKey = key, title = title.trim(), tmdbId = tmdbId,
                        imdbId = imdbId, tvdbId = tvdbId, preferredProvider = preferredProvider,
                        artworkRef = artworkRef, position = top, addedAt = now, updatedAt = now,
                    ),
                )
            } else {
                if (existing.removedAt != null) {
                    dao.update(existing.copy(removedAt = null, position = top, updatedAt = now))
                }
                existing.id
            }
        }
    }

    /** Moves a favourite to [toIndex]. Returns false if it is not in the current list. */
    suspend fun move(profileId: String, id: Long, toIndex: Int): Boolean {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS)) return false
        val now = clock()
        return db.withTransaction {
            val ids = dao.current(profileId).map { it.id }
            if (id !in ids) return@withTransaction false
            Ordering.moveTo(ids, id, toIndex).forEachIndexed { index, showId -> dao.setPosition(showId, index, now) }
            true
        }
    }

    suspend fun getById(id: Long): FavouriteShow? = dao.getById(id)

    /** Changes title, ids, provider and artwork of a favourite. Blank values are left as they are. */
    suspend fun updateDetails(
        id: Long, title: String = "", tmdbId: Long = 0, imdbId: String = "", tvdbId: String = "",
        preferredProvider: String? = null, artworkRef: String = "",
    ): Boolean {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS)) return false
        val f = dao.getById(id) ?: return false
        dao.update(
            f.copy(
                title = title.ifBlank { f.title },
                tmdbId = if (tmdbId > 0) tmdbId else f.tmdbId,
                imdbId = imdbId.ifBlank { f.imdbId },
                tvdbId = tvdbId.ifBlank { f.tvdbId },
                preferredProvider = preferredProvider ?: f.preferredProvider,
                artworkRef = artworkRef.ifBlank { f.artworkRef },
                updatedAt = clock(),
            ),
        )
        return true
    }

    /** Applies an order sent by another device. Listed favourites come first; the rest keep their order after. */
    suspend fun reorder(profileId: String, orderedSyncIds: List<String>): Boolean {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS)) return false
        val now = clock()
        return db.withTransaction {
            val current = dao.current(profileId)
            val bySync = current.associateBy { it.syncId }
            val desired = orderedSyncIds.mapNotNull { bySync[it]?.id }
            Ordering.applyOrder(current.map { it.id }, desired).forEachIndexed { index, showId -> dao.setPosition(showId, index, now) }
            true
        }
    }

    suspend fun setPreferredProvider(id: Long, provider: String): Boolean {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS)) return false
        dao.setPreferredProvider(id, provider, clock())
        return true
    }

    /** Looks a favourite up by its stable id, for external clients. */
    suspend fun getBySyncId(syncId: String): FavouriteShow? = dao.getBySync(syncId)

    /** Favourites changed at or after [since] (ms), including removed ones, oldest first. */
    suspend fun changedSince(profileId: String, since: Long): List<FavouriteShow> = dao.changedSince(profileId, since)

    /** Takes a show off the list. It stays under "Recently removed" and can be restored. */
    suspend fun remove(id: Long): Boolean {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS)) return false
        dao.markRemoved(id, clock())
        return true
    }

    suspend fun restore(id: Long): Boolean {
        if (!gate.isOn(Flag.FAVOURITE_SHOWS)) return false
        dao.restore(id, clock())
        return true
    }
}
