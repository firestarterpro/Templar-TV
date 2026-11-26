package com.firestarterpro.templartv.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.role.RoleManager
import android.os.Build
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.firestarterpro.templartv.FeatureFlags
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.MainActivity
import com.firestarterpro.templartv.quickmenu.QuickMenuController

/**
 * Optional helpers that need the accessibility service. Every feature here is behind a runtime
 * flag, OFF by default, and the service never logs key presses.
 *
 * - [Flag.SETTINGS_KEY_CAPTURE]: the remote's Settings key opens Templar's quick menu.
 * - [Flag.HOME_RETURN]: on Google TV Streamer a short Home press still opens Google's launcher even
 *   when Templar holds the HOME role. This watches window changes (no key events needed) and brings
 *   Templar back on top. It never consumes the Home key. Long-press Home (Google's dashboard with
 *   the Google Home panel) is never touched: only Google's HomeActivity triggers a bounce, and the
 *   bounce is cancelled if the dashboard window or a Home long-press shows up.
 *
 * The star button (KEYCODE_MACRO_1) is not handled: on Google TV Streamer it is a global key owned
 * by launcherx and never reaches an accessibility service (star capture removed 2026-11-25).
 *
 * The service only asks Android for key events while [Flag.SETTINGS_KEY_CAPTURE] is on. It is enabled via adb (it
 * can't be requested at runtime on TV).
 */
class VistaAccessibilityService : AccessibilityService() {

    private var flagListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val handler = Handler(Looper.getMainLooper())

    // Window tracking for HOME_RETURN (main thread only).
    private var frontPackage: String? = null
    private var frontIsGoogleHome = false
    private var lastOtherGoogleWindowAt = 0L
    private var homeLongPressAt = 0L
    private val bounces = ArrayDeque<Long>()
    private var pausedUntil = 0L

    private val bounceRunnable = Runnable { maybeBounce() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        applyKeyFilter()
        flagListener = FeatureFlags.register(this) { applyKeyFilter() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(bounceRunnable)
        flagListener?.let { FeatureFlags.unregister(this, it) }
        flagListener = null
        super.onDestroy()
    }

    /** Only ask the system for key events while Settings key capture is on. */
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!FeatureFlags.isEnabled(this, Flag.HOME_RETURN)) return

        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        val googleHome = HomeGuard.isGoogleHomeScreen(pkg, cls)

        // Diagnostics: only Google's launcher and Templar, never other apps.
        if (pkg == HomeGuard.GOOGLE_LAUNCHER_PACKAGE || pkg == packageName) {
            Log.d(TAG, "window pkg=$pkg cls=$cls googleHome=$googleHome")
        }

        if (googleHome) {
            frontPackage = pkg
            frontIsGoogleHome = true
            handler.removeCallbacks(bounceRunnable)
            handler.postDelayed(bounceRunnable, HomeGuard.BOUNCE_DELAY_MS)
            return
        }
        if (pkg == HomeGuard.GOOGLE_LAUNCHER_PACKAGE) {
            // Google's dashboard (long-press Home, with the Google Home panel): hands off.
            // Other launcher windows (for example the overlay it shows while apps open) are ignored.
            if (cls?.contains("dashboard", ignoreCase = true) == true) {
                lastOtherGoogleWindowAt = SystemClock.elapsedRealtime()
                frontIsGoogleHome = false
                handler.removeCallbacks(bounceRunnable)
            }
            return
        }
        // Some other app (or Templar) took the front.
        frontPackage = pkg
        frontIsGoogleHome = false
        handler.removeCallbacks(bounceRunnable)
    }

    private fun templarIsHome(): Boolean {
        // On Google TV Streamer the HOME intent still resolves to launcherx (higher priority) even
        // when Templar holds the HOME role, so ask RoleManager first.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val held = runCatching {
                getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME)
            }.getOrNull()
            if (held != null) return held
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun maybeBounce() {
        if (!FeatureFlags.isEnabled(this, Flag.HOME_RETURN)) return
        val now = SystemClock.elapsedRealtime()
        val longPress = homeLongPressAt != 0L && now - homeLongPressAt < LONG_PRESS_MEMORY_MS
        val otherRecent = lastOtherGoogleWindowAt != 0L &&
            now - lastOtherGoogleWindowAt < HomeGuard.OTHER_WINDOW_MEMORY_MS
        val isHome = templarIsHome()
        val allowed = HomeGuard.isGoogleLauncherAllowed()
        val decision = HomeGuard.shouldBounce(
            now = now,
            googleHomeStillFront = frontIsGoogleHome,
            otherGoogleWindowRecent = otherRecent,
            homeLongPressSeen = longPress,
            allowed = allowed,
            templarIsHome = isHome,
            pausedUntil = pausedUntil,
        )
        Log.d(
            TAG,
            "bounce decision=$decision front=$frontIsGoogleHome other=$otherRecent long=$longPress " +
                "home=$isHome allowed=$allowed paused=${now < pausedUntil}",
        )
        if (!decision) return

        // Circuit breaker: if Google keeps taking the screen back, stop fighting for a while.
        while (bounces.isNotEmpty() && now - bounces.first() > HomeGuard.BREAKER_WINDOW_MS) bounces.removeFirst()
        if (bounces.size >= HomeGuard.BREAKER_MAX_BOUNCES) {
            pausedUntil = now + HomeGuard.BREAKER_PAUSE_MS
            bounces.clear()
            Log.w(TAG, "too many bounces, pausing Home return for ${HomeGuard.BREAKER_PAUSE_MS} ms")
            return
        }
        bounces.addLast(now)
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        }.onFailure { Log.w(TAG, "could not bring Templar to the front", it) }
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_HOME -> {
                // Observe only, never consume: remember a long-press so the Google dashboard is left alone.
                if (event.action == KeyEvent.ACTION_DOWN && (event.isLongPress || event.repeatCount > 0)) {
                    homeLongPressAt = SystemClock.elapsedRealtime()
                    handler.removeCallbacks(bounceRunnable)
                }
                return false
            }
            KeyEvent.KEYCODE_SETTINGS -> {
                if (!FeatureFlags.isEnabled(this, Flag.SETTINGS_KEY_CAPTURE)) return false
                if (event.action == KeyEvent.ACTION_DOWN) openQuickMenu()
                return true // consume both down and up so the system dashboard doesn't also open
            }
        }
        return false
    }

    private fun openQuickMenu() {
        QuickMenuController.openOrLegacy(this)
    }

    private companion object {
        const val TAG = "TemplarHomeGuard"
        const val LONG_PRESS_MEMORY_MS = 2_000L
    }
}
