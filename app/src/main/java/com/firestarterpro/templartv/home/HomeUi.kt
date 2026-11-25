package com.firestarterpro.templartv.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.domain.AppEntry
import com.firestarterpro.templartv.ui.components.DrawableImage

val EdgePad = 48.dp
val TopClear = 108.dp

/** Installed apps by package, so APP tiles can draw their icons. */
val LocalAppEntries = staticCompositionLocalOf<Map<String, AppEntry>> { emptyMap() }

/**
 * Moves focus to a tile by its key, even if the tile is not on screen yet. Views scroll to the key
 * and the tile takes focus when it is composed. Used for the first focus, after every edit, and
 * when a view is switched, so focus lands where the design review says it should.
 */
@Stable
class FocusCoordinator {
    var target by mutableStateOf<String?>(null)
        private set
    var focusedKey by mutableStateOf<String?>(null)
    var focusedRowId by mutableStateOf<String?>(null)
    /** The Wall's "All" filter mixes shelves, so tiles cannot be moved there. */
    var wallAll by mutableStateOf(false)

    fun request(key: String?) { target = key }
    fun consume(key: String) { if (target == key) target = null }
    fun clear() { target = null }
}

/** Makes a tile focusable by key. [requester] lets a view reuse its own requester (for example the entry tile). */
@Composable
fun Modifier.trackedFocus(c: FocusCoordinator, key: String, requester: FocusRequester? = null): Modifier {
    val fr = requester ?: remember { FocusRequester() }
    LaunchedEffect(c.target, key) {
        if (c.target == key) {
            var done = false
            var tries = 0
            while (!done && tries < 6) {
                withFrameNanos { }
                done = runCatching { fr.requestFocus() }.isSuccess
                tries++
            }
            c.consume(key)
        }
    }
    return this.focusRequester(fr)
}

/** What every home view is handed: the data, focus and the tile callbacks. */
@Stable
class HomeViewScope(
    val ui: BigBoxUi,
    val focus: FocusCoordinator,
    val topBarFocus: FocusRequester,
    val contentFocus: FocusRequester,
    val interactive: Boolean,
    val onClick: (HomeRowModel, HomeTile) -> Unit,
    val onLongClick: (HomeRowModel, HomeTile) -> Unit,
    val rowOf: (HomeTile) -> HomeRowModel?,
) {
    val movingKey: String? get() = ui.move?.tileKey

    @Composable
    fun Tile(
        row: HomeRowModel,
        tile: HomeTile,
        aspect: Float,
        modifier: Modifier = Modifier,
        entry: Boolean = false,
        requester: FocusRequester? = null,
        compact: Boolean = false,
    ) {
        val fr = requester ?: contentFocus.takeIf { entry }
        TileCard(
            tile = tile,
            aspect = aspect,
            modifier = modifier,
            focusModifier = Modifier.trackedFocus(focus, tile.key, fr),
            lifted = movingKey == tile.key,
            compact = compact,
            onClick = { onClick(row, tile) },
            onLongClick = { onLongClick(row, tile) },
            onFocused = { focus.focusedKey = tile.key; focus.focusedRowId = row.rowId },
        )
    }
}

private val PosterShape = RoundedCornerShape(12.dp)

