package com.firestarterpro.templartv.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.QuickSettingsActivity

/**
 * Optionally captures the remote's Settings key so Templar's quick menu can open over any app.
 *
 * This is behind the [Flag.SETTINGS_KEY_CAPTURE] flag and is OFF by default. While it is off the
 * service does not ask Android for key events at all, never consumes a key, and the system
 * dashboard (with its Google Home panel) works as usual. The service never logs key presses.
 * Enabled via adb (it can't be requested at runtime on TV).
 */
class VistaAccessibilityService : AccessibilityService() {

    private var flagListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        applyKeyFilter()
        flagListener = FeatureFlags.register(this) { applyKeyFilter() }
    }

    override fun onDestroy() {
        flagListener?.let { FeatureFlags.unregister(this, it) }
        flagListener = null
        super.onDestroy()
    }

    /** Only ask the system for key events while the capture flag is on. */
    private fun applyKeyFilter() {
        val info = serviceInfo ?: return
        val wanted = FeatureFlags.isEnabled(this, Flag.SETTINGS_KEY_CAPTURE)
        val has = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        if (wanted == has) return
        info.flags = if (wanted) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_SETTINGS) return false
        if (!FeatureFlags.isEnabled(this, Flag.SETTINGS_KEY_CAPTURE)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            runCatching {
                startActivity(
                    Intent(this, QuickSettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        return true // consume both down and up so the system dashboard doesn't also open
    }
}
