package com.firestarterpro.templartv.observation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.firestarterpro.templartv.data.ProfileIds
import com.firestarterpro.templartv.data.TemplarData
import com.firestarterpro.templartv.data.TemplarDatabase
import com.firestarterpro.templartv.data.profile.ActiveProfileStore
import com.firestarterpro.templartv.data.shelf.ProgressKind
import com.firestarterpro.templartv.data.watchhistory.ReviewState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The observer's output going through the real data layer (in-memory Room). Made-up titles only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservationDataTest {

    private val p = ProfileIds.DEFAULT
    private val pkg = "com.peacocktv.peacockandroid"
    private val key = "$pkg/"
    private var launched = false
    private lateinit var db: TemplarDatabase
    private lateinit var data: TemplarData
    private val tracker = PlaybackTracker(launchedFromTemplar = { _, _, _ -> launched })
    private var now = 10_000_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TemplarDatabase::class.java)
            .allowMainThreadQueries().addCallback(TemplarDatabase.ensureDefaultProfile).build()
        data = TemplarData.create(db, { true }, ActiveProfileStore(context)) { now }
        runBlocking { data.profiles.ensureDefault() }
    }

    @After fun tearDown() = db.close()

    private fun play(md: RawMetadata, positionMs: Long, at: Long, state: PlayState = PlayState.PLAYING) = runBlocking {
        val snap = SessionSnapshot(key, pkg, "peacock", state, md, positionMs, at)
        tracker.onSnapshot(p, snap).filterIsInstance<TrackerEvent.Record>().forEach { data.history.record(it.observation) }
    }

    private val clean = RawMetadata(title = "Test Show", displaySubtitle = "S1:E2 Pilot", durationMs = 2_700_000)
    private val vague = RawMetadata(title = "The Name", album = "Test Show", durationMs = 2_700_000)

    private suspend fun shelf() = data.shelves.observeShelf(p, "peacock").first()

    @Test fun cleanReadingReachesTheShelfWithExactProgress() = runBlocking {
        play(clean, 600_000, now)
        val tile = shelf().single()
        assertEquals("Test Show", tile.title)
        assertEquals(1, tile.seasonNumber); assertEquals(2, tile.episodeNumber)
        assertEquals(600_000L, tile.progressMs)
        assertEquals(ProgressKind.EXACT, tile.progressKind)
    }

    @Test fun vagueReadingWaitsInTheReviewQueue() = runBlocking {
        play(vague, 600_000, now)
        assertTrue(shelf().isEmpty())
        assertEquals(ReviewState.NEEDS_REVIEW, data.history.observeReviewQueue(p).first().single().reviewState)
    }

    @Test fun vagueReadingPlusOpenedFromTemplarIsConfirmed() = runBlocking {
        launched = true
        play(vague, 600_000, now)
        assertEquals(1, shelf().size)
        assertTrue(data.history.observeReviewQueue(p).first().isEmpty())
    }

    @Test fun confirmingFromTheReviewQueueFillsTheShelf() = runBlocking {
        play(vague, 600_000, now)
        val id = data.history.observeReviewQueue(p).first().single().id.toLong()
        assertTrue(data.history.confirm(id))
        val tile = shelf().single()
        assertEquals("Test Show", tile.seriesTitle.ifBlank { tile.title })
        assertEquals(600_000L, tile.progressMs)
    }

    @Test fun correctingFromTheReviewQueueUsesTheNewTitle() = runBlocking {
        play(vague, 600_000, now)
        val id = data.history.observeReviewQueue(p).first().single().id.toLong()
        assertTrue(data.history.correct(id, "Real Show Name"))
        assertEquals("Real Show Name", shelf().single().title)
    }

    @Test fun aSecondEpisodeReplacesTheTileForTheSameSeries() = runBlocking {
        play(clean, 600_000, now)
        now += 3_000_000
        play(RawMetadata(title = "Test Show", displaySubtitle = "S1:E3 Next", durationMs = 2_700_000), 700_000, now)
        val tiles = shelf()
        assertEquals(1, tiles.size)
        assertEquals(3, tiles.single().episodeNumber)
    }

    @Test fun aRemovedTileStaysRemovedWhenSeenAgain() = runBlocking {
        play(clean, 600_000, now)
        data.shelves.remove(shelf().single().id)
        now += 60_000
        play(clean, 660_000, now)
        assertTrue(shelf().isEmpty())
        assertEquals(1, data.shelves.observeRemoved(p).first().size)
    }
}
