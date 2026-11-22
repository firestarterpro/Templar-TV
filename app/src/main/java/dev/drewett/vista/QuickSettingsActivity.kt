package com.firestarterpro.templartv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firestarterpro.templartv.domain.ThemeMode
import com.firestarterpro.templartv.ui.home.SettingsViewModel
import com.firestarterpro.templartv.ui.quicksettings.QuickSettingsPanel
import com.firestarterpro.templartv.ui.theme.VistaTheme

/**
 * A translucent activity that shows only the quick-settings panel, launched over whatever app is
 * on screen by [com.firestarterpro.templartv.service.VistaAccessibilityService] when the Settings key is pressed.
 */
class QuickSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            VistaTheme(dark = dark) {
                QuickSettingsPanel(
                    visible = visible,
                    themeMode = themeMode,
                    onCycleTheme = settingsViewModel::cycleTheme,
                    onDismiss = { finish() },
                )
            }
        }
    }
}
