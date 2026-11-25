package com.firestarterpro.templartv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage

private const val Landscape = 16f / 9f

/** Spotlight: big artwork for the focused show, one strip of tiles below. Up/Down switches shelf. */
@Composable
fun SpotlightView(s: HomeViewScope, modifier: Modifier = Modifier) {
    val rows = s.ui.rows
    var rowIndex by remember { mutableStateOf(0) }
    val ri = rowIndex.coerceIn(0, rows.lastIndex)
    val row = rows[ri]
    val stripState = rememberLazyListState()
    val focused = row.tiles.firstOrNull { it.key == s.focus.focusedKey }
        ?: row.realTiles.firstOrNull() ?: row.tiles.first()

    LaunchedEffect(s.focus.target, rows) {
        val key = s.focus.target ?: return@LaunchedEffect
        val idx = rows.indexOfFirst { r -> r.tiles.any { it.key == key } }
        if (idx < 0) return@LaunchedEffect
        rowIndex = idx
        runCatching { stripState.scrollToItem(rows[idx].indexOf(key).coerceAtLeast(0)) }
    }

    fun switchTo(newIndex: Int) {
        val col = row.indexOf(s.focus.focusedKey ?: "").coerceAtLeast(0)
        val next = rows[newIndex]
        rowIndex = newIndex
        s.focus.request(next.tiles[col.coerceIn(0, next.tiles.lastIndex)].key)
    }

    Box(modifier.fillMaxSize().focusProperties { canFocus = s.interactive }) {
        val bg = MaterialTheme.colorScheme.background
        Crossfade(targetState = focused.artworkUri, animationSpec = tween(450), label = "spotlightBg") { art ->
            if (art != null) {
                AsyncImage(model = art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to bg, 0.6f to bg.copy(alpha = 0.55f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to bg)))

        Column(Modifier.fillMaxSize().padding(horizontal = EdgePad)) {
            Spacer(Modifier.height(TopClear))
            SpotlightInfo(focused, row, Modifier.width(560.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "${row.title}   ${ri + 1} of ${rows.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LazyRow(
                state = stripState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .focusGroup()
                    .focusProperties {
                        exit = { d ->
                            when (d) {
                                FocusDirection.Up -> s.topBarFocus
                                FocusDirection.Left, FocusDirection.Right, FocusDirection.Down -> FocusRequester.Cancel
                                else -> FocusRequester.Default
                            }
                        }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> {
                                if (ri < rows.lastIndex) switchTo(ri + 1)
                                true
                            }
                            Key.DirectionUp -> if (ri > 0) { switchTo(ri - 1); true } else false
                            else -> false
                        }
                    },
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(row.tiles, key = { _, t -> t.key }) { ti, tile ->
                    s.Tile(row, tile, Landscape, Modifier.width(tileWidth(com.firestarterpro.templartv.data.layout.TileSize.SMALL, tile) + 32.dp), entry = ti == 0)
                }
            }
        }
    }
}

@Composable
private fun SpotlightInfo(tile: HomeTile, row: HomeRowModel, modifier: Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    Column(modifier) {
        val label = tile.providerLabel.ifBlank { row.title }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            tile.title, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = onBg, maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (tile.subtitle.isNotBlank()) Text(tile.subtitle, style = MaterialTheme.typography.titleMedium, color = onBg.copy(alpha = 0.75f))
        progressWords(tile)?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = onBg.copy(alpha = 0.75f)) }
        if (tile.description.isNotBlank()) {
            Text(tile.description, style = MaterialTheme.typography.bodyLarge, color = onBg.copy(alpha = 0.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (row.realTiles.isEmpty() && row.help.isNotBlank()) {
            Text(row.help, style = MaterialTheme.typography.bodyLarge, color = onBg.copy(alpha = 0.7f))
        }
    }
}

/** Wall: one big grid of everything with filter chips across the top. */
@Composable
fun WallView(s: HomeViewScope, modifier: Modifier = Modifier) {
    val rows = s.ui.rows
    var chip by remember { mutableStateOf(0) }
    val selectedChip = chip.coerceIn(0, rows.size)
    val entries: List<Pair<HomeRowModel, HomeTile>> = remember(rows, selectedChip) {
        if (selectedChip == 0) rows.flatMap { r -> r.tiles.filter { it.isRealContent }.map { r to it } }
        else rows[selectedChip - 1].let { r -> r.tiles.map { r to it } }
    }
    val gridFirst = remember { FocusRequester() }
    val chipSelected = remember { FocusRequester() }
    val chipState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    SideEffect { s.focus.wallAll = selectedChip == 0 }

    LaunchedEffect(s.focus.target, rows) {
        val key = s.focus.target ?: return@LaunchedEffect
        val idx = entries.indexOfFirst { it.second.key == key }
        if (idx >= 0) {
            runCatching { gridState.scrollToItem(idx) }
            return@LaunchedEffect
        }
        val rowIdx = rows.indexOfFirst { r -> r.tiles.any { it.key == key } }
        if (rowIdx >= 0) chip = rowIdx + 1
    }

    Column(modifier.fillMaxSize().focusProperties { canFocus = s.interactive }) {
        Spacer(Modifier.height(TopClear))
        LazyRow(
            state = chipState,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusProperties {
                    exit = { d ->
                        when (d) {
                            FocusDirection.Up -> s.topBarFocus
                            FocusDirection.Down -> gridFirst
                            else -> FocusRequester.Cancel
                        }
                    }
                },
            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val labels = listOf("All") + rows.map { it.title }
            itemsIndexed(labels) { i, label ->
                val requester = when {
                    i == 0 -> s.contentFocus
                    i == selectedChip -> chipSelected
                    else -> null
                }
                WallChip(
                    label = label,
                    selected = i == selectedChip,
                    requester = requester,
                    onFocused = {
                        chip = i
                        s.focus.focusedKey = "chip:$i"
                        s.focus.focusedRowId = if (i == 0) null else rows.getOrNull(i - 1)?.rowId
                    },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = gridState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusGroup()
                .focusProperties {
                    exit = { d ->
                        when (d) {
                            FocusDirection.Up -> if (selectedChip == 0) s.contentFocus else chipSelected
                            else -> FocusRequester.Cancel
                        }
                    }
                },
            contentPadding = PaddingValues(horizontal = EdgePad, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.second.key }) { i, (row, tile) ->
                s.Tile(row, tile, Landscape, Modifier.fillMaxWidth(), requester = gridFirst.takeIf { i == 0 }, compact = true)
            }
        }
    }
}

@Composable
private fun WallChip(label: String, selected: Boolean, requester: FocusRequester?, onFocused: () -> Unit) {
    val base = if (requester != null) Modifier.focusRequester(requester) else Modifier
    Surface(
        onClick = {},
        modifier = base.onFocusChanged { if (it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

private sealed interface ListEntry {
    data class Header(val row: HomeRowModel) : ListEntry
    data class Item(val row: HomeRowModel, val tile: HomeTile) : ListEntry
}

/** List: text first. Title, provider, episode and progress on one line. Left/Right jumps a shelf. */
@Composable
fun ListView(s: HomeViewScope, modifier: Modifier = Modifier) {
    val rows = s.ui.rows
    val entries = remember(rows) { rows.flatMap { r -> listOf<ListEntry>(ListEntry.Header(r)) + r.tiles.map { ListEntry.Item(r, it) } } }
    val listState = rememberLazyListState()

    LaunchedEffect(s.focus.target, rows) {
        val key = s.focus.target ?: return@LaunchedEffect
        val idx = entries.indexOfFirst { it is ListEntry.Item && it.tile.key == key }
        if (idx >= 0) runCatching { listState.scrollToItem(idx) }
    }

    fun jump(delta: Int) {
        val current = rows.indexOfFirst { it.rowId == s.focus.focusedRowId }
        val target = rows.getOrNull((if (current < 0) 0 else current) + delta) ?: return
        target.tiles.firstOrNull()?.let { s.focus.request(it.key) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .focusGroup()
            .focusProperties {
                canFocus = s.interactive
                exit = { d ->
                    when (d) {
                        FocusDirection.Up -> s.topBarFocus
                        FocusDirection.Left, FocusDirection.Right -> FocusRequester.Cancel
                        else -> FocusRequester.Default
                    }
                }
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { jump(1); true }
                    Key.DirectionLeft -> { jump(-1); true }
                    else -> false
                }
            },
        contentPadding = PaddingValues(start = EdgePad, end = EdgePad, top = TopClear, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(entries, key = { _, e -> if (e is ListEntry.Item) e.tile.key else "h:" + (e as ListEntry.Header).row.rowId }) { i, entry ->
            when (entry) {
                is ListEntry.Header -> Column(Modifier.padding(top = if (i == 0) 0.dp else 14.dp, bottom = 2.dp)) {
                    RowTitle(entry.row)
                }
                is ListEntry.Item -> ListLine(s, entry.row, entry.tile, entry = i == 1)
            }
        }
    }
}

@Composable
private fun ListLine(s: HomeViewScope, row: HomeRowModel, tile: HomeTile, entry: Boolean) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val lifted = s.movingKey == tile.key
    Box(Modifier.fillMaxWidth().trackedFocusBox(s, tile.key, entry)) {
        Surface(
            onClick = { s.onClick(row, tile) },
            onLongClick = { s.onLongClick(row, tile) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.hasFocus) { s.focus.focusedKey = tile.key; s.focus.focusedRowId = row.rowId } },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (lifted) 1.03f else 1.01f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (lifted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                contentColor = onBg,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (tile.kind == TileKind.ADD || tile.kind == TileKind.MORE) {
                    Text(
                        text = if (tile.kind == TileKind.ADD) "+  Add a show" else tile.title + (if (tile.subtitle.isNotBlank()) "  ·  ${tile.subtitle}" else ""),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    Box(Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
                        if (tile.artworkUri != null) {
                            AsyncImage(model = tile.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tile.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (tile.subtitle.isNotBlank()) {
                            Text(tile.subtitle, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (tile.providerLabel.isNotBlank()) {
                        Text(tile.providerLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    progressWords(tile)?.let { words ->
                        Column(Modifier.width(210.dp)) {
                            tile.progress?.let { ProgressMark(it, tile.progressKind) }
                            Text(words, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (tile.watched) Text("Watched", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Modifier.trackedFocusBox(s: HomeViewScope, key: String, entry: Boolean): Modifier =
    this.trackedFocus(s.focus, key, s.contentFocus.takeIf { entry })
