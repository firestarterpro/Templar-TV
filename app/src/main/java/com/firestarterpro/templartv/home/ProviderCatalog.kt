package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.ProviderIds

/**
 * Display names and the Android packages each provider is expected to use. The packages are
 * [unverified]: the 2026-11-25 probe saw Netflix, Prime Video, Paramount+, Peacock and YouTube TV on
 * the Streamer, but not every package name was checked. Matching also falls back to the app label,
 * so a wrong package name only costs a fallback, never a missing shelf.
 */
object ProviderCatalog {

    private data class Info(val id: String, val label: String, val packages: List<String>, val labels: List<String>)

    private val all = listOf(
        Info(ProviderIds.PEACOCK, "Peacock", listOf("com.peacocktv.peacockandroid", "com.peacock.peacockfiretv", "com.peacocktv.peacock"), listOf("peacock")),
        Info(ProviderIds.PARAMOUNT_PLUS, "Paramount+", listOf("com.cbs.ott", "com.cbs.app", "com.cbs.tve"), listOf("paramount+", "paramount plus", "paramount")),
        Info(ProviderIds.PRIME_VIDEO, "Prime Video", listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), listOf("prime video", "amazon prime video")),
        Info(ProviderIds.MAX, "Max", listOf("com.wbd.stream", "com.hbo.hbonow", "com.hbo.hbomax"), listOf("max", "hbo max")),
        Info(ProviderIds.NETFLIX, "Netflix", listOf("com.netflix.ninja", "com.netflix.mediaclient"), listOf("netflix")),
        Info(ProviderIds.HULU, "Hulu", listOf("com.hulu.livingroomplus", "com.hulu.plus"), listOf("hulu")),
        Info(ProviderIds.JELLYFIN, "Jellyfin", listOf("org.jellyfin.androidtv"), listOf("jellyfin")),
    )

    fun label(providerId: String): String =
        all.firstOrNull { it.id == providerId }?.label ?: providerId.replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun packages(providerId: String): List<String> = all.firstOrNull { it.id == providerId }?.packages.orEmpty()

    /** The provider an app belongs to, by package first and then by app label, or "" if it is none of them. */
    fun providerFor(packageName: String, appLabel: String = ""): String {
        all.firstOrNull { packageName in it.packages }?.let { return it.id }
        val l = appLabel.trim().lowercase()
        if (l.isEmpty()) return ""
        return all.firstOrNull { l in it.labels }?.id.orEmpty()
    }
}
