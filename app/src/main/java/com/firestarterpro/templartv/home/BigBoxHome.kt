package com.firestarterpro.templartv.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.firestarterpro.templartv.data.ProviderIds
import com.firestarterpro.templartv.data.layout.DefaultRows
import com.firestarterpro.templartv.data.layout.RowKind
import com.firestarterpro.templartv.data.shelf.ShelfSource
import com.firestarterpro.templartv.domain.AppEntry
import com.firestarterpro.templartv.domain.ContentCard

/** Only the very first appearance takes focus by itself, so tab sweeps do not have focus stolen. */
private var initialFocusDone = false

/** Which editor or menu is open over the home screen. */
sealed interface Overlay {
    data object None : Overlay
    data class TileMenu(val rowId: String, val tile: HomeTile, val more: Boolean = false) : Overlay
    data class RowMenu(val rowId: String) : Overlay
    data object RowManager : Overlay
    data object ViewPicker : Overlay
    data class SeeAll(val rowId: String) : Overlay
    data class AddShow(val rowId: String) : Overlay
    data class Rename(val rowId: String) : Overlay
    data class Removed(val rowId: String) : Overlay
    data class Evidence(val tile: HomeTile) : Overlay
    data class ChangeProvider(val tile: HomeTile) : Overlay
    data class RowMove(val rowId: String) : Overlay
}

/**
 * The big-box home: one data model, six views, and the editing that works the same in all of them
 * (hold OK for the tile menu, move mode, "Edit this row…"). Everything it edits goes through the
 * data layer's feature flags, so turning a flag off removes the matching action.
 */
