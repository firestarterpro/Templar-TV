package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.ProfileIds
import com.firestarterpro.templartv.data.ProviderIds
import com.firestarterpro.templartv.data.favourites.FavouriteShow
import com.firestarterpro.templartv.data.layout.DefaultRows
import com.firestarterpro.templartv.data.layout.RowKind
import com.firestarterpro.templartv.data.layout.RowLayoutEntry
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.data.shelf.ShelfSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun shelf(id: Long, provider: String, title: String, source: ShelfSource = ShelfSource.TEMPLAR_LAUNCH, progress: Long = 0, runtime: Long = 0, kind: ProgressKind = ProgressKind.UNKNOWN) =
    ShelfEntry(
        id = id, profileId = ProfileIds.DEFAULT, provider = provider, dedupeKey = "t:$title", title = title,
        progressMs = progress, runtimeMs = runtime, progressKind = kind, source = source,
    )

private fun fav(id: Long, title: String, provider: String = "") =
    FavouriteShow(id = id, profileId = ProfileIds.DEFAULT, dedupeKey = "t:$title", title = title, preferredProvider = provider)

private val layout = DefaultRows.forProfile(ProfileIds.DEFAULT)

class HomeViewTest {
    @Test fun unknownOrMissingKeyFallsBackToRows() {
        assertEquals(HomeView.ROWS, HomeView.resolve(null) { true })
        assertEquals(HomeView.ROWS, HomeView.resolve("nonsense") { true })
    }

    @Test fun aViewWhoseFlagIsOffFallsBackToRows() {
        val off = { f: Flag -> f != Flag.HOME_VIEW_COLUMNS }
        assertEquals(HomeView.ROWS, HomeView.resolve("columns", off))
        assertEquals(HomeView.WALL, HomeView.resolve("wall", off))
    }

    @Test fun rowsIsAlwaysAvailableAndFirst() {
        val none = HomeView.available { false }
        assertEquals(listOf(HomeView.ROWS), none)
        assertEquals(HomeView.entries.toList(), HomeView.available { true })
    }
}

class RowOptionsTest {
    @Test fun readsAndWritesTheInlineOption() {
        assertFalse(RowOptions.inlineProviderRows(""))
        assertEquals("{\"inline_provider_rows\":true}", RowOptions.withInlineProviderRows("", true))
        assertTrue(RowOptions.inlineProviderRows(RowOptions.withInlineProviderRows("{}", true)))
        assertFalse(RowOptions.inlineProviderRows(RowOptions.withInlineProviderRows("{\"inline_provider_rows\":true}", false)))
    }

    @Test fun keepsOtherKeys() {
        val out = RowOptions.withInlineProviderRows("{\"x\":1}", true)
        assertEquals("{\"x\":1,\"inline_provider_rows\":true}", out)
        assertEquals("{\"x\":1,\"inline_provider_rows\":false}", RowOptions.withInlineProviderRows(out, false))
    }
}

class ProviderCatalogTest {
    @Test fun matchesByPackageThenByLabel() {
        assertEquals(ProviderIds.PEACOCK, ProviderCatalog.providerFor("com.peacocktv.peacockandroid"))
        assertEquals(ProviderIds.MAX, ProviderCatalog.providerFor("some.unknown.pkg", "Max"))
        assertEquals("", ProviderCatalog.providerFor("com.google.android.youtube.tv", "YouTube"))
    }
}

class HomeModelBuilderTest {

    private fun build(input: HomeBuildInput) = HomeModelBuilder.build(input)

    @Test fun emptyRowsHideExceptFavouritesAndProviderShelves() {
        val rows = build(HomeBuildInput(layout))
        val ids = rows.map { it.rowId }
        assertTrue(DefaultRows.FAVOURITE_SHOWS in ids)
        assertTrue(DefaultRows.shelfRowId(ProviderIds.PEACOCK) in ids)
        assertFalse(DefaultRows.CONTINUE_WATCHING in ids)
        assertFalse(DefaultRows.REVIEW in ids)
        assertFalse(DefaultRows.PERSONAL_MUSIC in ids)
    }

    @Test fun hiddenRowsAreLeftOutAndOrderFollowsLayout() {
        val hidden = layout.map { if (it.rowId == DefaultRows.FAVOURITE_SHOWS) it.copy(visible = false) else it }
        val rows = build(HomeBuildInput(hidden))
        assertFalse(rows.any { it.rowId == DefaultRows.FAVOURITE_SHOWS })
        assertEquals(rows.map { it.rowId }, rows.map { it.rowId }.sortedBy { id -> layout.first { it.rowId == id }.position })
        // Netflix and Hulu start hidden.
        assertFalse(rows.any { it.rowId == DefaultRows.shelfRowId(ProviderIds.NETFLIX) })
    }

