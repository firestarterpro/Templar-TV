package com.firestarterpro.templartv.jellyfin

/**
 * Safe integration boundary for Jellyfin personal server content.
 * 
 * Future Jellyfin integration should support:
 * - Libraries
 * - Continue Watching
 * - Next Up
 * - Recently Added
 * - Favorites
 * - Playlists
 * - Music
 * - Personal/home videos
 * - Exact playback position
 * - Completion/Resume
 * - Artwork
 * - Deep links or playback intents
 * - Server connection status
 * 
 * No live Jellyfin connection is required for this foundation checkpoint.
 * No server address, Jellyfin URL, username, token, library name, or network path
 * is assumed or committed.
 */
interface JellyfinContract {

    /** Library information */
    data class Library(
        val id: String,
        val name: String,
        val art: String?,
        val unit: String?,
        val type: String,
        val tags: List<String>,
        val callbacks: Callbacks?

    interface Callbacks {
        fun onLibrariesUpdated()
        fun onLibrarySelected(libraryId: String)
    }

    /** Continue Watching entries */
    data class ContinueWatchingEntry(
        val itemId: String,
        val title: String,
        val artworkUrl: String?,
        val percentWatched: Float,
        val lastViewed: Long
    )

    /** Next Up entries */
    data class NextUpEntry(
        val title: String,
        val artworkUrl: String?,
        val airingWindowStart: Long,
        val airingWindowEnd: Long
    )

    /** Recently Added entries */
    data class RecentlyAddedEntry(
        val title: String,
        val artworkUrl: String?,
        val dateAdded: Long
    )

    /** Server connection status */
    data class ConnectionStatus(
        val connected: Boolean,
        val serverUrl: String?,
        val errorMessage: String?
    )