@Composable
fun BigBoxHome(
    apps: List<AppEntry>,
    cards: List<ContentCard>,
    continueCards: List<ContentCard>,
    favouriteApps: Set<String>,
    onLaunchApp: (AppEntry) -> Unit,
    onLaunchCard: (ContentCard) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onOpenSearch: () -> Unit,
    onFailed: () -> Unit,
    topBarFocus: FocusRequester,
    contentFocus: FocusRequester,
    resetSignal: Int,
    modifier: Modifier = Modifier,
    vm: BigBoxViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = remember { FocusCoordinator() }
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var previewView by remember { mutableStateOf<HomeView?>(null) }
    var contentHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(ui.failed) { if (ui.failed) onFailed() }

    LaunchedEffect(apps, cards, continueCards, favouriteApps) {
        vm.setExternal(ExternalInput(continueCards = continueCards, allCards = cards, apps = apps, favouriteApps = favouriteApps))
    }

    val view = previewView ?: ui.view
    val rows = ui.rows
    val appMap = remember(apps) { apps.associateBy { it.packageName } }
    val cardMap = remember(cards, continueCards) { (cards + continueCards).associateBy { it.id } }
    val tileRows = remember(rows) {
        HashMap<String, HomeRowModel>().also { m -> rows.forEach { r -> r.tiles.forEach { t -> m[t.key] = r } } }
    }
    val gridColumns = if (view == HomeView.WALL) 6 else 5

    fun refocus(key: String?) {
        if (key != null && tileRows.containsKey(key)) focus.request(key) else runCatching { contentFocus.requestFocus() }
    }

    fun close(restore: String?) {
        overlay = Overlay.None
        restoreKey = null
        refocus(restore)
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun open(row: HomeRowModel, tile: HomeTile) {
        when (tile.kind) {
            TileKind.APP -> appMap[tile.packageName]?.let(onLaunchApp)
            TileKind.CHANNEL -> cardMap[tile.ref]?.let(onLaunchCard)
            TileKind.SHELF -> vm.openShelf(tile) { ok -> if (!ok) toast("Couldn't open ${tile.title}") }
            TileKind.FAVOURITE -> if (!vm.open(tile)) onOpenSearch()
            TileKind.ADD -> { restoreKey = tile.key; overlay = Overlay.AddShow(row.rowId) }
            TileKind.MORE -> { restoreKey = tile.key; overlay = Overlay.SeeAll(row.rowId) }
        }
    }

    fun longPress(row: HomeRowModel, tile: HomeTile) {
        if (tile.kind == TileKind.APP) {
            appMap[tile.packageName]?.let(onAppLongPress)
            return
        }
        if (restoreKey == null) restoreKey = focus.focusedKey
        overlay = Overlay.TileMenu(row.rowId, tile)
    }

    fun act(action: MenuAction, row: HomeRowModel, tile: HomeTile) {
        when (action) {
            MenuAction.OPEN -> { val k = restoreKey; close(k); open(row, tile) }
            MenuAction.ADD_FAVOURITE -> { vm.addFavourite(tile); close(restoreKey) }
            MenuAction.REMOVE_FAVOURITE -> {
                val next = if (tile.kind == TileKind.FAVOURITE) HomeRules.focusAfterTileGone(rows, row.rowId, tile.key).firstOrNull() else restoreKey
                vm.removeFavouriteByTile(tile, ui)
                close(next)
            }
            MenuAction.MOVE -> {
                vm.startMove(row, tile)
                overlay = Overlay.None
                focus.request(tile.key)
            }
            MenuAction.REMOVE -> {
                val next = HomeRules.focusAfterTileGone(rows, row.rowId, tile.key).firstOrNull()
                vm.removeTile(tile)
                close(next)
            }
            MenuAction.MORE -> overlay = Overlay.TileMenu(row.rowId, tile, more = true)
            MenuAction.MARK_WATCHED -> { vm.setWatched(tile, true); close(restoreKey) }
            MenuAction.MARK_UNWATCHED -> { vm.setWatched(tile, false); close(restoreKey) }
            MenuAction.CHANGE_PROVIDER -> overlay = Overlay.ChangeProvider(tile)
            MenuAction.SHOW_EVIDENCE -> overlay = Overlay.Evidence(tile)
            MenuAction.ADD_SHOW_HERE -> overlay = Overlay.AddShow(row.rowId)
            MenuAction.EDIT_ROW -> overlay = Overlay.RowMenu(row.rowId)
        }
    }

    // Hooks other screens (the quick menu) can use.
    val latestUi by rememberUpdatedState(ui)
    fun cycleTo(dir: Int) {
        val list = latestUi.availableViews
        if (list.size > 1) {
            val i = list.indexOf(latestUi.view).coerceAtLeast(0)
            vm.setView(list[(i + dir + list.size) % list.size])
        }
    }
    // A request made while this screen wasn't showing (quick menu over another app).
    LaunchedEffect(Unit) {
        when (HomeActions.consumePending()) {
            HomeRequest.ROW_MANAGER -> overlay = Overlay.RowManager
            HomeRequest.VIEW_PICKER -> overlay = Overlay.ViewPicker
            HomeRequest.NEXT_VIEW -> cycleTo(1)
            HomeRequest.PREVIOUS_VIEW -> cycleTo(-1)
            null -> Unit
        }
    }
    LaunchedEffect(Unit) { HomeActions.openRowManager.collect { overlay = Overlay.RowManager } }
    LaunchedEffect(Unit) { HomeActions.openViewPicker.collect { overlay = Overlay.ViewPicker } }
    LaunchedEffect(Unit) {
        HomeActions.cycleView.collect { dir -> cycleTo(dir) }
    }

    // The first focus, once, when there is something to focus.
    LaunchedEffect(ui.ready, rows.isNotEmpty()) {
        if (initialFocusDone || !ui.ready || overlay !is Overlay.None) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (runCatching { contentFocus.requestFocus() }.isSuccess) {
                initialFocusDone = true
                return@LaunchedEffect
            }
        }
    }

    // Home pressed while already home: cancel any edit and go back to the start.
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            vm.cancelMove()
            overlay = Overlay.None
            previewView = null
            val first = HomeRules.firstKey(view, rows)
            if (first != null && tileRows.containsKey(first)) focus.request(first) else runCatching { contentFocus.requestFocus() }
        }
    }

    // A view chosen from the picker stays shown until the saved setting catches up.
    LaunchedEffect(ui.view) { if (overlay is Overlay.None) previewView = null }

    // Back from deep in a view: first tile, then the top bar (design review 2).
    BackHandler(enabled = overlay is Overlay.None && ui.move == null && contentHasFocus && rows.isNotEmpty()) {
        val firstKey = HomeRules.firstKey(view, rows)
        when (HomeRules.backTarget(focus.focusedKey, firstKey)) {
            HomeRules.BackTarget.FIRST_TILE ->
                if (firstKey != null && tileRows.containsKey(firstKey)) focus.request(firstKey) else runCatching { contentFocus.requestFocus() }
            HomeRules.BackTarget.TOP_BAR -> runCatching { topBarFocus.requestFocus() }
            HomeRules.BackTarget.NONE -> Unit
        }
    }
    // Back during a move puts the tile back where it was.
    BackHandler(enabled = ui.move != null) {
        val key = ui.move?.tileKey
        vm.cancelMove()
        focus.request(key)
    }

    val scope = HomeViewScope(
        ui = ui,
        focus = focus,
        topBarFocus = topBarFocus,
        contentFocus = contentFocus,
        interactive = overlay is Overlay.None,
        onClick = { row, tile -> open(row, tile) },
        onLongClick = { row, tile -> longPress(row, tile) },
        rowOf = { tileRows[it.key] },
    )

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e -> handleMoveKey(e.type, e.key, ui, view, gridColumns, vm, focus) },
    ) {
        CompositionLocalProvider(LocalAppEntries provides appMap) {
            Box(Modifier.fillMaxSize().onFocusChanged { contentHasFocus = it.hasFocus }) {
                when {
                    !ui.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    rows.isEmpty() -> EmptyHome({ overlay = Overlay.RowManager }, contentFocus)
                    else -> when (view) {
                        HomeView.ROWS -> RowsView(scope)
                        HomeView.COLUMNS -> ColumnsView(scope)
                        HomeView.RAIL_GRID -> RailGridView(scope)
                        HomeView.SPOTLIGHT -> SpotlightView(scope)
                        HomeView.WALL -> WallView(scope)
                        HomeView.LIST -> ListView(scope)
                    }
                }
            }
        }

        if (ui.move != null) MoveHintBar(view, Modifier.align(Alignment.BottomCenter))

        Overlays(
            overlay = overlay,
            ui = ui,
            rows = rows,
            view = view,
            vm = vm,
            moveAllowed = !(view == HomeView.WALL && focus.wallAll),
            restoreKey = restoreKey,
            onSetOverlay = { overlay = it },
            onClose = { close(it) },
            onAct = ::act,
            onOpenTile = { row, tile -> open(row, tile) },
            onPreview = { previewView = it },
            onApplyView = { v ->
                vm.setView(v)
                previewView = v
                overlay = Overlay.None
                refocus(focus.focusedKey)
            },
            onCancelView = {
                previewView = null
                overlay = Overlay.None
                refocus(focus.focusedKey)
            },
            onHideRow = { row ->
                val col = row.indexOf(focus.focusedKey ?: "").coerceAtLeast(0)
                val next = HomeRules.focusAfterRowHidden(rows, row.rowId, col).firstOrNull()
                vm.setRowVisible(row.rowId, false)
                close(next)
            },
        )
    }
}

