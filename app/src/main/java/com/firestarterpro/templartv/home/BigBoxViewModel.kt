package com.firestarterpro.templartv.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.DedupeKey
import com.firestarterpro.templartv.data.TemplarData
import com.firestarterpro.templartv.data.layout.RowKind
import com.firestarterpro.templartv.data.layout.RowLayoutEntry
import com.firestarterpro.templartv.data.layout.TileSize
import com.firestarterpro.templartv.data.favourites.FavouriteShow
import com.firestarterpro.templartv.data.settings.SettingKeys
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.data.shelf.ShelfDraft
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.domain.AppEntry
import com.firestarterpro.templartv.domain.ContentCard
import com.firestarterpro.templartv.observation.ShelfLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the old (channel and app) side of the home screen hands to the new one. */
data class ExternalInput(
    val continueCards: List<ContentCard> = emptyList(),
    val allCards: List<ContentCard> = emptyList(),
    val apps: List<AppEntry> = emptyList(),
    val favouriteApps: Set<String> = emptySet(),
)

/** Everything the home views need to draw and to decide what the owner may do. */
data class BigBoxUi(
    val ready: Boolean = false,
    val rows: List<HomeRowModel> = emptyList(),
    val managed: List<ManagedRow> = emptyList(),
    val layout: List<RowLayoutEntry> = emptyList(),
    val view: HomeView = HomeView.ROWS,
    val availableViews: List<HomeView> = listOf(HomeView.ROWS),
    val columns: Int = 4,
    val favouriteKeys: Set<String> = emptySet(),
    val shelfEditing: Boolean = true,
    val favouritesOn: Boolean = true,
    val rowEditing: Boolean = true,
    val move: MoveSession? = null,
    /** The data layer failed to load. The caller falls back to the classic home. */
    val failed: Boolean = false,
)

