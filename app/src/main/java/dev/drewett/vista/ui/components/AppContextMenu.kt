package com.firestarterpro.templartv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.firestarterpro.templartv.domain.AppEntry

private val MenuShape = RoundedCornerShape(20.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/** Long-press menu for an app: open, pin/unpin, app info, uninstall. */
@Composable
fun AppContextMenu(
    app: AppEntry?,
    isFavourite: Boolean,
    onDismiss: () -> Unit,
    onOpen: (AppEntry) -> Unit,
    onToggleFavourite: (AppEntry) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onMove: ((AppEntry) -> Unit)? = null,
    onHide: ((AppEntry) -> Unit)? = null,
) {
    app ?: return
    BackHandler { onDismiss() }
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(app.packageName) { runCatching { firstItem.requestFocus() } }

    // The menu opens while the OK button is still held down. Without this guard the held key
    // (its repeats and its release) lands on "Open" and launches the app straight away.
    // Select keys are swallowed until the button is released, or 1.5 s have passed.
    var armed by remember(app.packageName) { mutableStateOf(false) }
    LaunchedEffect(app.packageName) { delay(1500); armed = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { e ->
                val select = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    armed || !select -> false
                    e.type == KeyEventType.KeyUp -> { armed = true; true }
                    else -> true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(MaterialTheme.colorScheme.surface, MenuShape)
                .focusGroup()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                DrawableImage(
                    drawable = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.width(14.dp))
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(0.dp, 8.dp))
            MenuItem("Open", Modifier.focusRequester(firstItem)) { onOpen(app); onDismiss() }
            if (onMove != null) MenuItem("Move") { onMove(app); onDismiss() }
            if (onHide != null) MenuItem("Hide") { onHide(app); onDismiss() }
            MenuItem(if (isFavourite) "Remove from favourites" else "Add to favourites") {
                onToggleFavourite(app); onDismiss()
            }
            MenuItem("App info") { onAppInfo(app.packageName); onDismiss() }
            MenuItem("Uninstall") { onUninstall(app.packageName); onDismiss() }
        }
    }
}

@Composable
private fun MenuItem(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}
