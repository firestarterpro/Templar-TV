package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.DedupeKey
import com.firestarterpro.templartv.data.favourites.FavouriteShow
import com.firestarterpro.templartv.data.layout.RowKind
import com.firestarterpro.templartv.data.layout.RowLayoutEntry
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.data.shelf.ShelfEntry

/** A row of tiles an app published itself. [provider] is empty when the app is none of Templar's providers. */
data class ChannelRowInput(val key: String, val title: String, val provider: String, val tiles: List<HomeTile>)

/** Everything the builder needs. All plain data, so it can be tested without Android. */
data class HomeBuildInput(
    val layout: List<RowLayoutEntry>,
    val continueShelf: List<ShelfEntry> = emptyList(),
    /** Visible shelf entries per provider id, in shelf order. */
    val shelves: Map<String, List<ShelfEntry>> = emptyMap(),
    val favourites: List<FavouriteShow> = emptyList(),
    val channelContinue: List<HomeTile> = emptyList(),
    val channelRows: List<ChannelRowInput> = emptyList(),
    val appTiles: List<HomeTile> = emptyList(),
    val shelfLength: Int = 20,
    val showProviderRows: Boolean = true,
    val showNewAndUpcoming: Boolean = true,
)

/**
 * Turns the saved layout plus the shelves, favourites and channel content into the rows every
 * home view draws. Hidden rows are left out. Empty rows hide themselves, except Favourite Shows
 * and provider shelves, which stay and show an "Add a show" tile and one line of help.
 */
object HomeModelBuilder {

    fun build(input: HomeBuildInput): List<HomeRowModel> =
        input.layout.filter { it.visible }.sortedBy { it.position }.mapNotNull { row(it, input) }

    /** All rows for the Row Manager, in saved order, hidden ones included. */
    fun managed(layout: List<RowLayoutEntry>): List<ManagedRow> =
        layout.sortedBy { it.position }.map { ManagedRow(it.rowId, rowTitle(it), it.visible, it.kind) }

    fun rowTitle(row: RowLayoutEntry): String {
        if (row.titleOverride.isNotBlank()) return row.titleOverride
        return when (row.kind) {
            RowKind.CONTINUE_WATCHING -> "Continue Watching"
            RowKind.FAVOURITE_SHOWS -> "Favourite Shows"
            RowKind.RECENTLY_ADDED -> "Recently Added"
            RowKind.PROVIDER_SHELF -> ProviderCatalog.label(row.provider)
            RowKind.PROVIDER_ROWS -> ProviderCatalog.label(row.provider) + " rows"
            RowKind.PERSONAL_MEDIA -> "Personal Shows and Movies"
            RowKind.PERSONAL_MUSIC -> "Personal Music"
            RowKind.NEW_UPCOMING -> "New and Upcoming"
            RowKind.LEAVING_SOON -> "Leaving Soon"
            RowKind.REVIEW -> "Review"
            RowKind.APPS -> "Apps"
            RowKind.CUSTOM -> "Custom"
        }
    }

