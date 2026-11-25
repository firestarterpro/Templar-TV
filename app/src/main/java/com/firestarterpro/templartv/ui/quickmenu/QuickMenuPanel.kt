package com.firestarterpro.templartv.ui.quickmenu

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.firestarterpro.templartv.FeaturesActivity
import com.firestarterpro.templartv.R
import com.firestarterpro.templartv.data.apps.AppRepository
import com.firestarterpro.templartv.data.favourites.FavouritesRepository
import com.firestarterpro.templartv.data.usage.LaunchHistoryRepository
import com.firestarterpro.templartv.domain.AppEntry
import com.firestarterpro.templartv.domain.ThemeMode
import com.firestarterpro.templartv.google.GoogleHomeHandoff
import com.firestarterpro.templartv.quickmenu.FooterItem
import com.firestarterpro.templartv.quickmenu.QuickMenuController
import com.firestarterpro.templartv.quickmenu.QuickMenuLayout
import com.firestarterpro.templartv.quickmenu.QuickMenuSection
import com.firestarterpro.templartv.service.HomeGuard
import com.firestarterpro.templartv.ui.components.DrawableImage
import com.firestarterpro.templartv.ui.quicksettings.Hint
import com.firestarterpro.templartv.ui.quicksettings.QuickSettingsBody
import com.firestarterpro.templartv.ui.quicksettings.SubHeader
import com.firestarterpro.templartv.ui.quicksettings.Tile
import com.firestarterpro.templartv.ui.quicksettings.dateLine
import com.firestarterpro.templartv.ui.quicksettings.timeLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PanelShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
private val TileShape = RoundedCornerShape(16.dp)
private const val MAX_APPS = 8

/**
 * The Templar quick menu ([com.firestarterpro.templartv.Flag.QUICK_MENU]). Slides in from the
 * LEFT (Google's dashboard is on the right, so the two never look alike), over whatever is showing.
 * Sections follow the saved [QuickMenuLayout]; the footer (Google Home, Google TV Home, Templar
 * Home) is pinned below the scrolling list and never hidden. Focus can't leave the panel: Up from
 * the top wraps to Google Home, Down from the footer wraps to the top.
 */
