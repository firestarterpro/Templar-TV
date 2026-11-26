package com.firestarterpro.templartv.observation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.firestarterpro.templartv.data.shelf.ShelfDraft
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.data.shelf.ShelfRepository
import com.firestarterpro.templartv.data.shelf.ShelfSource

/**
 * Opens a "recently played" tile in its provider app and moves the tile to the front of its shelf.
 * The home UI (B4) calls this when a tile is selected.
 *
 * The tile's saved progress is kept as it was: a launch says "I chose this", not "I watched this",
 * so it never resets a resume position. If the tile has a deep link and some app can open it, that
 * is used; otherwise the provider app is opened. Returns false when nothing could be opened.
 */
class ShelfLauncher(
    private val context: Context,
    private val shelves: ShelfRepository,
    private val launches: ProviderLaunchStore = ProviderLaunchStore(context),
) {
    suspend fun launch(entry: ShelfEntry): Boolean {
        val pm = context.packageManager
        val packageName = entry.packageName.ifBlank {
            ProviderCatalog.packagesFor(entry.provider).firstOrNull { pm.getLaunchIntentForPackage(it) != null }.orEmpty()
        }
        val intent = deepLinkIntent(entry, packageName) ?: appIntent(packageName) ?: return false
        val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!started) return false
        if (packageName.isNotBlank()) launches.mark(packageName)
        shelves.record(
            entry.profileId, entry.provider, ShelfSource.TEMPLAR_LAUNCH,
            ShelfDraft(
                title = entry.title, seriesTitle = entry.seriesTitle, contentType = entry.contentType,
                seasonNumber = entry.seasonNumber, episodeNumber = entry.episodeNumber, episodeTitle = entry.episodeTitle,
                providerContentId = entry.providerContentId, deepLink = entry.deepLink, packageName = packageName,
                tmdbId = entry.tmdbId, imdbId = entry.imdbId, artworkRef = entry.artworkRef,
                progressMs = entry.progressMs, runtimeMs = entry.runtimeMs, progressKind = entry.progressKind,
                watched = entry.watched, watchHistoryId = entry.watchHistoryId,
            ),
        )
        return true
    }

    private fun deepLinkIntent(entry: ShelfEntry, packageName: String): Intent? {
        if (entry.deepLink.isBlank()) return null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.deepLink))
        if (packageName.isNotBlank()) intent.setPackage(packageName)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    private fun appIntent(packageName: String): Intent? {
        if (packageName.isBlank()) return null
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
    }
}
