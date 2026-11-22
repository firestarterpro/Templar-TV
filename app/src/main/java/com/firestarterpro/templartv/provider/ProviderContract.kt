package com.firestarterpro.templartv.provider

/**
 * Provider adapter contract designed for graceful degradation.
 * 
 * Each adapter should be able to describe capabilities rather than pretending
 * all providers are equal. A provider that cannot expose exact titles should
 * still be usable for launching, recording app sessions, and manual correction.
 * 
 * Do not scrape credentials. Do not automate user logins. Do not bypass provider
 * APIs or access controls. Do not hardcode unsupported assumptions as facts.
 */
interface ProviderContract {

    /** Whether the provider app is installed on the device */
    val installed: Boolean

    /** Launch the provider app */
    fun launchApp(context: android.content.Context)

    /** Describe provider capabilities */
    data class Capabilities(
        val installed: Boolean = false,
        val launchApp: Boolean = false,
        val searchCatalog: Boolean = false,
        val browseCatalog: Boolean = false,
        val publishPreviewRows: Boolean = false,
        val publishContinueWatchingItems: Boolean = false,
        val deepLinkTitle: Boolean = false,
        val deepLinkEpisode: Boolean = false,
        val provideMediaSessionState: Boolean = false,
        val provideMediaSessionTitle: Boolean = false,
        val exposeFocusedTileMetadata: Boolean = false,
        val supportExternalCatalogMatching: Boolean = false,
        val supportLocalWatchTracking: Boolean = false
    )

    /** Get provider capabilities */
    val capabilities: Capabilities
}