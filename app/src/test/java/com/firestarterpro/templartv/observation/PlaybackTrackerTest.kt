package com.firestarterpro.templartv.observation

import com.firestarterpro.templartv.data.watchhistory.EvidenceSource
import com.firestarterpro.templartv.data.watchhistory.PlaybackObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackerTest {

    private val pkg = "com.peacocktv.peacockandroid"
    private val key = "$pkg/"
    private val p = "default"
    private var launched = false
    private val tracker = PlaybackTracker(launchedFromTemplar = { _, _, _ -> launched })

    private fun meta(title: String = "Test Show", sub: String = "S1:E2 Pilot") =
        RawMetadata(title = title, displaySubtitle = sub, durationMs = 2_700_000)

    private fun snap(at: Long, state: PlayState = PlayState.PLAYING, pos: Long = 0, md: RawMetadata? = meta()) =
        SessionSnapshot(key, pkg, "peacock", state, md, pos, at)

    private fun records(events: List<TrackerEvent>): List<PlaybackObservation> =
        events.filterIsInstance<TrackerEvent.Record>().map { it.observation }

    @Test fun previewThatPlaysBrieflyIsNotRecorded() {
        assertTrue(records(tracker.onSnapshot(p, snap(0))).isEmpty())
        assertTrue(records(tracker.onSnapshot(p, snap(20_000, pos = 20_000))).isEmpty())
        // Autoplay preview ends: session goes away without ever qualifying.
        assertTrue(records(tracker.onSessionEnded(p, key, 25_000)).isEmpty())
    }

    @Test fun recordsOnceItHasPlayedLongEnough() {
        tracker.onSnapshot(p, snap(0))
        tracker.onSnapshot(p, snap(30_000, pos = 30_000))
        val r = records(tracker.onSnapshot(p, snap(50_000, pos = 50_000)))
        assertEquals(1, r.size)
        val o = r.single()
        assertEquals(EvidenceSource.MEDIA_SESSION, o.source)
        assertEquals("Test Show", o.title); assertEquals("Test Show", o.seriesTitle)
        assertEquals(1, o.seasonNumber); assertEquals(2, o.episodeNumber)
        assertEquals("playing", o.playbackState)
        assertEquals(50_000L, o.positionMs)
        assertNull(o.sourceConfidence)
        assertEquals(2_700_000L, o.runtimeMs)
    }

    @Test fun startingFarInQualifiesAtOnce() {
        val r = records(tracker.onSnapshot(p, snap(0, pos = 25 * 60_000L)))
        assertEquals(1, r.size)
    }

    @Test fun heartbeatSavesPositionEveryMinuteOnly() {
        tracker.onSnapshot(p, snap(0, pos = 600_000))             // qualifies, recorded
        assertTrue(records(tracker.onSnapshot(p, snap(30_000, pos = 630_000))).isEmpty())
        assertEquals(1, records(tracker.onSnapshot(p, snap(60_000, pos = 660_000))).size)
        assertTrue(records(tracker.onSnapshot(p, snap(90_000, pos = 690_000))).isEmpty())
    }

    @Test fun pauseAndStopSaveThePosition() {
        tracker.onSnapshot(p, snap(0, pos = 600_000))
        val paused = records(tracker.onSnapshot(p, snap(10_000, PlayState.PAUSED, 610_000)))
        assertEquals("paused", paused.single().playbackState)
        assertEquals(610_000L, paused.single().positionMs)
        val ended = records(tracker.onSessionEnded(p, key, 20_000))
        assertEquals("stopped", ended.single().playbackState)
    }

    @Test fun nextEpisodeSavesTheOldOneThenStartsFresh() {
        tracker.onSnapshot(p, snap(0, pos = 2_600_000))
        val events = records(tracker.onSnapshot(p, snap(60_000, pos = 5_000, md = meta(sub = "S1:E3 Next"))))
        // The finished episode is closed out; the new one has not played long enough yet.
        assertEquals(1, events.size)
        assertEquals(2, events.single().episodeNumber)
        assertEquals("stopped", events.single().playbackState)
    }

    @Test fun anAdInTheMiddleDoesNotEndTheShowOrCountAsPlay() {
        tracker.onSnapshot(p, snap(0, pos = 600_000))
        val ad = tracker.onSnapshot(p, snap(10_000, md = RawMetadata(title = "Advertisement", durationMs = 30_000)))
        assertTrue(records(ad).isEmpty())
        // Show resumes: still the same track, no "stopped" record in between.
        val back = records(tracker.onSnapshot(p, snap(40_000, pos = 610_000)))
        assertTrue(back.none { it.playbackState == "stopped" })
        assertTrue(tracker.activeKeys().contains(key))
    }

    @Test fun noMetadataEndsTheTrack() {
        tracker.onSnapshot(p, snap(0, pos = 600_000))
        val r = records(tracker.onSnapshot(p, snap(5_000, md = null)))
        assertEquals("stopped", r.single().playbackState)
        assertTrue(tracker.activeKeys().isEmpty())
    }

    @Test fun lowConfidenceReadingTravelsAsSourceConfidence() {
        val r = records(tracker.onSnapshot(p, snap(0, pos = 600_000, md = RawMetadata(title = "The Name", album = "Test Show"))))
        assertEquals(0.7f, r.single().sourceConfidence!!, 0.001f)
    }

    @Test fun openingFromTemplarIsAddedAsSupportingEvidence() {
        launched = true
        val o = records(tracker.onSnapshot(p, snap(0, pos = 600_000))).single()
        val c = o.corroboration.single()
        assertEquals(EvidenceSource.TEMPLAR_LAUNCH, c.source)
        assertEquals(PlaybackTracker.LAUNCH_HINT_CONFIDENCE, c.confidence, 0f)
    }

    @Test fun noCorroborationWhenNotOpenedFromTemplar() {
        launched = false
        assertTrue(records(tracker.onSnapshot(p, snap(0, pos = 600_000))).single().corroboration.isEmpty())
    }
}
