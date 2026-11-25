package com.firestarterpro.templartv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.domain.ThemeMode
import com.firestarterpro.templartv.quickmenu.QuickMenuController
import com.firestarterpro.templartv.ui.home.HomeScreen
import com.firestarterpro.templartv.ui.quickmenu.QuickMenuPanel
import com.firestarterpro.templartv.ui.quicksettings.QuickSettingsPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

@Composable
fun VistaRoot(themeMode: ThemeMode, onCycleTheme: () -> Unit) {
    var quickSettingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Flag.QUICK_MENU: off keeps the classic quick-settings panel exactly as before.
    val quickMenuFlow = remember { FeatureFlags.observe(context).map { it[Flag.QUICK_MENU] == true } }
    val quickMenuOn by quickMenuFlow.collectAsStateWithLifecycle(
        initialValue = FeatureFlags.isEnabled(context, Flag.QUICK_MENU),
    )
    var quickMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Android-style focus navigation sounds (respects the system sound setting).
                when (event.key) {
                    Key.DirectionUp -> audio?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
                    Key.DirectionDown -> audio?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_DOWN)
                    Key.DirectionLeft -> audio?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT)
                    Key.DirectionRight -> audio?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT)
                    Key.DirectionCenter, Key.Enter -> audio?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                }
                if (quickMenuOn) {
                    // Templar's own key handling (never the accessibility service, never Home).
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Menu, Key.Settings, Key.F2 -> {
                            quickMenuOpen = !quickMenuOpen
                            true
                        }
                        Key.Back -> if (quickMenuOpen) {
                            quickMenuOpen = false
                            true
                        } else {
                            false
                        }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.Menu, Key.Settings -> {
                        quickSettingsOpen = !quickSettingsOpen
                        true
                    }
                    Key.Back -> if (quickSettingsOpen) {
                        quickSettingsOpen = false
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            },
    ) {
        if (quickMenuOn) {
            QuickMenuHost(
                open = quickMenuOpen,
                onOpenChange = { quickMenuOpen = it },
                themeMode = themeMode,
                onCycleTheme = onCycleTheme,
            )
        } else {
            HomeScreen(onOpenQuickSettings = { quickSettingsOpen = true })
            QuickSettingsPanel(
                visible = quickSettingsOpen,
                themeMode = themeMode,
                onCycleTheme = onCycleTheme,
                onDismiss = { quickSettingsOpen = false },
            )
        }
    }
}

/**
 * Home screen plus the quick menu. The home screen sits in a focus group with a restorer, so when
 * the menu closes focus goes back to the tile that had it before the menu opened.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun QuickMenuHost(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
) {
    val homeFocus = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }

    // QuickMenuController.open() while Templar is in front lands here.
    LaunchedEffect(Unit) { QuickMenuController.openRequests.collect { onOpenChange(true) } }
    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            delay(CLOSE_ANIMATION_MS) // let the panel leave first; it keeps focus inside itself
            runCatching { homeFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(homeFocus)
            .focusRestorer()
            .focusGroup(),
    ) {
        HomeScreen(onOpenQuickSettings = { onOpenChange(true) })
    }
    QuickMenuPanel(
        visible = open,
        themeMode = themeMode,
        onCycleTheme = onCycleTheme,
        onDismiss = { onOpenChange(false) },
        onTemplarHome = {
            onOpenChange(false)
            VistaSignals.reset()
        },
    )
}

private const val CLOSE_ANIMATION_MS = 270L
