package com.firestarterpro.templartv.data

import com.firestarterpro.templartv.data.shortcuts.Chords
import com.firestarterpro.templartv.data.shortcuts.ShortcutScope
import com.firestarterpro.templartv.data.watchhistory.Confidence
import com.firestarterpro.templartv.data.watchhistory.EvidenceSource
import com.firestarterpro.templartv.data.watchhistory.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderingTest {
    @Test fun moveToFrontMiddleEnd() {
        val ids = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(3L, 1L, 2L, 4L), Ordering.moveTo(ids, 3L, 0))
        assertEquals(listOf(1L, 3L, 2L, 4L), Ordering.moveTo(ids, 3L, 1))
        assertEquals(listOf(1L, 2L, 4L, 3L), Ordering.moveTo(ids, 3L, 99))
        assertEquals(listOf(3L, 1L, 2L, 4L), Ordering.moveTo(ids, 3L, -5))
    }

    @Test fun moveUnknownIdChangesNothing() {
        assertEquals(listOf(1L, 2L), Ordering.moveTo(listOf(1L, 2L), 9L, 0))
    }

    @Test fun applyOrderPutsListedFirstAndKeepsTheRest() {
        assertEquals(listOf("c", "a", "b", "d"), Ordering.applyOrder(listOf("a", "b", "c", "d"), listOf("c", "a")))
    }

    @Test fun applyOrderIgnoresUnknownAndRepeated() {
        assertEquals(listOf("b", "a", "c"), Ordering.applyOrder(listOf("a", "b", "c"), listOf("b", "zzz", "b", "a")))
    }
}

class DedupeKeyTest {
    @Test fun normalizeIgnoresCaseAndPunctuation() {
        assertEquals(DedupeKey.normalize("Law & Order: SVU"), DedupeKey.normalize("law order  svu"))
    }

    @Test fun episodesOfOneSeriesShareAKey() {
        val a = DedupeKey.forShelf("Yellowstone", "ep-1", "Episode 1")
        val b = DedupeKey.forShelf("yellowstone", "ep-2", "Episode 2")
        assertEquals(a, b)
    }

    @Test fun movieUsesContentIdThenTitle() {
        assertEquals("c:abc", DedupeKey.forShelf("", "abc", "Heat"))
        assertEquals("t:heat", DedupeKey.forShelf("", "", "Heat"))
    }

    @Test fun favouritePrefersTmdb() {
        assertEquals("tmdb:42", DedupeKey.forFavourite(42, "Anything"))
        assertEquals("t:the bear", DedupeKey.forFavourite(0, "The Bear"))
    }
}

class ConfidenceTest {
    private val ms = EvidenceSource.MEDIA_SESSION.name

    @Test fun noEvidenceIsZero() = assertEquals(0f, Confidence.combine(emptyList()), 0f)

    @Test fun repeatedSignalsFromOneSourceDoNotStack() {
        val once = Confidence.combine(listOf(ms to 0.5f))
        val many = Confidence.combine(List(10) { ms to 0.5f })
        assertEquals(once, many, 0.0001f)
    }

    @Test fun independentSourcesRaiseConfidence() {
        val one = Confidence.combine(listOf(EvidenceSource.FOCUSED_TILE.name to 0.6f))
        val two = Confidence.combine(listOf(EvidenceSource.FOCUSED_TILE.name to 0.6f, EvidenceSource.OCR.name to 0.55f))
        assertTrue(two > one)
        assertTrue(two < 1f)
    }

    @Test fun manualSettlesIt() {
        assertEquals(1f, Confidence.combine(listOf(EvidenceSource.PATTERN.name to 0.1f, EvidenceSource.MANUAL.name to 1f)), 0f)
    }

    @Test fun weakSourceAloneNeedsReview() {
        val c = Confidence.combine(listOf(EvidenceSource.PATTERN.name to EvidenceSource.PATTERN.baseConfidence))
        assertEquals(ReviewState.NEEDS_REVIEW, Confidence.stateFor(c))
    }

    @Test fun mediaSessionAloneIsConfirmed() {
        val c = Confidence.combine(listOf(ms to EvidenceSource.MEDIA_SESSION.baseConfidence))
        assertEquals(ReviewState.CONFIRMED, Confidence.stateFor(c))
    }
}

class ChordsTest {
    @Test fun canonicalOrdersAndUppercases() {
        assertEquals("CTRL+F4", Chords.canonical("f4 + ctrl"))
        assertEquals("CTRL+ALT+SHIFT+K", Chords.canonical("shift+alt+ctrl+k"))
    }

    @Test fun badChordsAreRejected() {
        assertNull(Chords.canonical(""))
        assertNull(Chords.canonical("ctrl+"))
        assertNull(Chords.canonical("a+b"))
    }

    @Test fun reservedKeysCannotBeBound() {
        assertFalse(Chords.isAllowed("HOME", ShortcutScope.IN_APP))
        assertFalse(Chords.isAllowed("ctrl+ASSIST", ShortcutScope.IN_APP))
        assertFalse(Chords.isAllowed("VOLUME_UP", ShortcutScope.GLOBAL))
    }

    @Test fun globalNeedsModifierOrFunctionKey() {
        assertFalse(Chords.isAllowed("K", ShortcutScope.GLOBAL))
        assertTrue(Chords.isAllowed("ctrl+K", ShortcutScope.GLOBAL))
        assertTrue(Chords.isAllowed("F4", ShortcutScope.GLOBAL))
        assertFalse(Chords.isAllowed("F13", ShortcutScope.GLOBAL))
        assertTrue(Chords.isAllowed("K", ShortcutScope.IN_APP))
    }
}
