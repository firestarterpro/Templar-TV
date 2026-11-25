package com.firestarterpro.templartv.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.firestarterpro.templartv.QuickSettingsActivity
import com.firestarterpro.templartv.StarAction

/**
 * Optional helpers that need the accessibility service. Every feature here is behind a runtime
 * flag, OFF by default, and the service never logs key presses.
 *
 * - [Flag.SETTINGS_KEY_CAPTURE]: the remote's Settings key opens Templar's quick menu.
 * - [Flag.STAR_KEY_CAPTURE]: the star button (KEYCODE_MACRO_1) is claimed so Google's smart-home
 *   screen doesn't also open, and the action chosen in Features → Star button action runs.
 * - [Flag.HOME_RETURN]: on Google TV Streamer a short Home press still opens Google's launcher even
 *   when Templar holds the HOME role. This watches window changes (no key events needed) and brings
 *   Templar back on top. It never consumes the Home key. Long-press Home (Google's dashboard with
 *   the Google Home panel) is never touched: only Google's HomeActivity triggers a bounce, and the
 *   bounce is cancelled if the dashboard window or a Home long-press shows up.
 *
 * The service only asks Android for key events while a key flag is on. It is enabled via adb (it
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

    private fun starCaptureWanted(): Boolean =
        FeatureFlags.isEnabled(this, Flag.STAR_KEY_CAPTURE) &&
            FeatureFlags.starAction(this) != StarAction.GOOGLE_DEFAULT

    /** Only ask the system for key events while a key flag is on. */
    private fun applyKeyFilter() {
        val info = serviceInfo ?: return
        val wanted = FeatureFlags.isEnabled(this, Flag.SETTINGS_KEY_CAPTURE) || starCaptureWanted()
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
            // Dashboard / Google Home panel / any other Google launcher window: hands off.
            lastOtherGoogleWindowAt = SystemClock.elapsedRealtime()
            frontIsGoogleHome = false
            handler.removeCallbacks(bounceRunnable)
            return
        }
        // Some other app (or Templar) took the front.
        frontPackage = pkg
        frontIsGoogleHome = false
        handler.removeCallbacks(bounceRunnable)
    }

    private fun templarIsHome(): Boolean {
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
        val decision = HomeGuard.shouldBounce(
            now = now,
            googleHomeStillFront = frontIsGoogleHome,
            otherGoogleWindowRecent = otherRecent,
            homeLongPressSeen = longPress,
            allowed = HomeGuard.isGoogleLauncherAllowed(),
            templarIsHome = templarIsHome(),
            pausedUntil = pausedUntil,
        )
        Log.d(TAG, "bounce decision=$decision front=$frontIsGoogleHome other=$otherRecent long=$longPress")
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
            STAR_KEYCODE -> {
                if (!starCaptureWanted()) return false
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) runStarAction()
                return true // consume both down and up so Google's smart-home screen doesn't also open
            }
        }
        return false
    }

    private fun runStarAction() {
        when (FeatureFlags.starAction(this)) {
            StarAction.QUICK_MENU -> openQuickMenu()
            StarAction.TEMPLAR_HOME -> runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            StarAction.GOOGLE_DEFAULT -> Unit
        }
    }

    private fun openQuickMenu() {
        runCatching {
            startActivity(
                Intent(this, QuickSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private companion object {
        const val TAG = "TemplarHomeGuard"
        const val STAR_KEYCODE = KeyEvent.KEYCODE_MACRO_1 // 313: the remote's star button
        const val LONG_PRESS_MEMORY_MS = 2_000L
    }
}
