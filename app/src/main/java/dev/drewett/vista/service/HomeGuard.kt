package com.firestarterpro.templartv.service

import android.os.SystemClock

/**
 * Shared state for "Home opens Templar" ([com.firestarterpro.templartv.Flag.HOME_RETURN]).
 *
 * Lives in the app process next to [VistaAccessibilityService], so Templar's own screens can tell
 * the service "I am opening Google's launcher on purpose, don't bring me back".
 */
object HomeGuard {
    /** Google TV's launcher. Its HomeActivity is what a short Home press opens on the Streamer. */
    const val GOOGLE_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"
    const val GOOGLE_HOME_ACTIVITY_SUFFIX = ".HomeActivity"

    @Volatile
    private var allowUntil = 0L

    /** Let Google's launcher stay in front for [millis] (used by deliberate "go to Google TV Home"). */
    fun allowGoogleLauncherFor(millis: Long = 60_000L) {
        allowUntil = SystemClock.elapsedRealtime() + millis
    }

    fun isGoogleLauncherAllowed(): Boolean = SystemClock.elapsedRealtime() < allowUntil

    fun isGoogleHomeScreen(packageName: CharSequence?, className: CharSequence?): Boolean =
        packageName?.toString() == GOOGLE_LAUNCHER_PACKAGE &&
            className?.toString()?.endsWith(GOOGLE_HOME_ACTIVITY_SUFFIX) == true

    /**
     * Pure decision, kept free of Android types so it can be unit tested.
     * [otherGoogleWindowRecent] is true when Google's dashboard (or any non-HomeActivity launcher
     * window) appeared just before or after the HomeActivity, which is what a long-press looks like.
     */
    fun shouldBounce(
        now: Long,
        googleHomeStillFront: Boolean,
        otherGoogleWindowRecent: Boolean,
        homeLongPressSeen: Boolean,
        allowed: Boolean,
        templarIsHome: Boolean,
        pausedUntil: Long,
    ): Boolean {
        if (!googleHomeStillFront || otherGoogleWindowRecent || homeLongPressSeen) return false
        if (allowed || !templarIsHome) return false
        if (now < pausedUntil) return false
        return true
    }

    const val BOUNCE_DELAY_MS = 300L
    const val OTHER_WINDOW_MEMORY_MS = 1_500L
    const val BREAKER_WINDOW_MS = 10_000L
    const val BREAKER_MAX_BOUNCES = 4
    const val BREAKER_PAUSE_MS = 60_000L
}