    @Test fun emptyFavouritesShowHelpAndAnAddTile() {
        val row = build(HomeBuildInput(layout)).first { it.rowId == DefaultRows.FAVOURITE_SHOWS }
        assertTrue(row.help.isNotBlank())
        assertEquals(listOf(TileKind.ADD), row.tiles.map { it.kind })
        assertTrue(row.canAddShow)
    }

    @Test fun tileKeysAreUniqueAcrossTheWholeScreen() {
        val input = HomeBuildInput(
            layout,
            continueShelf = listOf(shelf(1, ProviderIds.PEACOCK, "Poker Face", progress = 10, runtime = 100, kind = ProgressKind.EXACT)),
            shelves = mapOf(ProviderIds.PEACOCK to listOf(shelf(1, ProviderIds.PEACOCK, "Poker Face"))),
            favourites = listOf(fav(5, "Poker Face")),
        )
        val keys = build(input).flatMap { r -> r.tiles.map { it.key } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun progressOnlyShowsWhenKnown() {
        val exact = with(HomeModelBuilder) { shelf(1, ProviderIds.MAX, "A", progress = 25, runtime = 100, kind = ProgressKind.EXACT).toTile() }
        val unknown = with(HomeModelBuilder) { shelf(2, ProviderIds.MAX, "B", progress = 25, runtime = 100, kind = ProgressKind.UNKNOWN).toTile() }
        assertEquals(0.25f, exact.progress!!, 0.001f)
        assertNull(unknown.progress)
    }

    @Test fun continueWatchingMergesChannelTilesWithoutDuplicates() {
        val channel = listOf(
            HomeTile("card:1", TileKind.CHANNEL, "Poker Face"),
            HomeTile("card:2", TileKind.CHANNEL, "Severance"),
        )
        val input = HomeBuildInput(
            layout,
            continueShelf = listOf(shelf(1, ProviderIds.PEACOCK, "poker face", progress = 5, runtime = 50, kind = ProgressKind.EXACT)),
            channelContinue = channel,
        )
        val row = build(input).first { it.rowId == DefaultRows.CONTINUE_WATCHING }
        assertEquals(listOf("poker face", "Severance"), row.tiles.map { it.title })
    }

    @Test fun longRowsCapAndPutTheRestBehindSeeAll() {
        val many = (1..30L).map { shelf(it, ProviderIds.PEACOCK, "Show $it") }
        val row = build(HomeBuildInput(layout, shelves = mapOf(ProviderIds.PEACOCK to many), shelfLength = 20))
            .first { it.rowId == DefaultRows.shelfRowId(ProviderIds.PEACOCK) }
        assertEquals(20, row.tiles.count { it.kind == TileKind.SHELF })
        assertEquals(10, row.moreTiles.size)
        val more = row.tiles.first { it.kind == TileKind.MORE }
        assertEquals("See all", more.title)
        assertEquals(TileKind.ADD, row.tiles.last().kind)
    }

    @Test fun providerRowsGoBehindMoreFromOrInlineWhenAsked() {
        val channelTiles = listOf(HomeTile("card:9", TileKind.CHANNEL, "Trending"))
        val channel = listOf(ChannelRowInput("c", "Trending", ProviderIds.PEACOCK, channelTiles))
        val rowId = DefaultRows.shelfRowId(ProviderIds.PEACOCK)
        val behind = build(HomeBuildInput(layout, channelRows = channel)).first { it.rowId == rowId }
        assertEquals("More from Peacock", behind.tiles.first { it.kind == TileKind.MORE }.title)
        assertEquals(1, behind.moreTiles.size)

        val inlineLayout = layout.map { if (it.rowId == rowId) it.copy(optionsJson = RowOptions.withInlineProviderRows("", true)) else it }
        val inline = build(HomeBuildInput(inlineLayout, channelRows = channel)).first { it.rowId == rowId }
        assertTrue(inline.tiles.any { it.kind == TileKind.CHANNEL })
        assertFalse(inline.tiles.any { it.kind == TileKind.MORE })
    }

    @Test fun providerRowsFlagOffRemovesThem() {
        val channel = listOf(ChannelRowInput("c", "Trending", ProviderIds.PEACOCK, listOf(HomeTile("card:9", TileKind.CHANNEL, "Trending"))))
        val row = build(HomeBuildInput(layout, channelRows = channel, showProviderRows = false))
            .first { it.rowId == DefaultRows.shelfRowId(ProviderIds.PEACOCK) }
        assertFalse(row.tiles.any { it.kind == TileKind.CHANNEL || it.kind == TileKind.MORE })
    }

    @Test fun renamedRowUsesTheOwnersTitle() {
        val renamed = layout.map { if (it.rowId == DefaultRows.FAVOURITE_SHOWS) it.copy(titleOverride = "Must watch") else it }
        assertEquals("Must watch", build(HomeBuildInput(renamed)).first { it.rowId == DefaultRows.FAVOURITE_SHOWS }.title)
        assertEquals("Must watch", HomeModelBuilder.managed(renamed).first { it.rowId == DefaultRows.FAVOURITE_SHOWS }.title)
    }

    @Test fun managerListsHiddenRowsToo() {
        val managed = HomeModelBuilder.managed(layout)
        assertEquals(layout.size, managed.size)
        assertFalse(managed.first { it.rowId == DefaultRows.shelfRowId(ProviderIds.NETFLIX) }.visible)
    }

    @Test fun episodeLine() {
        assertEquals("S1 · E5  ·  Pilot", HomeModelBuilder.episodeLine(1, 5, "Pilot"))
        assertEquals("E5", HomeModelBuilder.episodeLine(0, 5, ""))
        assertEquals("", HomeModelBuilder.episodeLine(0, 0, ""))
    }
}

class HomeRulesTest {
    private fun row(id: String, vararg keys: String) =
        HomeRowModel(id, id, RowKind.CUSTOM, "", keys.map { HomeTile(it, TileKind.SHELF, it, source = ShelfSource.MANUAL) })

    @Test fun removedTileFocusesNextThenPrevious() {
        val rows = listOf(row("r1", "a", "b", "c"))
        assertEquals(listOf("c", "a"), HomeRules.focusAfterTileGone(rows, "r1", "b").take(2))
        assertEquals(listOf("b"), HomeRules.focusAfterTileGone(rows, "r1", "c").take(1))
    }

    @Test fun lastTileGoneFallsToTheSameColumnInTheNextRow() {
        val rows = listOf(row("r1", "a"), row("r2", "x", "y"))
        assertEquals(listOf("x"), HomeRules.focusAfterTileGone(rows, "r1", "a"))
    }

    @Test fun rowHiddenGoesToSameColumnNextRowThenPrevious() {
        val rows = listOf(row("r1", "a", "b"), row("r2", "c", "d", "e"), row("r3", "f"))
        assertEquals("f", HomeRules.focusAfterRowHidden(rows, "r2", 2)[0])
        assertEquals("b", HomeRules.focusAfterRowHidden(rows, "r2", 2)[1])
        assertEquals("e", HomeRules.focusAfterRowHidden(rows, "r3", 5)[0])
    }

    @Test fun moveKeysFollowTheViewsAxis() {
        assertEquals(1, HomeRules.moveDelta(HomeView.ROWS, Dir.RIGHT, 5))
        assertNull(HomeRules.moveDelta(HomeView.ROWS, Dir.UP, 5))
        assertEquals(-1, HomeRules.moveDelta(HomeView.COLUMNS, Dir.UP, 5))
        assertNull(HomeRules.moveDelta(HomeView.COLUMNS, Dir.LEFT, 5))
        assertEquals(5, HomeRules.moveDelta(HomeView.RAIL_GRID, Dir.DOWN, 5))
        assertEquals(-6, HomeRules.moveDelta(HomeView.WALL, Dir.UP, 6))
    }

    @Test fun stepStaysInsideTheMovableTiles() {
        val s = MoveSession("r", "k", 1, 1)
        assertEquals(0, HomeRules.step(s, -5, 3).currentIndex)
        assertEquals(2, HomeRules.step(s, 5, 3).currentIndex)
    }

    @Test fun applyMoveReordersOnlyMovableTilesAndLeavesTheRestInPlace() {
        val add = HomeTile("add", TileKind.ADD, "Add")
        val r = HomeRowModel("r", "r", RowKind.FAVOURITE_SHOWS, "",
            listOf(HomeTile("a", TileKind.FAVOURITE, "a"), HomeTile("b", TileKind.FAVOURITE, "b"), HomeTile("c", TileKind.FAVOURITE, "c"), add))
        val moved = HomeRules.applyMove(listOf(r), MoveSession("r", "a", 0, 2)).first()
        assertEquals(listOf("b", "c", "a", "add"), moved.tiles.map { it.key })
        assertEquals(2, HomeRules.movableIndex(moved, "a"))
        assertEquals(listOf(r), HomeRules.applyMove(listOf(r), null))
    }

    @Test fun providerRowTilesAreNotMovable() {
        val t = HomeTile("a", TileKind.SHELF, "a", source = ShelfSource.PROVIDER_ROW)
        assertFalse(t.movable)
        assertTrue(t.copy(source = ShelfSource.MANUAL).movable)
        assertFalse(HomeTile("c", TileKind.CHANNEL, "c").movable)
    }

    @Test fun rowMoveAmongVisibleRowsMapsToTheFullOrder() {
        val all = listOf("a", "h1", "b", "h2", "c")
        val visible = listOf("a", "b", "c")
        // Move "c" to the front of the visible rows: it lands before "a".
        assertEquals(0, HomeRules.fullIndexForVisibleMove(all, visible, "c", 0))
        // Move "a" to the end of the visible rows: after "c", at the end.
        assertEquals(4, HomeRules.fullIndexForVisibleMove(all, visible, "a", 2))
        // Move "a" between b and c: it lands after the hidden row that follows b, before c.
        assertEquals(3, HomeRules.fullIndexForVisibleMove(all, visible, "a", 1))
    }

    @Test fun backGoesToFirstTileThenTopBar() {
        assertEquals(HomeRules.BackTarget.FIRST_TILE, HomeRules.backTarget("x", "a"))
        assertEquals(HomeRules.BackTarget.TOP_BAR, HomeRules.backTarget("a", "a"))
        assertEquals(HomeRules.BackTarget.NONE, HomeRules.backTarget(null, "a"))
    }
}

class TileMenuTest {
    private val favRow = HomeRowModel("f", "Favourites", RowKind.FAVOURITE_SHOWS, "", emptyList(), canAddShow = true)
    private fun ctx(tile: HomeTile, isFav: Boolean = false, editing: Boolean = true, move: Boolean = true) =
        TileMenuContext(tile, favRow, isFav, editing, favouritesOn = true, rowEditing = true, moveAllowed = move)

    @Test fun templarShelfTileGetsOpenFavouriteMoveRemoveMore() {
        val t = HomeTile("a", TileKind.SHELF, "A", source = ShelfSource.TEMPLAR_LAUNCH)
        assertEquals(
            listOf(MenuAction.OPEN, MenuAction.ADD_FAVOURITE, MenuAction.MOVE, MenuAction.REMOVE, MenuAction.MORE),
            TileMenu.primary(ctx(t)),
        )
        assertEquals(MenuAction.REMOVE_FAVOURITE, TileMenu.primary(ctx(t, isFav = true))[1])
    }

    @Test fun providerRowTileHasNoMove() {
        val t = HomeTile("a", TileKind.SHELF, "A", source = ShelfSource.PROVIDER_ROW)
        assertFalse(MenuAction.MOVE in TileMenu.primary(ctx(t)))
        assertFalse(MenuAction.CHANGE_PROVIDER in TileMenu.more(ctx(t)))
    }

    @Test fun favouriteTileMenu() {
        val t = HomeTile("a", TileKind.FAVOURITE, "A")
        assertEquals(
            listOf(MenuAction.OPEN, MenuAction.MOVE, MenuAction.REMOVE_FAVOURITE, MenuAction.MORE),
            TileMenu.primary(ctx(t)),
        )
    }

    @Test fun neverMoreThanFiveAndMoveHiddenWhenNotAllowed() {
        val t = HomeTile("a", TileKind.SHELF, "A", source = ShelfSource.MANUAL)
        assertTrue(TileMenu.primary(ctx(t)).size <= 5)
        assertFalse(MenuAction.MOVE in TileMenu.primary(ctx(t, move = false)))
    }

    @Test fun editingFlagOffHidesEditActions() {
        val t = HomeTile("a", TileKind.SHELF, "A", source = ShelfSource.MANUAL)
        val p = TileMenu.primary(ctx(t, editing = false))
        assertFalse(MenuAction.MOVE in p)
        assertFalse(MenuAction.REMOVE in p)
        assertTrue(MenuAction.OPEN in p)
    }

    @Test fun addAndMoreTilesHaveTheirOwnMenus() {
        assertEquals(listOf(MenuAction.ADD_SHOW_HERE, MenuAction.EDIT_ROW), TileMenu.primary(ctx(HomeTile("add", TileKind.ADD, "Add"))))
        assertEquals(listOf(MenuAction.OPEN, MenuAction.EDIT_ROW), TileMenu.primary(ctx(HomeTile("m", TileKind.MORE, "More"))))
        assertTrue(TileMenu.primary(ctx(HomeTile("p", TileKind.APP, "App"))).isEmpty())
    }
}
