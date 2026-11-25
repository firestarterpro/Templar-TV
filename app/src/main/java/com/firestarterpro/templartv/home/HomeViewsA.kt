package com.firestarterpro.templartv.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private const val Landscape = 16f / 9f

/** Rows: shelves stacked top to bottom, tiles left to right. The view that always works. */
@Composable
fun RowsView(s: HomeViewScope, modifier: Modifier = Modifier) {
    val rows = s.ui.rows
    val listState = rememberLazyListState()
    val rowStates = remember { HashMap<String, LazyListState>() }

    LaunchedEffect(s.focus.target, rows) {
        val key = s.focus.target ?: return@LaunchedEffect
        val ri = rows.indexOfFirst { r -> r.tiles.any { it.key == key } }
        if (ri < 0) return@LaunchedEffect
        runCatching { listState.scrollToItem(ri) }
        val ti = rows[ri].indexOf(key)
        runCatching { rowStates.getOrPut(rows[ri].rowId) { LazyListState() }.scrollToItem(ti) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .focusProperties {
                canFocus = s.interactive
                exit = { d -> if (d == FocusDirection.Up) s.topBarFocus else FocusRequester.Default }
            },
        contentPadding = PaddingValues(top = TopClear, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(rows, key = { _, r -> r.rowId }) { ri, row ->
            Column {
                RowTitle(row, Modifier.padding(start = EdgePad, bottom = 8.dp))
                val state = remember(row.rowId) { rowStates.getOrPut(row.rowId) { LazyListState() } }
                LazyRow(
                    state = state,
                    contentPadding = PaddingValues(horizontal = EdgePad, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .focusGroup()
                        .focusProperties {
                            exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
                        },
                ) {
                    itemsIndexed(row.tiles, key = { _, t -> t.key }) { ti, tile ->
                        s.Tile(
                            row, tile, Landscape,
                            modifier = Modifier.width(tileWidth(row.tileSize, tile)),
                            entry = ri == 0 && ti == 0,
                        )
                    }
                }
            }
        }
    }
}

/** Columns: each shelf is a column of tiles, several side by side. Left/Right changes column. */
@Composable
fun ColumnsView(s: HomeViewScope, modifier: Modifier = Modifier) {
    val rows = s.ui.rows
    val listState = rememberLazyListState()
    val colStates = remember { HashMap<String, LazyListState>() }

    LaunchedEffect(s.focus.target, rows) {
        val key = s.focus.target ?: return@LaunchedEffect
        val ci = rows.indexOfFirst { r -> r.tiles.any { it.key == key } }
        if (ci < 0) return@LaunchedEffect
        runCatching { listState.animateScrollToItem(ci) }
        val ti = rows[ci].indexOf(key)
        runCatching { colStates.getOrPut(rows[ci].rowId) { LazyListState() }.scrollToItem(ti) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap = 14.dp
        val n = s.ui.columns.coerceIn(3, 6)
        val colWidth = (maxWidth - EdgePad * 2 - gap * (n - 1)) / n
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .focusProperties {
                    canFocus = s.interactive
                    exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default }
                },
            contentPadding = PaddingValues(horizontal = EdgePad),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(rows, key = { _, r -> r.rowId }) { ci, row ->
                Column(Modifier.width(colWidth).fillParentMaxHeight()) {
                    Spacer(Modifier.height(TopClear))
                    RowTitle(row, Modifier.padding(bottom = 8.dp))
                    val state = remember(row.rowId) { colStates.getOrPut(row.rowId) { LazyListState() } }
                    LazyColumn(
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .focusGroup()
                            .focusProperties {
                                exit = { d ->
                                    when (d) {
                                        FocusDirection.Up -> s.topBarFocus
                                        FocusDirection.Down -> FocusRequester.Cancel
                                        else -> FocusRequester.Default
                                    }
                                }
                            },
                        contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(row.tiles, key = { _, t -> t.key }) { ti, tile ->
                            s.Tile(row, tile, Landscape, Modifier.fillMaxWidth(), entry = ci == 0 && ti == 0)
                        }
                    }
                }
            }
        }
    }
}

/** Rail + grid: shelf names on the left; the chosen shelf fills the right as a grid. */
@Composable
fun RailGridView(s: HomeViewScope, modifier: Modifier = Modifier) {
    val rows = s.ui.rows
    var selectedId by remember { mutableStateOf(rows.firstOrNull()?.rowId) }
    val selected = rows.firstOrNull { it.rowId == selectedId } ?: rows.first()
    val selectedIndex = rows.indexOf(selected).coerceAtLeast(0)
    val gridFirst = remember { FocusRequester() }
    val railSelected = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val railState = rememberLazyListState()

    LaunchedEffect(s.focus.target, rows) {
        val key = s.focus.target ?: return@LaunchedEffect
        val row = rows.firstOrNull { r -> r.tiles.any { it.key == key } } ?: return@LaunchedEffect
        selectedId = row.rowId
        runCatching { railState.scrollToItem(rows.indexOf(row).coerceAtLeast(0)) }
        runCatching { gridState.scrollToItem(row.indexOf(key).coerceAtLeast(0)) }
    }

    Row(modifier.fillMaxSize().focusProperties { canFocus = s.interactive }) {
        LazyColumn(
            state = railState,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .focusGroup()
                .focusProperties {
                    exit = { d ->
                        when (d) {
                            FocusDirection.Up -> s.topBarFocus
                            FocusDirection.Right -> gridFirst
                            else -> FocusRequester.Cancel
                        }
                    }
                },
            contentPadding = PaddingValues(start = EdgePad, end = 8.dp, top = TopClear, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(rows, key = { _, r -> r.rowId }) { i, row ->
                val requester = when {
                    i == 0 -> s.contentFocus
                    row.rowId == selected.rowId -> railSelected
                    else -> null
                }
                RailItem(
                    label = row.title,
                    count = row.realTiles.size,
                    selected = row.rowId == selected.rowId,
                    requester = requester,
                    onFocused = {
                        selectedId = row.rowId
                        s.focus.focusedKey = "rail:${row.rowId}"
                        s.focus.focusedRowId = row.rowId
                    },
                    onClick = { runCatching { gridFirst.requestFocus() } },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusGroup()
                .focusProperties {
                    exit = { d ->
                        when (d) {
                            FocusDirection.Left -> if (selectedIndex == 0) s.contentFocus else railSelected
                            FocusDirection.Up -> s.topBarFocus
                            else -> FocusRequester.Cancel
                        }
                    }
                },
            contentPadding = PaddingValues(start = 12.dp, end = EdgePad, top = TopClear, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(selected.tiles, key = { _, t -> t.key }) { ti, tile ->
                s.Tile(selected, tile, Landscape, Modifier.fillMaxWidth(), requester = gridFirst.takeIf { ti == 0 }, compact = true)
            }
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    count: Int,
    selected: Boolean,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val base = if (requester != null) Modifier.focusRequester(requester) else Modifier
    Surface(
        onClick = onClick,
        modifier = base.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("$count", style = MaterialTheme.typography.labelLarge)
        }
    }
}