@Composable
fun TileCard(
    tile: HomeTile,
    aspect: Float,
    modifier: Modifier = Modifier,
    focusModifier: Modifier = Modifier,
    lifted: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onBg = MaterialTheme.colorScheme.onBackground
    val shape = if (tile.kind == TileKind.APP) CircleShape else PosterShape
    Box(focusModifier.then(modifier)) {
        Column(Modifier.onFocusChanged { if (it.hasFocus) onFocused() }) {
            Surface(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(if (tile.kind == TileKind.APP) 1f else aspect),
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = if (lifted) 1.12f else 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = if (lifted) Border(BorderStroke(5.dp, secondary), shape = shape)
                    else if (tile.kind == TileKind.ADD) Border(BorderStroke(2.dp, onBg.copy(alpha = 0.35f)), shape = shape)
                    else Border.None,
                    focusedBorder = Border(BorderStroke(if (lifted) 5.dp else 4.dp, if (lifted) secondary else primary), shape = shape),
                ),
                glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(primary, 12.dp)),
            ) {
                TileBody(tile, lifted)
            }
            if (tile.kind != TileKind.ADD && tile.kind != TileKind.MORE) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onBg,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                )
                if (!compact && tile.subtitle.isNotBlank()) {
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = onBg.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TileBody(tile: HomeTile, lifted: Boolean) {
    val onBg = MaterialTheme.colorScheme.onBackground
    when (tile.kind) {
        TileKind.APP -> {
            val app = LocalAppEntries.current[tile.packageName]
            if (app != null) {
                DrawableImage(app.icon, tile.title, Modifier.fillMaxSize(), ContentScale.Crop)
            } else {
                Placeholder(tile.title)
            }
        }
        TileKind.ADD -> Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("+", fontSize = 34.sp, fontWeight = FontWeight.Light, color = onBg.copy(alpha = 0.8f))
            Text("Add a show", style = MaterialTheme.typography.labelLarge, color = onBg.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
        TileKind.MORE -> Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tile.title, style = MaterialTheme.typography.titleSmall, color = onBg, textAlign = TextAlign.Center, maxLines = 2)
            if (tile.subtitle.isNotBlank()) {
                Text(tile.subtitle, style = MaterialTheme.typography.labelMedium, color = onBg.copy(alpha = 0.6f), maxLines = 1)
            }
        }
        else -> Box(Modifier.fillMaxSize()) {
            Placeholder(tile.title)
            if (tile.artworkUri != null) {
                AsyncImage(
                    model = tile.artworkUri,
                    contentDescription = tile.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (tile.providerLabel.isNotBlank()) {
                Text(
                    text = tile.providerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (tile.watched) {
                Text(
                    text = "Watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (lifted) {
                Text(
                    text = "Moving",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            tile.progress?.let { p ->
                ProgressMark(p, tile.progressKind, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** A solid bar means the provider gave the exact position. A dashed bar means Templar estimated it. */
@Composable
fun ProgressMark(progress: Float, kind: ProgressKind, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRect(Color.White.copy(alpha = 0.25f))
        val w = size.width * progress.coerceIn(0f, 1f)
        if (kind == ProgressKind.ESTIMATED) {
            drawLine(
                color = fill, start = Offset(0f, size.height / 2), end = Offset(w, size.height / 2),
                strokeWidth = size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 7f)),
            )
        } else {
            drawRect(fill, size = Size(w, size.height))
        }
    }
}

/** Words for the focused tile's progress, so the meaning never depends on colour or bar style alone. */
fun progressWords(tile: HomeTile): String? {
    val p = tile.progress ?: return null
    val pct = (p * 100).toInt()
    return when (tile.progressKind) {
        ProgressKind.EXACT -> "$pct% watched"
        ProgressKind.ESTIMATED -> "about $pct% watched (estimated)"
        ProgressKind.UNKNOWN -> null
    }
}

fun tileWidth(size: com.firestarterpro.templartv.data.layout.TileSize, tile: HomeTile): Dp = when {
    tile.kind == TileKind.APP -> 104.dp
    size == com.firestarterpro.templartv.data.layout.TileSize.SMALL -> 168.dp
    size == com.firestarterpro.templartv.data.layout.TileSize.LARGE -> 288.dp
    else -> 224.dp
}

@Composable
fun RowTitle(row: HomeRowModel, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.help.isNotBlank() && row.realTiles.isEmpty()) {
            Text(
                text = row.help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 2,
            )
        }
    }
}

@Composable
fun EmptyHome(onOpenRowManager: () -> Unit, requester: FocusRequester) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Nothing to show yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Every row is hidden or empty. Open the Row Manager to show rows again.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Surface(
                onClick = onOpenRowManager,
                modifier = Modifier.focusRequester(requester),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Open Row Manager", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            }
        }
    }
}
