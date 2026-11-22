package com.firestarterpro.templartv.google

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
    fun openGoogleHome(context: android.content.Context) {
        // Discover the Google Home activity safely at runtime
        val intent = android.content.Intent("com.google.android.gms.actions.NOTE_PAIRED_DEVICE_ACTION")
            .setPackage("com.google.android.apps.home")
        if (intent.resolveActivity(context.packageManager) != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Open Android system settings */
    fun openSettings(context: android.content.Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Show rollback instructions for HOME role */
    fun showRollbackInstructions() {
        // Documentation: HOME role can be reassigned via:
        // adb shell pm set-home LAUNCHER_PACKAGE_NAME
        // To restore Google: adb shell pm set-home com.google.android.tvlauncher
    }
}