package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.layout.RowKind
import com.firestarterpro.templartv.data.layout.TileSize
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.data.shelf.ShelfSource

enum class TileKind {
    /** A tile on a provider's recently played shelf (or Continue Watching). */
    SHELF,
    /** A favourite show. */
    FAVOURITE,
    /** A tile an app published through Android TV channels. The provider owns it. */
    CHANNEL,
    APP,
    /** The "Add a show" tile at the end of a shelf. */
    ADD,
    /** "More from X" or "See all": opens the rest of the row in a grid. */
    MORE,
}

/**
 * One tile, the same in every view. [key] is unique on the whole home screen and stays the same
 * across edits and refreshes, which is how focus is kept on the right tile.
 */
data class HomeTile(
    val key: String,
    val kind: TileKind,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val providerId: String = "",
    val providerLabel: String = "",
    val artworkUri: String? = null,
    /** 0..1, or null when the position is not known. Never shown for [ProgressKind.UNKNOWN]. */
    val progress: Float? = null,
    val progressKind: ProgressKind = ProgressKind.UNKNOWN,
    /** Database id for shelf and favourite tiles, TV content id for channel tiles. */
    val ref: Long = 0,
    val packageName: String = "",
    val deepLink: String = "",
    val tmdbId: Long = 0,
    val source: ShelfSource? = null,
    val watched: Boolean = false,
    /** True when the owner confirmed the title by adding it by hand. */
    val confirmed: Boolean = false,
) {
    /** Templar owns the order of favourites and of shelf tiles it did not take from a provider row. */
    val movable: Boolean
        get() = kind == TileKind.FAVOURITE || kind == TileKind.APP || (kind == TileKind.SHELF && source != ShelfSource.PROVIDER_ROW)

    val isRealContent: Boolean get() = kind != TileKind.ADD && kind != TileKind.MORE
}

/** One row of the home screen, ready to draw in any view. */
data class HomeRowModel(
    val rowId: String,
    val title: String,
    val kind: RowKind,
    val provider: String,
    /** What the row shows, in order, ending with an optional MORE tile and an optional ADD tile. */
    val tiles: List<HomeTile>,
    /** What is behind the MORE tile. */
    val moreTiles: List<HomeTile> = emptyList(),
    val tileSize: TileSize = TileSize.MEDIUM,
    /** One line of help shown in the row when it has no real tiles yet. */
    val help: String = "",
    /** The owner can add a show here by hand. */
    val canAddShow: Boolean = false,
) {
    val realTiles: List<HomeTile> get() = tiles.filter { it.isRealContent }
    fun indexOf(key: String): Int = tiles.indexOfFirst { it.key == key }
}

/** A row as the Row Manager lists it: every row, hidden ones included. */
data class ManagedRow(val rowId: String, val title: String, val visible: Boolean, val kind: RowKind)
