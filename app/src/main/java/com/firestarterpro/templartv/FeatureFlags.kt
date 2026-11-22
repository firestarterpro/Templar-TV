package com.firestarterpro.templartv

/**
 * Centralized feature flag mechanism for Templar TV.
 * 
 * Experimental features default off.
 * Passive and harmless foundations may be enabled only when they preserve current behavior.
 * Feature status should be inspectable from Templar TV settings or diagnostics when practical.
 */
object FeatureFlags {

    // Watch history
    val watchHistory = true  // Enabled by default to preserve current "Jump back in" behavior

    // Media session observation
    val mediaSessionObservation = true  // Enabled by default

    // Jellyfin integration
    val jellyfin = false  // Disabled by default, experimental

    // Provider-specific rows
    val providerRows = true  // Enabled by default

    // Preview and upcoming titles
    val previewAndUpcoming = true  // Enabled by default

    // OCR observation (experimental)
    val ocrObservation = false  // Disabled by default, experimental

    // Focused-tile observation (experimental)
    val focusedTileObservation = false  // Disabled by default, experimental

    // Custom voice routing
    val customVoiceRouting = false  // Disabled by default, experimental

    // Low confidence review queue
    val lowConfidenceReview = false  // Disabled by default, experimental

    // Google Home handoff
    val googleHomeHandoff = true  // Enabled by default for reversible Google Home access

    // Diagnostics
    val diagnostics = true  // Enabled by default for monitoring
}