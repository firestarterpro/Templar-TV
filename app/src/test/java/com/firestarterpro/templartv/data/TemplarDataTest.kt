package com.firestarterpro.templartv.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.favourites.FavouriteShowRepository
import com.firestarterpro.templartv.data.layout.DefaultRows
import com.firestarterpro.templartv.data.profile.ActiveProfileStore
import com.firestarterpro.templartv.data.shelf.ShelfDraft
import com.firestarterpro.templartv.data.shelf.ShelfSource
import com.firestarterpro.templartv.data.shortcuts.ShortcutAction
import com.firestarterpro.templartv.data.shortcuts.ShortcutScope
import com.firestarterpro.templartv.data.sync.SyncFavourite
import com.firestarterpro.templartv.data.sync.SyncShelfEntry
import com.firestarterpro.templartv.data.watchhistory.EvidenceSource
import com.firestarterpro.templartv.data.watchhistory.PlaybackObservation
import com.firestarterpro.templartv.data.watchhistory.ReviewState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemplarDataTest {

    private val p = ProfileIds.DEFAULT
    private var now = 1_000L
    private val off = mutableSetOf<Flag>()
    private lateinit var db: TemplarDatabase
    private lateinit var data: TemplarData

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TemplarDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(TemplarDatabase.ensureDefaultProfile)
            .build()
        data = TemplarData.create(db, { it !in off }, ActiveProfileStore(context)) { ++now }
        runBlocking { data.profiles.ensureDefault() }
    }

    @After fun tearDown() = db.close()

    private fun draft(title: String, series: String = "", id: String = "") =
        ShelfDraft(title = title, seriesTitle = series, providerContentId = id)

    // Shelves

    @Test fun shelfAddsAtFrontAndOneTilePerSeries() = runBlocking {
        data.shelves.record(p, ProviderIds.PEACOCK, ShelfSource.MEDIA_SESSION, draft("Ep 1", "Show A", "e1"))
        data.shelves.record(p, ProviderIds.PEACOCK, ShelfSource.MEDIA_SESSION, draft("Movie B"))
        data.shelves.record(p, ProviderIds.PEACOCK, ShelfSource.MEDIA_SESSION, draft("Ep 2", "show a", "e2"))
        val shelf = data.shelves.observeShelf(p, ProviderIds.PEACOCK).first()
        assertEquals(2, shelf.size)
        assertEquals("Ep 2", shelf[0].title)
    }

    @Test fun shelfMoveReordersAndPinnedShowsFirst() = runBlocking {
        val a = data.shelves.record(p, ProviderIds.MAX, ShelfSource.MANUAL, draft("A"))!!
        val b = data.shelves.record(p, ProviderIds.MAX, ShelfSource.MANUAL, draft("B"))!!
        val c = data.shelves.record(p, ProviderIds.MAX, ShelfSource.MANUAL, draft("C"))!!
        assertEquals(listOf(c, b, a), data.shelves.observeShelf(p, ProviderIds.MAX).first().map { it.id })
        assertTrue(data.shelves.move(p, ProviderIds.MAX, a, 0))
        assertEquals(listOf(a, c, b), data.shelves.observeShelf(p, ProviderIds.MAX).first().map { it.id })
        data.shelves.setPinned(b, true)
        assertEquals(b, data.shelves.observeShelf(p, ProviderIds.MAX).first().first().id)
    }

    @Test fun removeKeepsRowAndObservationDoesNotBringItBack() = runBlocking {
        val id = data.shelves.record(p, ProviderIds.MAX, ShelfSource.MEDIA_SESSION, draft("Gone"))!!
        data.shelves.remove(id)
        assertTrue(data.shelves.observeShelf(p, ProviderIds.MAX).first().isEmpty())
        assertEquals(1, data.shelves.observeRemoved(p).first().size)
        assertNull(data.shelves.record(p, ProviderIds.MAX, ShelfSource.MEDIA_SESSION, draft("Gone")))
        assertEquals(id, data.shelves.record(p, ProviderIds.MAX, ShelfSource.TEMPLAR_LAUNCH, draft("Gone")))
        assertEquals(1, data.shelves.observeShelf(p, ProviderIds.MAX).first().size)
    }

    @Test fun changeProviderMergesDuplicates() = runBlocking {
        val a = data.shelves.record(p, ProviderIds.MAX, ShelfSource.MANUAL, draft("Same"))!!
        val b = data.shelves.record(p, ProviderIds.HULU, ShelfSource.MANUAL, draft("Same"))!!
        assertEquals(b, data.shelves.changeProvider(a, ProviderIds.HULU))
        assertTrue(data.shelves.observeShelf(p, ProviderIds.MAX).first().isEmpty())
        assertEquals(1, data.shelves.observeShelf(p, ProviderIds.HULU).first().size)
        val c = data.shelves.record(p, ProviderIds.MAX, ShelfSource.MANUAL, draft("Other"))!!
        assertEquals(c, data.shelves.changeProvider(c, ProviderIds.PEACOCK))
        assertEquals(1, data.shelves.observeShelf(p, ProviderIds.PEACOCK).first().size)
    }

    // Favourites

    @Test fun favouritesOrderRemoveRestore() = runBlocking {
        val a = data.favourites.add(p, "A")!!
        val b = data.favourites.add(p, "B")!!
        val c = data.favourites.add(p, "C")!!
        assertEquals(listOf(c, b, a), data.favourites.observe(p).first().map { it.id })
        data.favourites.move(p, a, 0)
        assertEquals(listOf(a, c, b), data.favourites.observe(p).first().map { it.id })
        data.favourites.remove(c)
        assertEquals(listOf(a, b), data.favourites.observe(p).first().map { it.id })
        assertEquals(c, data.favourites.add(p, "c"))
        assertEquals(3, data.favourites.observe(p).first().size)
    }

    // Flags

    @Test fun flagOffStopsWritesButKeepsData() = runBlocking {
        val id = data.favourites.add(p, "Keep")!!
        off += Flag.FAVOURITE_SHOWS
        assertNull(data.favourites.add(p, "New"))
        assertFalse(data.favourites.remove(id))
        assertEquals(1, data.favourites.observe(p).first().size)
        off += Flag.SHELF_EDITING
        assertNull(data.shelves.addManual(p, ProviderIds.MAX, draft("X")))
        off.clear(); off += Flag.WATCH_HISTORY
        assertNull(data.history.record(obs("Show", EvidenceSource.MEDIA_SESSION)))
    }

    // Rows

    @Test fun defaultRowsSeedOnceAndKeepUserEdits() = runBlocking {
        val shelfRow = DefaultRows.shelfRowId(ProviderIds.PEACOCK)
        data.rows.setVisible(p, shelfRow, false)
        data.rows.rename(p, shelfRow, "My Peacock")
        data.profiles.ensureDefault()
        val rows = data.rows.observe(p).first()
        assertEquals(DefaultRows.forProfile(p).size, rows.size)
        val row = rows.first { it.rowId == shelfRow }
        assertFalse(row.visible)
        assertEquals("My Peacock", row.titleOverride)
        assertFalse(rows.first { it.rowId == DefaultRows.shelfRowId(ProviderIds.NETFLIX) }.visible)
    }

    @Test fun rowMoveRenumbers() = runBlocking {
        data.rows.move(p, DefaultRows.APPS, 0)
        val rows = data.rows.observe(p).first()
        assertEquals(DefaultRows.APPS, rows.first().rowId)
        assertEquals(rows.indices.toList(), rows.map { it.position })
    }

    // Profiles

    @Test fun profilesKeepHistoriesApartAndArchiveNeverDeletes() = runBlocking {
        val kid = data.profiles.create("Kid")!!
        data.favourites.add(p, "Adult show")
        data.favourites.add(kid, "Kid show")
        assertEquals(listOf("Adult show"), data.favourites.observe(p).first().map { it.title })
        assertEquals(listOf("Kid show"), data.favourites.observe(kid).first().map { it.title })
        assertTrue(data.profiles.switchTo(kid))
        assertTrue(data.profiles.archive(kid))
        assertEquals(ProfileIds.DEFAULT, data.profiles.activeProfileId.value)
        assertFalse(data.profiles.switchTo(kid))
        assertFalse(data.profiles.archive(ProfileIds.DEFAULT))
        assertEquals(1, data.favourites.observe(kid).first().size)
        data.profiles.unarchive(kid)
        assertTrue(data.profiles.switchTo(kid))
    }

    @Test fun settingsFallBackToDefaults() = runBlocking {
        assertEquals("rows", data.profiles.setting(p, "home_view"))
        data.profiles.putSetting(p, "home_view", "columns")
        assertEquals("columns", data.profiles.setting(p, "home_view"))
        data.profiles.resetSetting(p, "home_view")
        assertEquals("rows", data.profiles.setting(p, "home_view"))
    }

    // Shortcuts

    @Test fun shortcutsRejectReservedAndUnsafeGlobalChords() = runBlocking {
        assertNull(data.shortcuts.bind(p, "HOME", ShortcutScope.IN_APP, ShortcutAction.QUICK_MENU))
        assertNull(data.shortcuts.bind(p, "K", ShortcutScope.GLOBAL, ShortcutAction.QUICK_MENU))
        assertNotNull(data.shortcuts.bind(p, "K", ShortcutScope.IN_APP, ShortcutAction.QUICK_MENU))
    }

    @Test fun shortcutsGlobalNeedsItsFlagAndRebindRevives() = runBlocking {
        off += Flag.GLOBAL_SHORTCUTS
        assertNull(data.shortcuts.bind(p, "ctrl+K", ShortcutScope.GLOBAL, ShortcutAction.QUICK_MENU))
        off.clear()
        val id = data.shortcuts.bind(p, "k + ctrl", ShortcutScope.GLOBAL, ShortcutAction.QUICK_MENU)!!
        data.shortcuts.unbind(id)
        assertTrue(data.shortcuts.observe(p).first().isEmpty())
        assertEquals(id, data.shortcuts.bind(p, "CTRL+K", ShortcutScope.GLOBAL, ShortcutAction.VOICE_SEARCH))
        assertEquals(ShortcutAction.VOICE_SEARCH, data.shortcuts.observe(p).first().single().action)
    }

    // Watch history

    private fun obs(title: String, source: EvidenceSource, at: Long = now, detail: String = "", conf: Float? = null) =
        PlaybackObservation(
            profileId = p, provider = ProviderIds.PEACOCK, title = title, source = source, observedAt = at,
            seriesTitle = title, positionMs = 60_000, runtimeMs = 600_000, detail = detail, sourceConfidence = conf,
        )

    @Test fun mediaSessionObservationIsConfirmedAndReachesTheShelf() = runBlocking {
        val id = data.history.record(obs("Show", EvidenceSource.MEDIA_SESSION, detail = "title match"))!!
        val entry = db.watchHistoryDao().get(id)!!
        assertEquals(ReviewState.CONFIRMED, entry.reviewState)
        assertEquals(10, entry.completionPercentage)
        assertEquals(1, data.shelves.observeShelf(p, ProviderIds.PEACOCK).first().size)
        assertEquals(1, data.history.observeRecent(p).first().size)
    }

    @Test fun sameSittingExtendsOneSessionAndAddsNoDuplicateEvidence() = runBlocking {
        val first = data.history.record(obs("Show", EvidenceSource.MEDIA_SESSION, at = 10_000, detail = "t"))!!
        val second = data.history.record(obs("Show", EvidenceSource.MEDIA_SESSION, at = 20_000, detail = "t"))!!
        assertEquals(first, second)
        assertEquals(1, db.watchHistoryDao().evidenceFor(first).size)
        val later = data.history.record(obs("Show", EvidenceSource.MEDIA_SESSION, at = 10_000 + 3_600_000, detail = "t"))!!
        assertTrue(later != first)
    }

    @Test fun weakEvidenceWaitsInReviewAndStaysOffTheShelf() = runBlocking {
        val id = data.history.record(obs("Maybe", EvidenceSource.OCR, conf = 0.4f))!!
        assertEquals(ReviewState.NEEDS_REVIEW, db.watchHistoryDao().get(id)!!.reviewState)
        assertEquals(1, data.history.observeReviewQueue(p).first().size)
        assertTrue(data.shelves.observeShelf(p, ProviderIds.PEACOCK).first().isEmpty())
        assertTrue(data.history.observeRecent(p).first().isEmpty())
    }

    @Test fun manualConfirmSettlesAndDismissSticks() = runBlocking {
        val id = data.history.record(obs("Maybe", EvidenceSource.PATTERN))!!
        assertTrue(data.history.confirm(id))
        val e = db.watchHistoryDao().get(id)!!
        assertEquals(ReviewState.CONFIRMED, e.reviewState)
        assertEquals(1f, e.matchConfidence, 0f)

        val other = data.history.record(obs("Other", EvidenceSource.PATTERN))!!
        data.history.dismiss(other)
        data.history.record(obs("Other", EvidenceSource.MEDIA_SESSION, at = now + 1))
        assertEquals(ReviewState.DISMISSED, db.watchHistoryDao().get(other)!!.reviewState)
    }

    @Test fun historyRemoveIsSoft() = runBlocking {
        val id = data.history.record(obs("Show", EvidenceSource.MEDIA_SESSION))!!
        data.history.remove(id)
        assertTrue(data.history.observeRecent(p).first().isEmpty())
        assertNotNull(db.watchHistoryDao().get(id))
        data.history.restore(id)
        assertEquals(1, data.history.observeRecent(p).first().size)
    }

    // Sync

    @Test fun syncFavouritesCreateUpdateReorderRemoveAndPull() = runBlocking {
        val t0 = now
        val a = data.sync.upsertFavourite(SyncFavourite("uuid-a", p, "Alpha"))!!
        val b = data.sync.upsertFavourite(SyncFavourite("uuid-b", p, "Beta"))!!
        assertEquals("uuid-a", a.syncId)
        assertTrue(data.sync.reorderFavourites(p, listOf("uuid-a", "uuid-b")))
        assertEquals(listOf("uuid-a", "uuid-b"), data.sync.favouritesSince(p, t0).items.sortedBy { it.position }.map { it.syncId })
        val renamed = data.sync.upsertFavourite(a.copy(title = "Alpha 2"))!!
        assertEquals("Alpha 2", renamed.title)
        val mark = data.sync.favouritesSince(p, 0).asOf
        assertTrue(data.sync.softDeleteFavourite("uuid-b"))
        val changes = data.sync.favouritesSince(p, mark).items
        assertEquals(listOf("uuid-b"), changes.map { it.syncId })
        assertNotNull(changes.single().removedAt)
        assertTrue(data.sync.restoreFavourite("uuid-b"))
        assertFalse(data.sync.softDeleteFavourite("nope"))
        assertEquals(b.syncId, data.sync.favouritesSince(p, 0).items.first { it.syncId == "uuid-b" }.syncId)
    }

    @Test fun syncShelfEntryJellyfinPinMoveProviderAndOrder() = runBlocking {
        val pin = SyncShelfEntry("uuid-j", p, "jellyfin", "Movie", externalId = "jf-123", artworkRef = "http://x/p.jpg", pinned = true)
        val saved = data.sync.upsertShelfEntry(pin)!!
        assertEquals("MANUAL", saved.source)
        assertEquals("jf-123", saved.externalId)
        assertTrue(saved.pinned)
        data.sync.upsertShelfEntry(SyncShelfEntry("uuid-k", p, "jellyfin", "Other"))
        assertTrue(data.sync.reorderShelf(p, "jellyfin", listOf("uuid-k", "uuid-j")))
        val moved = data.sync.upsertShelfEntry(saved.copy(provider = ProviderIds.MAX))!!
        assertEquals(ProviderIds.MAX, moved.provider)
        assertEquals("uuid-j", moved.syncId)
        assertTrue(data.sync.softDeleteShelfEntry("uuid-j"))
        assertNotNull(data.sync.shelfEntriesSince(p, 0).items.first { it.syncId == "uuid-j" }.removedAt)
    }

    @Test fun syncRowsHideRenameAndReorder() = runBlocking {
        val row = data.sync.rowsSince(p, 0).items.first { it.rowId == DefaultRows.APPS }
        val updated = data.sync.upsertRow(row.copy(visible = false, titleOverride = "Mine"))!!
        assertFalse(updated.visible)
        assertEquals("Mine", updated.titleOverride)
        assertNull(data.sync.upsertRow(row.copy(rowId = "no_such_row")))
        assertTrue(data.sync.reorderRows(p, listOf(DefaultRows.APPS)))
        assertEquals(DefaultRows.APPS, data.sync.rowsSince(p, 0).items.minByOrNull { it.position }!!.rowId)
    }

    @Test fun syncWritesRefusedWhenFlagOff() = runBlocking {
        off += Flag.FAVOURITE_SHOWS
        assertNull(data.sync.upsertFavourite(SyncFavourite("uuid-z", p, "Z")))
    }
}