    private fun row(entry: RowLayoutEntry, input: HomeBuildInput): HomeRowModel? {
        val id = entry.rowId
        val title = rowTitle(entry)
        val cap = input.shelfLength.coerceIn(5, 100)
        fun model(
            tiles: List<HomeTile>,
            more: List<HomeTile> = emptyList(),
            help: String = "",
            canAdd: Boolean = false,
        ) = HomeRowModel(id, title, entry.kind, entry.provider, tiles.map { it.prefixed(id) }, more.map { it.prefixed(id) }, entry.tileSize, help, canAdd)

        fun capped(tiles: List<HomeTile>, moreLabel: String): HomeRowModel {
            val more = tiles.drop(cap)
            return model(tiles.take(cap) + listOfNotNull(moreTile(id, moreLabel, more.size)), more)
        }

        return when (entry.kind) {
            RowKind.CONTINUE_WATCHING -> {
                val own = input.continueShelf.map { it.toTile() }
                val seen = own.map { DedupeKey.normalize(it.title) }.toSet()
                val merged = own + input.channelContinue.filter { DedupeKey.normalize(it.title) !in seen }
                if (merged.isEmpty()) null else capped(merged, "See all")
            }

            RowKind.FAVOURITE_SHOWS -> {
                val tiles = input.favourites.map { it.toTile() }
                val shown = tiles.take(cap)
                val more = tiles.drop(cap)
                model(
                    shown + listOfNotNull(moreTile(id, "See all", more.size)) + addTile(id),
                    more,
                    help = if (tiles.isEmpty()) "No favourite shows yet. Add a show to keep it one press away." else "",
                    canAdd = true,
                )
            }

            RowKind.PROVIDER_SHELF -> {
                val label = ProviderCatalog.label(entry.provider)
                val owned = input.shelves[entry.provider].orEmpty().map { it.toTile() }
                val channel = if (input.showProviderRows) {
                    input.channelRows.filter { it.provider == entry.provider }.flatMap { it.tiles }
                } else {
                    emptyList()
                }
                val inline = RowOptions.inlineProviderRows(entry.optionsJson)
                val shown = owned.take(cap)
                val more = owned.drop(cap) + if (inline) emptyList() else channel
                val shownAll = shown + if (inline) channel else emptyList()
                val moreLabel = if (channel.isNotEmpty() && !inline) "More from $label" else "See all"
                model(
                    shownAll + listOfNotNull(moreTile(id, moreLabel, more.size)) + addTile(id),
                    more,
                    help = if (owned.isEmpty()) "Nothing from $label yet. Play something, or add a show." else "",
                    canAdd = true,
                )
            }

            RowKind.PROVIDER_ROWS -> {
                if (!input.showProviderRows) return null
                val tiles = input.channelRows.filter { it.provider == entry.provider }.flatMap { it.tiles }
                if (tiles.isEmpty()) null else capped(tiles, "See all")
            }

            RowKind.NEW_UPCOMING -> {
                if (!input.showNewAndUpcoming) return null
                val tiles = input.channelRows.filter { it.provider.isEmpty() }.flatMap { it.tiles }
                if (tiles.isEmpty()) null else capped(tiles, "See all")
            }

            RowKind.APPS -> if (input.appTiles.isEmpty()) null else capped(input.appTiles, "See all apps")

            // No data source yet: these fill in as later threads add Jellyfin, review and custom rows.
            RowKind.RECENTLY_ADDED, RowKind.PERSONAL_MEDIA, RowKind.PERSONAL_MUSIC,
            RowKind.LEAVING_SOON, RowKind.REVIEW, RowKind.CUSTOM -> null
        }
    }

    private fun moreTile(rowId: String, label: String, count: Int): HomeTile? =
        if (count <= 0) null else HomeTile(key = "more", kind = TileKind.MORE, title = label, subtitle = "$count more")

    private fun addTile(rowId: String) = HomeTile(key = "add", kind = TileKind.ADD, title = "Add a show")

    private fun HomeTile.prefixed(rowId: String) = copy(key = "$rowId/$key")

    // Tile mapping -----------------------------------------------------------------------------

    fun ShelfEntry.toTile(): HomeTile {
        val showProgress = progressKind != ProgressKind.UNKNOWN && progressMs > 0 && runtimeMs > 0
        return HomeTile(
            key = "shelf:$id",
            kind = TileKind.SHELF,
            title = seriesTitle.ifBlank { title },
            subtitle = episodeLine(seasonNumber, episodeNumber, episodeTitle.ifBlank { if (seriesTitle.isNotBlank()) title else "" }),
            providerId = provider,
            providerLabel = ProviderCatalog.label(provider),
            artworkUri = artworkRef.ifBlank { null },
            progress = if (showProgress) (progressMs.toFloat() / runtimeMs).coerceIn(0f, 1f) else null,
            progressKind = progressKind,
            ref = id,
            packageName = packageName,
            deepLink = deepLink,
            tmdbId = tmdbId,
            source = source,
            watched = watched,
            confirmed = userConfirmed,
        )
    }

    fun FavouriteShow.toTile(): HomeTile = HomeTile(
        key = "fav:$id",
        kind = TileKind.FAVOURITE,
        title = title,
        providerId = preferredProvider,
        providerLabel = if (preferredProvider.isBlank()) "" else ProviderCatalog.label(preferredProvider),
        artworkUri = artworkRef.ifBlank { null },
        ref = id,
        tmdbId = tmdbId,
        confirmed = true,
    )

    fun episodeLine(season: Int, episode: Int, episodeTitle: String): String {
        val code = when {
            season > 0 && episode > 0 -> "S$season · E$episode"
            episode > 0 -> "E$episode"
            else -> ""
        }
        return listOf(code, episodeTitle.trim()).filter { it.isNotEmpty() }.joinToString("  ·  ")
    }
}
