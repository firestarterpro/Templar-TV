package com.firestarterpro.templartv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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
import com.firestarterpro.templartv.quickmenu.QuickMenuController
import com.firestarterpro.templartv.ui.home.SettingsViewModel
import com.firestarterpro.templartv.ui.quickmenu.QuickMenuPanel
import com.firestarterpro.templartv.ui.quicksettings.QuickSettingsPanel
import com.firestarterpro.templartv.ui.theme.VistaTheme

/**
 * A translucent activity that shows only the quick-settings panel, launched over whatever app is
 * on screen by [com.firestarterpro.templartv.service.VistaAccessibilityService] when the Settings key is pressed.
 *
 * With [Flag.QUICK_MENU] on it shows the quick menu instead (which includes every quick setting),
 * and [QuickMenuController.ACTION_OPEN_QUICK_MENU] opens it. With the flag off it is unchanged, and
 * that action does nothing.
 */
class QuickSettingsActivity : ComponentActivity() {

    private var quickMenu = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickMenu = QuickMenuController.isEnabled(this)
        if (!quickMenu && intent?.action == QuickMenuController.ACTION_OPEN_QUICK_MENU) {
            finish()
            return
        }
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
                if (quickMenu) {
                    QuickMenuPanel(
                        visible = visible,
                        themeMode = themeMode,
                        onCycleTheme = settingsViewModel::cycleTheme,
                        onDismiss = { finish() },
                        onTemplarHome = ::openTemplarHome,
                    )
                } else {
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

    /** Quick menu only: the trigger pressed again while the menu is up closes it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (quickMenu) finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (quickMenu && keyCode in CLOSE_KEYS) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openTemplarHome() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        }
        finish()
    }

    private companion object {
        val CLOSE_KEYS = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_F2)
    }
}
