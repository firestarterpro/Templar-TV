package com.firestarterpro.templartv.data.layout

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.FlagGate
import kotlinx.coroutines.flow.Flow

/**
 * Order, visibility and options of the home rows. Edits need [Flag.ROW_MANAGER]; with it off the
 * layout is still readable and [ensureDefaults] still seeds it, so home always has rows.
 */
class RowLayoutRepository(
    private val db: RoomDatabase,
    private val dao: RowLayoutDao,
    private val gate: FlagGate = FlagGate.AllOn,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(profileId: String): Flow<List<RowLayoutEntry>> = dao.observe(profileId)

    /** Adds any default row the profile is missing. Never changes a row the user has edited. */
    suspend fun ensureDefaults(profileId: String) {
        val now = clock()
        dao.insertIfMissing(DefaultRows.forProfile(profileId).map { it.copy(updatedAt = now) })
    }

    suspend fun setVisible(profileId: String, rowId: String, visible: Boolean): Boolean =
        change(profileId, rowId) { it.copy(visible = visible) }

    suspend fun rename(profileId: String, rowId: String, title: String): Boolean =
        change(profileId, rowId) { it.copy(titleOverride = title.trim()) }

    suspend fun setTileSize(profileId: String, rowId: String, size: TileSize): Boolean =
        change(profileId, rowId) { it.copy(tileSize = size) }

    suspend fun setOptions(profileId: String, rowId: String, json: String): Boolean =
        change(profileId, rowId) { it.copy(optionsJson = json) }

    /** Moves a row to [toIndex] among all of the profile's rows, hidden ones included. */
    suspend fun move(profileId: String, rowId: String, toIndex: Int): Boolean {
        if (!gate.isOn(Flag.ROW_MANAGER)) return false
        val now = clock()
        return db.withTransaction {
            val ids = dao.all(profileId).map { it.rowId }.toMutableList()
            val from = ids.indexOf(rowId)
            if (from < 0) return@withTransaction false
            ids.removeAt(from)
            ids.add(toIndex.coerceIn(0, ids.size), rowId)
            ids.forEachIndexed { index, id -> dao.setPosition(profileId, id, index, now) }
            true
        }
    }

    /** Adds a user-made row, for example a second shelf for a provider. Returns false if the id is taken. */
    suspend fun addCustom(profileId: String, row: RowLayoutEntry): Boolean {
        if (!gate.isOn(Flag.ROW_MANAGER)) return false
        return db.withTransaction {
            if (dao.get(profileId, row.rowId) != null) return@withTransaction false
            val last = dao.all(profileId).maxOfOrNull { it.position } ?: -1
            dao.put(row.copy(profileId = profileId, position = last + 1, updatedAt = clock()))
            true
        }
    }

    /** Rows changed at or after [since] (ms), oldest first, for external sync. */
    suspend fun changedSince(profileId: String, since: Long): List<RowLayoutEntry> = dao.changedSince(profileId, since)

    private suspend fun change(profileId: String, rowId: String, edit: (RowLayoutEntry) -> RowLayoutEntry): Boolean {
        if (!gate.isOn(Flag.ROW_MANAGER)) return false
        return db.withTransaction {
            val row = dao.get(profileId, rowId) ?: return@withTransaction false
            dao.put(edit(row).copy(updatedAt = clock()))
            true
        }
    }
}