private data class DbSnapshot(
    val profileId: String,
    val layout: List<RowLayoutEntry>,
    val favourites: List<FavouriteShow>,
    val continueShelf: List<ShelfEntry>,
    val shelves: Map<String, List<ShelfEntry>>,
    val shelfLength: Int,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BigBoxViewModel(application: Application) : AndroidViewModel(application) {

    private val data = TemplarData.get(application)
    private val external = MutableStateFlow(ExternalInput())
    private val ready = MutableStateFlow(false)
    private val moveSession = MutableStateFlow<MoveSession?>(null)
    private val snapshotCount = MutableStateFlow(0)
    private val failed = MutableStateFlow(false)

    val profileId: StateFlow<String> = data.profiles.activeProfileId

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // The app never seeded the default rows before this screen; do it once, safely.
            runCatching {
                data.profiles.ensureDefault()
                if (profileId.value != com.firestarterpro.templartv.data.ProfileIds.DEFAULT) data.rows.ensureDefaults(profileId.value)
                moveAppsLeftOnce()
            }.onFailure { failed.value = true }
            ready.value = true
        }
    }

    /** One time only: older installs have the Apps row last (right side); put it first (left side). Later moves stay. */
    private suspend fun moveAppsLeftOnce() {
        val prefs = getApplication<Application>().getSharedPreferences("templar_migrations", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("apps_left_v1", false)) return
        data.rows.move(profileId.value, com.firestarterpro.templartv.data.layout.DefaultRows.APPS, 0)
        prefs.edit().putBoolean("apps_left_v1", true).apply()
    }

    fun setExternal(input: ExternalInput) {
        external.value = input
    }

    private val snapshot: Flow<DbSnapshot> = profileId.flatMapLatest { pid ->
        val layout = data.rows.observe(pid)
        val shelves = layout
            .map { rows -> rows.filter { it.kind == RowKind.PROVIDER_SHELF }.map { it.provider }.distinct().sorted() }
            .distinctUntilChanged()
            .flatMapLatest { providers ->
                if (providers.isEmpty()) {
                    flowOf(emptyMap<String, List<ShelfEntry>>())
                } else {
                    combine(providers.map { p -> data.shelves.observeShelf(pid, p, 100).map { p to it } }) { pairs -> pairs.toMap() }
                }
            }
        combine(
            layout,
            data.favourites.observe(pid),
            data.shelves.observeContinue(pid, 40),
            shelves,
            data.profiles.observeSetting(pid, SettingKeys.SHELF_LENGTH),
        ) { l, f, c, s, len -> DbSnapshot(pid, l, f, c, s, len?.toIntOrNull() ?: 20) }
    }.onEach { snapshotCount.value = snapshotCount.value + 1 }
        .catch { failed.value = true }

    private val viewKey = profileId.flatMapLatest { data.profiles.observeSetting(it, SettingKeys.HOME_VIEW) }
    private val columnsKey = profileId.flatMapLatest { data.profiles.observeSetting(it, SettingKeys.COLUMNS_COUNT) }

    private val base: Flow<BigBoxUi> = combine(
        snapshot, external, FeatureFlags.observe(application), viewKey, columnsKey,
    ) { db, ext, flags, viewK, colsK ->
        fun on(f: Flag) = flags[f] ?: f.defaultEnabled
        val input = HomeBuildInput(
            layout = db.layout,
            continueShelf = db.continueShelf,
            shelves = db.shelves,
            favourites = db.favourites,
            channelContinue = ext.continueCards.map { it.toTile(ext.apps) },
            channelRows = channelRows(ext),
            appTiles = appTiles(ext),
            shelfLength = db.shelfLength,
            showProviderRows = on(Flag.PROVIDER_ROWS),
            showNewAndUpcoming = on(Flag.PREVIEW_AND_UPCOMING),
        )
        BigBoxUi(
            ready = true,
            rows = HomeModelBuilder.build(input),
            managed = HomeModelBuilder.managed(db.layout),
            layout = db.layout,
            view = HomeView.resolve(viewK, ::on),
            availableViews = HomeView.available(::on),
            columns = (colsK?.toIntOrNull() ?: 4).coerceIn(3, 6),
            favouriteKeys = db.favourites.map { it.dedupeKey }.toSet(),
            shelfEditing = on(Flag.SHELF_EDITING),
            favouritesOn = on(Flag.FAVOURITE_SHOWS),
            rowEditing = on(Flag.ROW_MANAGER),
        )
    }.flowOn(Dispatchers.Default)

    val ui: StateFlow<BigBoxUi> = combine(base, moveSession, ready, failed) { ui, move, isReady, isFailed ->
        ui.copy(ready = isReady, rows = HomeRules.applyMove(ui.rows, move), move = move, failed = isFailed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BigBoxUi())

    // Conversions from the old side of the home screen ---------------------------------------------

    private fun ContentCard.toTile(apps: List<AppEntry>): HomeTile {
        val label = apps.firstOrNull { it.packageName == packageName }?.label.orEmpty()
        val provider = packageName?.let { ProviderCatalog.providerFor(it, label) }.orEmpty()
        return HomeTile(
            key = "card:$id",
            kind = TileKind.CHANNEL,
            title = title,
            subtitle = metadataLine ?: episodeLabel.orEmpty(),
            description = description.orEmpty(),
            providerId = provider,
            providerLabel = if (provider.isNotEmpty()) ProviderCatalog.label(provider) else label,
            artworkUri = posterArtUri,
            progress = progress,
            progressKind = if (progress != null) ProgressKind.EXACT else ProgressKind.UNKNOWN,
            ref = id,
            packageName = packageName.orEmpty(),
        )
    }

    private fun channelRows(ext: ExternalInput): List<ChannelRowInput> {
        val continueIds = ext.continueCards.map { it.id }.toSet()
        return ext.allCards
            .filter { it.id !in continueIds && it.packageName != null }
            .groupBy { it.packageName!! }
            .map { (pkg, cards) ->
                val label = ext.apps.firstOrNull { it.packageName == pkg }?.label.orEmpty()
                ChannelRowInput(
                    key = "ch:$pkg",
                    title = label.ifEmpty { pkg },
                    provider = ProviderCatalog.providerFor(pkg, label),
                    tiles = cards.sortedByDescending { it.lastEngagement }.map { it.toTile(ext.apps) },
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    private fun appTiles(ext: ExternalInput): List<HomeTile> {
        val (fav, rest) = ext.apps.partition { it.packageName in ext.favouriteApps }
        return (fav + rest).map {
            HomeTile(key = "app:${it.packageName}", kind = TileKind.APP, title = it.label, packageName = it.packageName)
        }
    }

    // Opening things -------------------------------------------------------------------------------

    private val shelfLauncher by lazy { ShelfLauncher(getApplication(), data.shelves) }

    /**
     * Opens a shelf tile through [ShelfLauncher] (B3), from a coroutine, so the tile also moves to
     * the front of its shelf. Falls back to [open] when [Flag.PROVIDER_RECENT_SHELVES] is off or the
     * entry can't be launched. [onResult] runs on the main thread.
     */
    fun openShelf(tile: HomeTile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = ShelfOpen.open(
                ref = tile.ref,
                launcherOn = FeatureFlags.isEnabled(getApplication(), Flag.PROVIDER_RECENT_SHELVES),
                lookup = { id -> withContext(Dispatchers.IO) { data.shelves.getById(id) } },
                launch = { entry -> shelfLauncher.launch(entry) },
                fallback = { open(tile) },
            )
            onResult(ok)
        }
    }

    /** Opens a shelf or favourite tile in its provider's app. Returns false when nothing could be opened. */
    fun open(tile: HomeTile): Boolean = when (tile.kind) {
        TileKind.SHELF -> startLink(tile.deepLink, tile.packageName) ||
            launchPackage(tile.packageName.ifBlank { installedPackageFor(tile.providerId).orEmpty() })
        TileKind.FAVOURITE -> launchPackage(installedPackageFor(tile.providerId).orEmpty())
        else -> false
    }

    private fun startLink(link: String, pkg: String): Boolean {
        if (link.isBlank()) return false
        return runCatching {
            val intent = if (link.startsWith("intent:")) {
                Intent.parseUri(link, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).also { if (pkg.isNotBlank()) it.setPackage(pkg) }
            }
            getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun launchPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val pm = getApplication<Application>().packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun installedPackageFor(provider: String): String? {
        if (provider.isBlank()) return null
        val pm = getApplication<Application>().packageManager
        return ProviderCatalog.packages(provider).firstOrNull {
            runCatching { pm.getPackageInfo(it, 0); true }.getOrDefault(false)
        }
    }

    // Tile edits -----------------------------------------------------------------------------------

    fun isFavourite(tile: HomeTile, ui: BigBoxUi): Boolean =
        DedupeKey.forFavourite(tile.tmdbId, tile.title) in ui.favouriteKeys || DedupeKey.forFavourite(0, tile.title) in ui.favouriteKeys

    fun addFavourite(tile: HomeTile) = launchIo {
        data.favourites.add(
            profileId.value, tile.title, tmdbId = tile.tmdbId,
            preferredProvider = tile.providerId, artworkRef = tile.artworkUri.orEmpty(),
        )
    }

    fun removeFavouriteByTile(tile: HomeTile, ui: BigBoxUi) = launchIo {
        val id = if (tile.kind == TileKind.FAVOURITE) {
            tile.ref
        } else {
            data.favourites.observe(profileId.value).first().firstOrNull {
                it.dedupeKey == DedupeKey.forFavourite(tile.tmdbId, tile.title) || it.dedupeKey == DedupeKey.forFavourite(0, tile.title)
            }?.id
        }
        if (id != null) data.favourites.remove(id)
    }

    fun removeTile(tile: HomeTile) = launchIo {
        when (tile.kind) {
            TileKind.SHELF -> data.shelves.remove(tile.ref)
            TileKind.FAVOURITE -> data.favourites.remove(tile.ref)
            else -> Unit
        }
    }

    fun setWatched(tile: HomeTile, watched: Boolean) = launchIo { if (tile.kind == TileKind.SHELF) data.shelves.setWatched(tile.ref, watched) }

    fun changeProvider(tile: HomeTile, provider: String) = launchIo { if (tile.kind == TileKind.SHELF) data.shelves.changeProvider(tile.ref, provider) }

    /** Adds a show by hand to the favourites or to a provider shelf. */
    fun addShow(row: HomeRowModel, title: String) = launchIo {
        val name = title.trim()
        if (name.isEmpty()) return@launchIo
        when (row.kind) {
            RowKind.FAVOURITE_SHOWS -> data.favourites.add(profileId.value, name)
            RowKind.PROVIDER_SHELF -> data.shelves.addManual(profileId.value, row.provider, ShelfDraft(title = name))
            else -> Unit
        }
    }

    fun restoreShelfEntry(id: Long) = launchIo { data.shelves.restore(id) }
    fun restoreFavourite(id: Long) = launchIo { data.favourites.restore(id) }

    fun removedShelf(provider: String): Flow<List<ShelfEntry>> =
        profileId.flatMapLatest { data.shelves.observeRemoved(it) }.map { list -> list.filter { it.provider == provider } }

    fun removedFavourites(): Flow<List<FavouriteShow>> = profileId.flatMapLatest { data.favourites.observeRemoved(it) }

    // Move mode ------------------------------------------------------------------------------------

    fun startMove(row: HomeRowModel, tile: HomeTile) {
        val index = HomeRules.movableIndex(row, tile.key)
        if (index < 0) return
        moveSession.value = MoveSession(row.rowId, tile.key, index, index)
    }

    /** One D-pad step of a move. Returns false when there is no move in progress. */
    fun stepMove(delta: Int): Boolean {
        val s = moveSession.value ?: return false
        val row = ui.value.rows.firstOrNull { it.rowId == s.rowId } ?: return false
        moveSession.value = HomeRules.step(s, delta, row.tiles.count { it.movable })
        return true
    }

    fun cancelMove() {
        moveSession.value = null
    }

    /** Saves the move. The lifted tile stays where it was dropped while the database catches up. */
    fun commitMove() {
        val s = moveSession.value ?: return
        val row = ui.value.rows.firstOrNull { it.rowId == s.rowId }
        val tile = row?.tiles?.firstOrNull { it.key == s.tileKey }
        if (row == null || tile == null || s.currentIndex == s.originalIndex) {
            moveSession.value = null
            return
        }
        val before = snapshotCount.value
        viewModelScope.launch(Dispatchers.IO) {
            when (tile.kind) {
                TileKind.FAVOURITE -> data.favourites.move(profileId.value, tile.ref, s.currentIndex)
                TileKind.SHELF -> data.shelves.move(profileId.value, row.provider, tile.ref, s.currentIndex)
                else -> Unit
            }
            withTimeoutOrNull(1_000) { snapshotCount.first { it > before } }
            moveSession.value = null
        }
    }

    // Row edits ------------------------------------------------------------------------------------

    fun setRowVisible(rowId: String, visible: Boolean) = launchIo { data.rows.setVisible(profileId.value, rowId, visible) }
    fun renameRow(rowId: String, title: String) = launchIo { data.rows.rename(profileId.value, rowId, title) }

    fun cycleTileSize(rowId: String) = launchIo {
        val current = data.rows.get(profileId.value, rowId)?.tileSize ?: return@launchIo
        val next = when (current) {
            TileSize.SMALL -> TileSize.MEDIUM
            TileSize.MEDIUM -> TileSize.LARGE
            TileSize.LARGE -> TileSize.SMALL
        }
        data.rows.setTileSize(profileId.value, rowId, next)
    }

    fun setInlineProviderRows(rowId: String, on: Boolean) = launchIo {
        val row = data.rows.get(profileId.value, rowId) ?: return@launchIo
        data.rows.setOptions(profileId.value, rowId, RowOptions.withInlineProviderRows(row.optionsJson, on))
    }

    /** Moves a row to [toIndex] among all rows (hidden ones included). */
    fun moveRow(rowId: String, toIndex: Int) = launchIo { data.rows.move(profileId.value, rowId, toIndex) }

    // Views ----------------------------------------------------------------------------------------

    fun setView(view: HomeView) = launchIo { data.profiles.putSetting(profileId.value, SettingKeys.HOME_VIEW, view.key) }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { block() } }
    }
}
