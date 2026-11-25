package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.Ordering

enum class Dir { LEFT, RIGHT, UP, DOWN }

/** An edit in progress: one tile is lifted and the owner moves it with the D-pad. */
data class MoveSession(
    val rowId: String,
    val tileKey: String,
    val originalIndex: Int,
    val currentIndex: Int,
)

/**
 * The home screen's rules that are about order and focus, kept free of Android so they can be
 * tested. They follow section 3.4 of the design review.
 */
object HomeRules {

    /**
     * Where focus should go when [tileKey] leaves its row (removed, hidden or moved to another shelf).
     * Returns candidate keys in order of preference; the caller uses the first one that exists:
     * the next tile in the row, the previous one, the same column in the next row, then the same
     * column in the previous row.
     */
    fun focusAfterTileGone(rows: List<HomeRowModel>, rowId: String, tileKey: String): List<String> {
        val rowIndex = rows.indexOfFirst { it.rowId == rowId }
        if (rowIndex < 0) return emptyList()
        val row = rows[rowIndex]
        val col = row.indexOf(tileKey)
        if (col < 0) return emptyList()
        val out = mutableListOf<String>()
        row.tiles.getOrNull(col + 1)?.let { out += it.key }
        row.tiles.getOrNull(col - 1)?.let { out += it.key }
        out += sameColumnElsewhere(rows, rowIndex, col)
        return out
    }

    /** Where focus goes when a whole row is hidden: the same column in the next row, then the previous. */
    fun focusAfterRowHidden(rows: List<HomeRowModel>, rowId: String, column: Int): List<String> {
        val rowIndex = rows.indexOfFirst { it.rowId == rowId }
        if (rowIndex < 0) return emptyList()
        return sameColumnElsewhere(rows, rowIndex, column)
    }

    private fun sameColumnElsewhere(rows: List<HomeRowModel>, rowIndex: Int, col: Int): List<String> {
        val out = mutableListOf<String>()
        rows.getOrNull(rowIndex + 1)?.tiles?.let { if (it.isNotEmpty()) out += it[col.coerceIn(0, it.lastIndex)].key }
        rows.getOrNull(rowIndex - 1)?.tiles?.let { if (it.isNotEmpty()) out += it[col.coerceIn(0, it.lastIndex)].key }
        return out
    }

    // Move mode ----------------------------------------------------------------------------------

    /** How far one D-pad press moves a lifted tile in [view], or null when that key does nothing. */
    fun moveDelta(view: HomeView, dir: Dir, gridColumns: Int): Int? = when (view) {
        HomeView.ROWS, HomeView.SPOTLIGHT -> when (dir) {
            Dir.LEFT -> -1
            Dir.RIGHT -> 1
            else -> null
        }
        HomeView.COLUMNS, HomeView.LIST -> when (dir) {
            Dir.UP -> -1
            Dir.DOWN -> 1
            else -> null
        }
        HomeView.RAIL_GRID, HomeView.WALL -> when (dir) {
            Dir.LEFT -> -1
            Dir.RIGHT -> 1
            Dir.UP -> -gridColumns.coerceAtLeast(1)
            Dir.DOWN -> gridColumns.coerceAtLeast(1)
        }
    }

    /** Applies one step of a move, keeping the tile inside the movable part of its row. */
    fun step(session: MoveSession, delta: Int, movableCount: Int): MoveSession {
        if (movableCount <= 0) return session
        val to = (session.currentIndex + delta).coerceIn(0, movableCount - 1)
        return session.copy(currentIndex = to)
    }

    /**
     * Shows [session] in [rows] before it is saved: the lifted tile sits at its current index among the
     * row's movable tiles. The tile list is otherwise unchanged.
     */
    fun applyMove(rows: List<HomeRowModel>, session: MoveSession?): List<HomeRowModel> {
        if (session == null) return rows
        return rows.map { row ->
            if (row.rowId != session.rowId) return@map row
            val movable = row.tiles.filter { it.movable }
            val moved = Ordering.moveTo(movable.map { it.key }, session.tileKey, session.currentIndex)
            val byKey = movable.associateBy { it.key }
            var next = 0
            row.copy(tiles = row.tiles.map { t -> if (t.movable) byKey.getValue(moved[next++]) else t })
        }
    }

    /** Index of [tileKey] among the row's movable tiles, or -1. This is the index the database wants. */
    fun movableIndex(row: HomeRowModel, tileKey: String): Int =
        row.tiles.filter { it.movable }.indexOfFirst { it.key == tileKey }

    // Row order ------------------------------------------------------------------------------------

    /**
     * Row move mode shows only visible rows, but the database orders all rows, hidden ones too.
     * Given the full order and the visible order, returns the index in the full order for a row moved
     * to [toVisibleIndex] among the visible rows.
     */
    fun fullIndexForVisibleMove(allIds: List<String>, visibleIds: List<String>, rowId: String, toVisibleIndex: Int): Int {
        val without = visibleIds.filter { it != rowId }
        val clamped = toVisibleIndex.coerceIn(0, without.size)
        val anchor = without.getOrNull(clamped)
        val allWithout = allIds.filter { it != rowId }
        return if (anchor == null) allWithout.size else allWithout.indexOf(anchor).coerceAtLeast(0)
    }

    /** When Back is pressed deep in a view: go to the first tile; if already there, go to the top bar. */
    fun backTarget(focusedKey: String?, firstKey: String?): BackTarget = when {
        focusedKey == null || firstKey == null -> BackTarget.NONE
        focusedKey != firstKey -> BackTarget.FIRST_TILE
        else -> BackTarget.TOP_BAR
    }

    enum class BackTarget { NONE, FIRST_TILE, TOP_BAR }

    /**
     * The key of the first focus stop in [view]: the first tile for most views, the first shelf name in
     * Rail + grid and the "All" chip on the Wall. Null when there is nothing to focus.
     */
    fun firstKey(view: HomeView, rows: List<HomeRowModel>): String? {
        val first = rows.firstOrNull() ?: return null
        return when (view) {
            HomeView.RAIL_GRID -> "rail:${first.rowId}"
            HomeView.WALL -> "chip:0"
            else -> first.tiles.firstOrNull()?.key
        }
    }
}
