package com.firestarterpro.templartv.data

import android.content.Context
import com.firestarterpro.templartv.data.favourites.FavouriteShowRepository
import com.firestarterpro.templartv.data.layout.RowLayoutRepository
import com.firestarterpro.templartv.data.profile.ActiveProfileStore
import com.firestarterpro.templartv.data.profile.ProfileRepository
import com.firestarterpro.templartv.data.shelf.ShelfRepository
import com.firestarterpro.templartv.data.shortcuts.ShortcutRepository
import com.firestarterpro.templartv.data.sync.SyncStore
import com.firestarterpro.templartv.data.sync.TemplarSyncStore
import com.firestarterpro.templartv.data.watchhistory.WatchHistoryRepository

/**
 * The one place the app gets its data repositories. Builds them on the single [TemplarDatabase]
 * with runtime feature flags. Tests build the same graph with [FlagGate.AllOn] and an in-memory
 * database through [create].
 */
class TemplarData private constructor(
    val database: TemplarDatabase,
    val profiles: ProfileRepository,
    val shelves: ShelfRepository,
    val favourites: FavouriteShowRepository,
    val rows: RowLayoutRepository,
    val shortcuts: ShortcutRepository,
    val history: WatchHistoryRepository,
    /** For the companion server (Templar Tablet). Same repositories and flags as the TV UI. */
    val sync: SyncStore,
) {
    companion object {
        @Volatile
        private var instance: TemplarData? = null

        fun get(context: Context): TemplarData =
            instance ?: synchronized(this) {
                instance ?: create(
                    TemplarDatabase.get(context),
                    FlagGate.fromContext(context),
                    ActiveProfileStore(context),
                ).also { instance = it }
            }

        fun create(
            db: TemplarDatabase,
            gate: FlagGate,
            activeStore: ActiveProfileStore,
            clock: () -> Long = System::currentTimeMillis,
        ): TemplarData {
            val shelves = ShelfRepository(db, db.shelfDao(), gate, clock)
            val rows = RowLayoutRepository(db, db.rowLayoutDao(), gate, clock)
            val favourites = FavouriteShowRepository(db, db.favouriteShowDao(), gate, clock)
            return TemplarData(
                database = db,
                profiles = ProfileRepository(db, db.profileDao(), db.profileSettingDao(), rows, activeStore, clock),
                shelves = shelves,
                favourites = favourites,
                rows = rows,
                shortcuts = ShortcutRepository(db.shortcutDao(), gate, clock),
                history = WatchHistoryRepository(db, db.watchHistoryDao(), shelves, gate, clock),
                sync = TemplarSyncStore(db.profileDao(), favourites, shelves, rows, clock),
            )
        }
    }
}
