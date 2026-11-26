package com.firestarterpro.templartv.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val PanelShape = RoundedCornerShape(20.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/** Dims the home screen and keeps focus and Back inside the panel. */
@Composable
fun ModalScrim(
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = alignment) {
        Box(
            Modifier
                .focusGroup()
                .focusProperties { exit = { FocusRequester.Cancel } },
        ) { content() }
    }
}

data class MenuEntry(val label: String, val hint: String = "", val onClick: () -> Unit)

@Composable
private fun PanelButton(label: String, hint: String = "", modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(ItemShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * A vertical menu with plain-verb labels. Focus starts on [initialIndex]. A click that arrives in the
 * first moments after opening is ignored, so letting go of the hold-OK that opened the menu cannot
 * trigger its first item.
 */
@Composable
fun MenuPanel(
    title: String,
    subtitle: String = "",
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.Center,
    initialIndex: Int = 0,
    onEntryFocused: ((Int) -> Unit)? = null,
) {
    val requesters = remember(entries.size) { List(entries.size) { FocusRequester() } }
    val openedAt = remember { SystemClock.uptimeMillis() }
    LaunchedEffect(entries.size) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { requesters.getOrNull(initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))?.requestFocus() }
    }
    ModalScrim(onDismiss, alignment) {
        Column(
            Modifier
                .padding(horizontal = 56.dp, vertical = 40.dp)
                .width(440.dp)
                .background(MaterialTheme.colorScheme.surface, PanelShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 2)
            }
            Spacer(Modifier.height(4.dp))
            entries.forEachIndexed { i, e ->
                PanelButton(
                    label = e.label,
                    hint = e.hint,
                    modifier = Modifier
                        .focusRequester(requesters[i])
                        .onFocusChanged { if (it.hasFocus) onEntryFocused?.invoke(i) },
                ) {
                    if (SystemClock.uptimeMillis() - openedAt > 350) e.onClick()
                }
            }
        }
    }
}

@Composable
fun InfoPanel(title: String, lines: List<String>, onDismiss: () -> Unit) {
    MenuPanel(
        title = title,
        subtitle = lines.joinToString("\n"),
        entries = listOf(MenuEntry("Close") { onDismiss() }),
        onDismiss = onDismiss,
    )
}

/** Type a short text. OK on the field opens the on-screen keyboard. */
@Composable
fun TextInputPanel(
    title: String,
    hint: String,
    initial: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
    ModalScrim(onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .background(MaterialTheme.colorScheme.surface, PanelShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Surface(
                onClick = {
                    runCatching { fieldFocus.requestFocus() }
                    keyboard?.show()
                },
                shape = ClickableSurfaceDefaults.shape(ItemShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (text.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                    )
                }
            }
            Text(
                "Press OK on the box to type. Then choose $confirmLabel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelButton(confirmLabel, modifier = Modifier.width(220.dp)) { if (text.isNotBlank()) onConfirm(text.trim()) }
                PanelButton("Cancel", modifier = Modifier.width(220.dp)) { onDismiss() }
            }
        }
    }
}

/** Switch home view. The home behind the panel previews each view as focus moves; OK keeps it. */
@Composable
fun ViewPickerPanel(
    current: HomeView,
    available: List<HomeView>,
    onPreview: (HomeView) -> Unit,
    onApply: (HomeView) -> Unit,
    onCancel: () -> Unit,
) {
    MenuPanel(
        title = "Home view",
        subtitle = "Move up and down to preview. OK keeps a view. Back keeps ${current.label}.",
        entries = available.map { v ->
            MenuEntry(v.label, if (v == current) "current" else "") { onApply(v) }
        },
        onDismiss = onCancel,
        alignment = Alignment.CenterStart,
        initialIndex = available.indexOf(current).coerceAtLeast(0),
        onEntryFocused = { i -> available.getOrNull(i)?.let(onPreview) },
    )
}

