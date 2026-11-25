package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.shelf.ShelfEntry

/**
 * How the big-box home opens a shelf tile. With [Flag.PROVIDER_RECENT_SHELVES] on, the tile's
 * shelf entry is looked up and handed to [com.firestarterpro.templartv.observation.ShelfLauncher]
 * (which opens it and moves the tile to the front). If the entry is gone, the flag is off, or the
 * launcher can't open anything, the old direct open is used. Pure, so it is unit tested.
 */
object ShelfOpen {
    suspend fun open(
        ref: Long,
        launcherOn: Boolean,
        lookup: suspend (Long) -> ShelfEntry?,
        launch: suspend (ShelfEntry) -> Boolean,
        fallback: () -> Boolean,
    ): Boolean {
        if (launcherOn && ref > 0) {
            val entry = runCatching { lookup(ref) }.getOrNull()
            if (entry != null && runCatching { launch(entry) }.getOrDefault(false)) return true
        }
        return fallback()
    }
}