/** Move mode owns the D-pad: direction keys move the lifted tile and OK drops it. */
private fun handleMoveKey(
    type: KeyEventType,
    key: Key,
    ui: BigBoxUi,
    view: HomeView,
    gridColumns: Int,
    vm: BigBoxViewModel,
    focus: FocusCoordinator,
): Boolean {
    val move = ui.move ?: return false
    val isOk = key == Key.DirectionCenter || key == Key.Enter
    if (type == KeyEventType.KeyUp) return isOk // swallow the release so the tile is not opened
    if (type != KeyEventType.KeyDown) return false
    val dir = when (key) {
        Key.DirectionLeft -> Dir.LEFT
        Key.DirectionRight -> Dir.RIGHT
        Key.DirectionUp -> Dir.UP
        Key.DirectionDown -> Dir.DOWN
        else -> null
    }
    return when {
        dir != null -> {
            HomeRules.moveDelta(view, dir, gridColumns)?.let {
                vm.stepMove(it)
                focus.request(move.tileKey)
            }
            true
        }
        isOk -> {
            vm.commitMove()
            focus.request(move.tileKey)
            true
        }
        else -> false
    }
}

@Composable
private fun Overlays(
    overlay: Overlay,
    ui: BigBoxUi,
    rows: List<HomeRowModel>,
    view: HomeView,
    vm: BigBoxViewModel,
    moveAllowed: Boolean,
    restoreKey: String?,
    onSetOverlay: (Overlay) -> Unit,
    onClose: (String?) -> Unit,
    onAct: (MenuAction, HomeRowModel, HomeTile) -> Unit,
    onOpenTile: (HomeRowModel, HomeTile) -> Unit,
    onPreview: (HomeView) -> Unit,
    onApplyView: (HomeView) -> Unit,
    onCancelView: () -> Unit,
    onHideRow: (HomeRowModel) -> Unit,
) {
    fun rowOf(id: String) = rows.firstOrNull { it.rowId == id }

    when (overlay) {
        Overlay.None -> Unit

        is Overlay.TileMenu -> {
            val row = rowOf(overlay.rowId)
            if (row == null) {
                LaunchedEffect(Unit) { onClose(null) }
                return
            }
            val ctx = TileMenuContext(
                tile = overlay.tile,
                row = row,
                isFavourite = vm.isFavourite(overlay.tile, ui),
                shelfEditing = ui.shelfEditing,
                favouritesOn = ui.favouritesOn,
                rowEditing = ui.rowEditing,
                moveAllowed = moveAllowed && row.indexOf(overlay.tile.key) >= 0,
            )
            val actions = if (overlay.more) TileMenu.more(ctx) else TileMenu.primary(ctx)
            if (actions.isEmpty()) {
                LaunchedEffect(Unit) { onClose(restoreKey) }
                return
            }
            MenuPanel(
                title = overlay.tile.title,
                subtitle = listOf(overlay.tile.providerLabel, overlay.tile.subtitle).filter { it.isNotBlank() }.joinToString("  ·  "),
                entries = actions.map { a -> MenuEntry(a.label) { onAct(a, row, overlay.tile) } },
                onDismiss = {
                    if (overlay.more) onSetOverlay(Overlay.TileMenu(overlay.rowId, overlay.tile, more = false)) else onClose(restoreKey)
                },
            )
        }

        is Overlay.RowMenu -> {
            val row = rowOf(overlay.rowId)
            val layout = ui.layout.firstOrNull { it.rowId == overlay.rowId }
            if (row == null || layout == null) {
                LaunchedEffect(Unit) { onClose(null) }
                return
            }
            val entries = buildList {
                if (ui.rowEditing) {
                    add(MenuEntry("Move row") { onSetOverlay(Overlay.RowMove(row.rowId)) })
                    add(MenuEntry("Hide row") { onHideRow(row) })
                    add(MenuEntry("Rename") { onSetOverlay(Overlay.Rename(row.rowId)) })
                    add(MenuEntry("Tile size", layout.tileSize.name.lowercase().replaceFirstChar { it.uppercase() }) {
                        vm.cycleTileSize(row.rowId); onClose(restoreKey)
                    })
                    if (row.kind == RowKind.PROVIDER_SHELF) {
                        val inline = RowOptions.inlineProviderRows(layout.optionsJson)
                        add(MenuEntry("Show ${ProviderCatalog.label(row.provider)} rows on home", if (inline) "On" else "Off") {
                            vm.setInlineProviderRows(row.rowId, !inline); onClose(restoreKey)
                        })
                    }
                }
                if (row.kind == RowKind.PROVIDER_SHELF || row.kind == RowKind.FAVOURITE_SHOWS) {
                    add(MenuEntry("Restore removed items…") { onSetOverlay(Overlay.Removed(row.rowId)) })
                }
                add(MenuEntry("Row Manager…") { onSetOverlay(Overlay.RowManager) })
                add(MenuEntry("Change home view…", view.label) { onSetOverlay(Overlay.ViewPicker) })
            }
            MenuPanel("Edit \"${row.title}\"", entries = entries, onDismiss = { onClose(restoreKey) })
        }

        Overlay.RowManager -> RowManagerPanel(
            managed = ui.managed,
            currentView = view,
            viewsAvailable = ui.availableViews.size > 1,
            onToggle = { m -> vm.setRowVisible(m.rowId, !m.visible) },
            onMove = { m -> onSetOverlay(Overlay.RowMove(m.rowId)) },
            onChangeView = { onSetOverlay(Overlay.ViewPicker) },
            onRestoreFavourites = { onSetOverlay(Overlay.Removed(DefaultRows.FAVOURITE_SHOWS)) },
            onDismiss = { onClose(restoreKey) },
        )

        Overlay.ViewPicker -> ViewPickerPanel(
            current = ui.view,
            available = ui.availableViews,
            onPreview = onPreview,
            onApply = onApplyView,
            onCancel = onCancelView,
        )

        is Overlay.RowMove -> {
            val visible = ui.managed.filter { m -> rows.any { it.rowId == m.rowId } }
            RowMovePanel(
                visibleRows = visible,
                rowId = overlay.rowId,
                onCommit = { visibleIndex ->
                    val full = HomeRules.fullIndexForVisibleMove(
                        ui.managed.map { it.rowId }, visible.map { it.rowId }, overlay.rowId, visibleIndex,
                    )
                    vm.moveRow(overlay.rowId, full)
                    onClose(restoreKey)
                },
                onCancel = { onClose(restoreKey) },
            )
        }

        is Overlay.SeeAll -> {
            val row = rowOf(overlay.rowId)
            if (row == null) {
                LaunchedEffect(Unit) { onClose(null) }
                return
            }
            SeeAllPanel(
                title = row.title,
                tiles = row.moreTiles,
                onClick = { tile -> onClose(restoreKey); onOpenTile(row, tile) },
                onLongClick = { tile -> onSetOverlay(Overlay.TileMenu(row.rowId, tile)) },
                onDismiss = { onClose(restoreKey) },
            )
        }

        is Overlay.AddShow -> {
            val row = rowOf(overlay.rowId)
            if (row == null) {
                LaunchedEffect(Unit) { onClose(null) }
                return
            }
            TextInputPanel(
                title = "Add a show to ${row.title}",
                hint = "Show title",
                confirmLabel = "Add",
                onConfirm = { text -> vm.addShow(row, text); onClose(restoreKey) },
                onDismiss = { onClose(restoreKey) },
            )
        }

        is Overlay.Rename -> {
            val layout = ui.layout.firstOrNull { it.rowId == overlay.rowId }
            val current = rowOf(overlay.rowId)?.title.orEmpty()
            TextInputPanel(
                title = "Rename row",
                hint = "Row name",
                initial = current,
                confirmLabel = "Save",
                onConfirm = { text -> if (layout != null) vm.renameRow(layout.rowId, text); onClose(restoreKey) },
                onDismiss = { onClose(restoreKey) },
            )
        }

        is Overlay.Removed -> {
            val layout = ui.layout.firstOrNull { it.rowId == overlay.rowId }
            if (layout?.kind == RowKind.FAVOURITE_SHOWS) {
                val removed by remember { vm.removedFavourites() }.collectAsStateWithLifecycle(initialValue = emptyList())
                RemovedPanel(
                    title = "Removed favourites",
                    items = removed.map { it.id to it.title },
                    onRestore = { vm.restoreFavourite(it) },
                    onDismiss = { onClose(restoreKey) },
                )
            } else {
                val provider = layout?.provider.orEmpty()
                val removed by remember(provider) { vm.removedShelf(provider) }.collectAsStateWithLifecycle(initialValue = emptyList())
                RemovedPanel(
                    title = "Removed from ${ProviderCatalog.label(provider)}",
                    items = removed.map { it.id to it.title },
                    onRestore = { vm.restoreShelfEntry(it) },
                    onDismiss = { onClose(restoreKey) },
                )
            }
        }

        is Overlay.Evidence -> {
            val t = overlay.tile
            val lines = buildList {
                add(
                    when (t.source) {
                        ShelfSource.PROVIDER_ROW -> "Source: the provider's own row"
                        ShelfSource.MEDIA_SESSION -> "Source: seen playing (media session)"
                        ShelfSource.TEMPLAR_LAUNCH -> "Source: opened from Templar TV"
                        ShelfSource.MANUAL -> "Source: added by you"
                        null -> if (t.kind == TileKind.FAVOURITE) "Source: added to your favourites" else "Source: not recorded"
                    },
                )
                add(if (t.confirmed) "Title: confirmed by you" else "Title: not confirmed")
                add(progressWords(t)?.let { "Progress: $it" } ?: "Progress: position not known")
                add(if (t.deepLink.isNotBlank()) "Opens: straight to the title" else "Opens: the provider's app")
            }
            InfoPanel(t.title, lines) { onClose(restoreKey) }
        }

        is Overlay.ChangeProvider -> {
            val t = overlay.tile
            val choices = ProviderIds.PRIORITY.filter { it != t.providerId }
            MenuPanel(
                title = "Move \"${t.title}\" to",
                entries = choices.map { p -> MenuEntry(ProviderCatalog.label(p)) { vm.changeProvider(t, p); onClose(restoreKey) } },
                onDismiss = { onClose(restoreKey) },
            )
        }
    }
}
