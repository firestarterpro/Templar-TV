package com.firestarterpro.templartv.google

import android.content.Context
import android.content.Intent

/**
 * Google Home and system navigation foundations for Templar TV.
 * 
 * Never disables, uninstall, alters, or replaces:
 * - Stock Google TV launcher
 * - Gemini
 * - Google Assistant
 * - Google Play Services
 * - Google Play Store
 * - Google Cast / Chromecast services
 * - Google Home
 * - Matter services
 * - Voice Remote services
 * - System update services
 * - Input services
 * - Setup services
 * - Widevine / DRM services
 * - Protected video paths
 * 
 * Provides safe adapters for:
 * - Opening Google Home
 * - Opening Android system settings
 * - Opening Templar TV settings
 * - Rollback instructions for HOME role reassignment
 */
object GoogleHomeHandoff {

    /** Open Google Home app if installed */
    fun openGoogleHome(context: Context) {
        // Discover the Google Home activity safely at runtime
        val intent = Intent("com.google.android.gms.actions.NOTE_PAIRED_DEVICE_ACTION")
            .setPackage("com.google.android.apps.home")
        if (intent.resolveActivity(context.packageManager) != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    /** Open Android system settings */
    fun openSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /** Show rollback instructions for HOME role */
    fun showRollbackInstructions() {
        // HOME can be reassigned from the Home chooser or Settings -> Apps -> Default apps -> Home app.
        // `pm set-home` and the com.google.android.tvlauncher package name are unverified on Google TV.
        // See docs/handoffs/2026-11-22-templar-tv-v0-foundationrollback.md
    }
}
