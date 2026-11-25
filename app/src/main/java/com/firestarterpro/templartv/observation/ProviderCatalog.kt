package com.firestarterpro.templartv.observation

import com.firestarterpro.templartv.data.ProviderIds

/**
 * Which installed app belongs to which streaming provider. Pure data, so it is unit-tested and can
 * be extended without touching the observer. Package names are the known Android TV / Google TV
 * builds; a package that is not listed is simply not observed (the observer never guesses).
 *
 * Verified on the owner's Google TV Streamer (2026-11-25): Netflix, Prime Video, Paramount+
 * (`com.cbs.ott`), Peacock. Max, Hulu and Jellyfin were not installed there, so their entries are
 * unverified and marked as such.
 */
object ProviderCatalog {

    data class Entry(
        val provider: String,
        val packageName: String,
        /** True once the package was seen on the owner's device. */
        val verified: Boolean = false,
    )

    val entries: List<Entry> = listOf(
        Entry(ProviderIds.PEACOCK, "com.peacocktv.peacockandroid", verified = true),
        Entry(ProviderIds.PARAMOUNT_PLUS, "com.cbs.ott", verified = true),
        Entry(ProviderIds.PARAMOUNT_PLUS, "com.cbs.app"),
        Entry(ProviderIds.PRIME_VIDEO, "com.amazon.amazonvideo.livingroom"),
        Entry(ProviderIds.PRIME_VIDEO, "com.amazon.avod.thirdpartyclient", verified = true),
        Entry(ProviderIds.MAX, "com.wbd.stream"),
        Entry(ProviderIds.MAX, "com.hbo.hbonow"),
        Entry(ProviderIds.MAX, "com.hbo.hbomax"),
        Entry(ProviderIds.NETFLIX, "com.netflix.ninja", verified = true),
        Entry(ProviderIds.NETFLIX, "com.netflix.mediaclient"),
        Entry(ProviderIds.HULU, "com.hulu.livingroomplus"),
        Entry(ProviderIds.HULU, "com.hulu.plus"),
    )

    private val byPackage = entries.associateBy { it.packageName }

    /** The provider id for an app package, or null if the app is not a known provider. */
    fun providerFor(packageName: String): String? = byPackage[packageName]?.provider

    fun packagesFor(provider: String): List<String> = entries.filter { it.provider == provider }.map { it.packageName }

    /**
     * Providers whose MediaSession title is only the episode name, with no show name in any field
     * (measured on the owner's Streamer: Peacock publishes title and display title = episode name,
     * plus duration, and nothing else readable). A title like that is not enough to name a shelf
     * tile, so it goes to the review queue.
     */
    fun titleIsEpisodeNameOnly(provider: String): Boolean = provider == ProviderIds.PEACOCK

    /** Display name used to reject metadata that is only the app's own name. */
    fun displayName(provider: String): String = when (provider) {
        ProviderIds.PEACOCK -> "Peacock"
        ProviderIds.PARAMOUNT_PLUS -> "Paramount+"
        ProviderIds.PRIME_VIDEO -> "Prime Video"
        ProviderIds.MAX -> "Max"
        ProviderIds.NETFLIX -> "Netflix"
        ProviderIds.HULU -> "Hulu"
        else -> provider
    }
}