@Composable
fun QuickMenuPanel(
    visible: Boolean,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onDismiss: () -> Unit,
    onTemplarHome: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)),
            exit = slideOutHorizontally(tween(250)) { -it } + fadeOut(tween(200)),
        ) {
            MenuContent(themeMode, onCycleTheme, onDismiss, onTemplarHome)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuContent(
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onDismiss: () -> Unit,
    onTemplarHome: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val layoutFlow = remember { QuickMenuController.observeLayout(context) }
    val layout by layoutFlow.collectAsStateWithLifecycle(initialValue = QuickMenuLayout.DEFAULT)
    var editing by remember { mutableStateOf(false) }

    val appRepository = remember { AppRepository(context) }
    val launchHistory = remember { LaunchHistoryRepository(context) }
    val favouritesFlow = remember { FavouritesRepository(context).favourites }
    val favourites by favouritesFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val recent by launchHistory.recent.collectAsStateWithLifecycle(initialValue = emptyList())
    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { runCatching { appRepository.loadApps() }.getOrDefault(emptyList()) }
    }
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }

    val top = remember { FocusRequester() }
    val googleHome = remember { FocusRequester() }
    LaunchedEffect(editing) { runCatching { top.requestFocus() } }

    fun save(newLayout: QuickMenuLayout) {
        scope.launch { runCatching { QuickMenuController.saveLayout(context, newLayout) } }
    }

    fun launch(entry: AppEntry) {
        appRepository.launch(entry)
        scope.launch { runCatching { launchHistory.record(entry.packageName) } }
        onDismiss()
    }

    Column(
        modifier = Modifier
            .width(460.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface, PanelShape)
            // Keep D-pad focus inside the menu, and wrap top <-> footer.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (!focusManager.moveFocus(FocusDirection.Up)) runCatching { googleHome.requestFocus() }
                        true
                    }
                    Key.DirectionDown -> {
                        if (!focusManager.moveFocus(FocusDirection.Down)) runCatching { top.requestFocus() }
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 28.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(top)
                .focusGroup()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dateLine(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(timeLine(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (editing) {
                EditSections(
                    layout = layout,
                    onChange = ::save,
                    onReset = { scope.launch { runCatching { QuickMenuController.resetLayout(context) } } },
                    onDone = { editing = false },
                )
            } else {
                layout.visibleSections.forEach { section ->
                    when (section) {
                        QuickMenuSection.RESUME -> ResumeSection(recent, byPackage, context.packageName, ::launch)
                        QuickMenuSection.APPS -> AppsSection(favourites, byPackage, ::launch)
                        QuickMenuSection.QUICK_SETTINGS -> {
                            SubHeader(section.label)
                            QuickSettingsBody(themeMode, onCycleTheme, onDismiss, showGoogleHome = false)
                        }
                        QuickMenuSection.TEMPLAR_SHORTCUTS -> TemplarSection(context, onDismiss)
                    }
                }
                // Not part of the layout, so it is reachable even with every section hidden.
                Tile("Edit this menu", "Hide / move", R.drawable.ic_settings, Modifier.fillMaxWidth()) { editing = true }
            }
        }
        Footer(googleHome, context, onDismiss, onTemplarHome)
    }
}

@Composable
private fun ResumeSection(
    recent: List<String>,
    byPackage: Map<String, AppEntry>,
    ownPackage: String,
    onLaunch: (AppEntry) -> Unit,
) {
    SubHeader(QuickMenuSection.RESUME.label)
    val last = recent.firstOrNull { it != ownPackage && byPackage.containsKey(it) }?.let { byPackage[it] }
    if (last == null) {
        Hint("Apps you open from Templar show up here.")
    } else {
        AppTile(last, "Resume ${last.label}", Modifier.fillMaxWidth().height(84.dp)) { onLaunch(last) }
    }
}

@Composable
private fun AppsSection(favourites: Set<String>, byPackage: Map<String, AppEntry>, onLaunch: (AppEntry) -> Unit) {
    SubHeader(QuickMenuSection.APPS.label)
    val pinned = favourites.mapNotNull { byPackage[it] }.sortedBy { it.label.lowercase() }.take(MAX_APPS)
    if (pinned.isEmpty()) {
        Hint("Pin apps on the home screen (hold OK on an app, then Favourite) to list them here.")
        return
    }
    pinned.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { entry -> AppTile(entry, entry.label, Modifier.weight(1f).height(72.dp)) { onLaunch(entry) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TemplarSection(context: Context, onDismiss: () -> Unit) {
    var showGuide by remember { mutableStateOf(false) }
    SubHeader(QuickMenuSection.TEMPLAR_SHORTCUTS.label)
    Tile("Features", "On / off", R.drawable.ic_settings, Modifier.fillMaxWidth()) {
        runCatching {
            context.startActivity(Intent(context, FeaturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismiss()
    }
    Tile("Turn the quick menu off", if (showGuide) "Hide" else "How", null, Modifier.fillMaxWidth()) { showGuide = !showGuide }
    if (showGuide) {
        Hint("Features > Quick menu > Off brings back the old quick-settings panel. Nothing you set here is lost.")
    }
}

@Composable
private fun EditSections(
    layout: QuickMenuLayout,
    onChange: (QuickMenuLayout) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    SubHeader("Edit menu")
    Hint("OK shows or hides a section. Use ▲ ▼ to move it.")
    layout.sections.forEach { state ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Tile(state.section.label, if (state.hidden) "Hidden" else "Shown", null, Modifier.weight(1f)) {
                onChange(layout.toggleHidden(state.section))
            }
            Tile("▲", null, null, Modifier.width(64.dp)) { onChange(layout.move(state.section, -1)) }
            Tile("▼", null, null, Modifier.width(64.dp)) { onChange(layout.move(state.section, 1)) }
        }
    }
    Hint("Google Home, Google TV Home and Templar Home are pinned at the bottom and always shown.")
    Tile("Reset to default", "", R.drawable.ic_reload, Modifier.fillMaxWidth(), onClick = onReset)
    Tile("Done", "", null, Modifier.fillMaxWidth(), onClick = onDone)
}

@Composable
private fun Footer(googleHomeRequester: FocusRequester, context: Context, onDismiss: () -> Unit, onTemplarHome: () -> Unit) {
    var hint by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hint?.let { Hint(it) }
        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickMenuLayout.FOOTER.forEach { item ->
                val modifier = Modifier.weight(1f).height(76.dp).let {
                    if (item == FooterItem.GOOGLE_HOME) it.focusRequester(googleHomeRequester) else it
                }
                FooterTile(item.label, modifier) {
                    when (item) {
                        FooterItem.GOOGLE_HOME ->
                            if (GoogleHomeHandoff.openGoogleHome(context)) onDismiss()
                            else hint = "Hold the Home button to open Google Home."
                        FooterItem.GOOGLE_TV_HOME ->
                            if (openGoogleTvHome(context)) onDismiss()
                            else hint = "Press Home and pick Google TV Home, or set it in Settings > Apps > Default apps > Home app."
                        FooterItem.TEMPLAR_HOME -> onTemplarHome()
                    }
                }
            }
        }
    }
}

/** Opens Google's launcher on purpose and tells Home return to leave it there. */
private fun openGoogleTvHome(context: Context): Boolean {
    HomeGuard.allowGoogleLauncherFor()
    val home = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .setPackage(HomeGuard.GOOGLE_LAUNCHER_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val intent = home.takeIf { it.resolveActivity(context.packageManager) != null }
        ?: runCatching { context.packageManager.getLaunchIntentForPackage(HomeGuard.GOOGLE_LAUNCHER_PACKAGE) }
            .getOrNull()?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: return false
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

@Composable
private fun FooterTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = TileShape),
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun AppTile(entry: AppEntry, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = TileShape),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawableImage(entry.icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
