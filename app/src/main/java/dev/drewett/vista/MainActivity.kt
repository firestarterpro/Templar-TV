package com.firestarterpro.templartv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firestarterpro.templartv.domain.ThemeMode
import com.firestarterpro.templartv.service.VistaForeground
import com.firestarterpro.templartv.ui.VistaRoot
import com.firestarterpro.templartv.ui.VistaSignals
import com.firestarterpro.templartv.ui.home.SettingsViewModel
import com.firestarterpro.templartv.ui.theme.VistaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }
            VistaTheme(dark = dark) {
                VistaRoot(themeMode = themeMode, onCycleTheme = settingsViewModel::cycleTheme)
            }
        }
    }

    /** Pressing HOME while already home re-delivers the launch intent — reset to the top. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        VistaSignals.reset()
    }

    override fun onResume() {
        super.onResume()
        VistaForeground.isForeground = true
    }

    override fun onPause() {
        super.onPause()
        VistaForeground.isForeground = false
    }
}