@Composable
fun RowManagerPanel(
    managed: List<ManagedRow>,
    currentView: HomeView,
    viewsAvailable: Boolean,
    onToggle: (ManagedRow) -> Unit,
    onMove: (ManagedRow) -> Unit,
    onChangeView: () -> Unit,
    onRestoreFavourites: () -> Unit,
    hiddenAppCount: Int,
    onShowHiddenApps: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    ModalScrim(onDismiss) {
        Column(
            Modifier
                .width(640.dp)
                .heightIn(max = 480.dp)
                .background(MaterialTheme.colorScheme.surface, PanelShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Row Manager", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Show or hide rows and change their order. Hidden rows keep everything in them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            if (viewsAvailable) {
                PanelButton("Home view", hint = currentView.label, modifier = Modifier.focusRequester(first)) { onChangeView() }
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                itemsIndexed(managed, key = { _, r -> r.rowId }) { i, row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            row.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (row.visible) 1f else 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        PanelButton(
                            if (row.visible) "Shown" else "Hidden",
                            modifier = Modifier.width(130.dp).then(if (!viewsAvailable && i == 0) Modifier.focusRequester(first) else Modifier),
                        ) { onToggle(row) }
                        if (row.visible) PanelButton("Move", modifier = Modifier.width(110.dp)) { onMove(row) } else Spacer(Modifier.width(110.dp))
                    }
                }
            }
            PanelButton("Restore removed favourites…") { onRestoreFavourites() }
            if (hiddenAppCount > 0) PanelButton("Show $hiddenAppCount hidden apps") { onShowHiddenApps() }
        }
    }
}

/** Collapsed list of row titles. Up and Down move the lifted row, OK saves, Back cancels. */
@Composable
fun RowMovePanel(
    visibleRows: List<ManagedRow>,
    rowId: String,
    onCommit: (visibleIndex: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val startIndex = visibleRows.indexOfFirst { it.rowId == rowId }.coerceAtLeast(0)
    var index by remember { mutableStateOf(startIndex) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    val order = remember(index, visibleRows) {
        com.firestarterpro.templartv.data.Ordering.moveTo(visibleRows.map { it.rowId }, rowId, index)
    }
    val titles = visibleRows.associate { it.rowId to it.title }
    ModalScrim(onCancel) {
        Column(
            Modifier
                .width(520.dp)
                .background(MaterialTheme.colorScheme.surface, PanelShape)
                .padding(24.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                        Key.DirectionDown -> { index = (index + 1).coerceAtMost(visibleRows.lastIndex); true }
                        Key.DirectionCenter, Key.Enter -> { onCommit(index); true }
                        else -> false
                    }
                }
                .focusRequester(focus)
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Move row", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("▲ ▼ move · OK place · Back cancel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            order.forEach { id ->
                val lifted = id == rowId
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, ItemShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        titles[id].orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (lifted) FontWeight.Bold else FontWeight.Normal,
                        color = if (lifted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Everything in a row, as a grid: the tiles behind "More from X" or "See all". */
@Composable
fun SeeAllPanel(
    title: String,
    tiles: List<HomeTile>,
    onClick: (HomeTile) -> Unit,
    onLongClick: (HomeTile) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    ModalScrim(onDismiss) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)).padding(horizontal = EdgePad, vertical = 36.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize().focusGroup(),
            ) {
                itemsIndexed(tiles, key = { _, t -> t.key }) { i, tile ->
                    TileCard(
                        tile = tile,
                        aspect = 16f / 9f,
                        modifier = Modifier.fillMaxWidth(),
                        focusModifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        compact = true,
                        onClick = { onClick(tile) },
                        onLongClick = { onLongClick(tile) },
                        onFocused = {},
                    )
                }
            }
        }
    }
}

/** Removed tiles can always be brought back. */
@Composable
fun RemovedPanel(
    title: String,
    items: List<Pair<Long, String>>,
    onRestore: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) {
        InfoPanel(title, listOf("Nothing has been removed from here."), onDismiss)
    } else {
        MenuPanel(
            title = title,
            subtitle = "OK brings an item back.",
            entries = items.take(8).map { (id, label) -> MenuEntry(label, "Restore") { onRestore(id) } },
            onDismiss = onDismiss,
        )
    }
}

/** Where the hint bar sits during move mode. */
@Composable
fun MoveHintBar(view: HomeView, modifier: Modifier = Modifier) {
    val arrows = when (view) {
        HomeView.ROWS, HomeView.SPOTLIGHT -> "◀ ▶"
        HomeView.COLUMNS, HomeView.LIST -> "▲ ▼"
        HomeView.RAIL_GRID, HomeView.WALL -> "◀ ▶ ▲ ▼"
    }
    Box(modifier.padding(bottom = 22.dp)) {
        Text(
            "$arrows move · OK place · Back cancel",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}
