package com.firestarterpro.templartv.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests. All titles here are made up; no real viewing data is ever committed. */
class MediaMetadataParserTest {

    private fun parsed(raw: RawMetadata, provider: String = "Peacock"): ParseResult.Parsed =
        MediaMetadataParser.parse(raw, provider) as ParseResult.Parsed

    private fun skipped(raw: RawMetadata, provider: String = "Peacock") =
        (MediaMetadataParser.parse(raw, provider) as ParseResult.Skipped).reason

    @Test fun markerInSubtitleMakesTitleTheShow() {
        val p = parsed(RawMetadata(title = "Test Show", displaySubtitle = "S2:E5 The Long Night", durationMs = 2_700_000))
        assertEquals("Test Show", p.title)
        assertEquals("Test Show", p.seriesTitle)
        assertEquals(2, p.seasonNumber); assertEquals(5, p.episodeNumber)
        assertEquals("The Long Night", p.episodeTitle)
        assertEquals("episode", p.contentType)
        assertEquals(1f, p.confidence, 0f)
    }

    @Test fun markerInsideTitleSplitsShowAndEpisode() {
        val p = parsed(RawMetadata(title = "Test Show S02E05 - The Long Night"))
        assertEquals("Test Show", p.seriesTitle)
        assertEquals("The Long Night", p.episodeTitle)
        assertEquals(2, p.seasonNumber); assertEquals(5, p.episodeNumber)
    }

    @Test fun wordyMarker() {
        val p = parsed(RawMetadata(title = "Test Show", displaySubtitle = "Season 3, Episode 11 • Finale"))
        assertEquals(3, p.seasonNumber); assertEquals(11, p.episodeNumber)
        assertEquals("Finale", p.episodeTitle)
    }

    @Test fun crossMarker() {
        val p = parsed(RawMetadata(title = "Test Show", artist = "4x07 Something"))
        assertEquals(4, p.seasonNumber); assertEquals(7, p.episodeNumber)
    }

    @Test fun episodeOnlyMarkerKeepsSeasonZero() {
        val p = parsed(RawMetadata(title = "Test Show", displaySubtitle = "Episode 7"))
        assertEquals(0, p.seasonNumber); assertEquals(7, p.episodeNumber)
        assertEquals("Test Show", p.seriesTitle)
    }

    @Test fun markerInTitleWithNothingBeforeUsesAlbumAsShow() {
        val p = parsed(RawMetadata(title = "S1:E2 The Name", album = "Test Show"))
        assertEquals("Test Show", p.seriesTitle)
        assertEquals("The Name", p.episodeTitle)
    }

    @Test fun resolutionIsNotASeason() {
        val p = parsed(RawMetadata(title = "Nature Film 1920x1080"))
        assertEquals(0, p.seasonNumber)
        assertEquals("", p.seriesTitle)
    }

    @Test fun titleOnlyIsKeptAsIs() {
        val p = parsed(RawMetadata(title = "Some Movie", durationMs = 6_000_000))
        assertEquals("Some Movie", p.title)
        assertEquals("", p.contentType)
        assertEquals(0.85f, p.confidence, 0f)
    }

    @Test fun episodeNameOnlyProvidersNeverConfirmOnTheirOwn() {
        // Real Peacock behaviour: title = display title = episode name, nothing else readable.
        val p = (MediaMetadataParser.parse(RawMetadata(title = "Pilot", displayTitle = "Pilot", durationMs = 2_700_000), "Peacock", titleIsEpisodeOnly = true)
            as ParseResult.Parsed)
        assertEquals("Pilot", p.episodeTitle)
        assertEquals("episode", p.contentType)
        // Even with the "opened from Templar" signal (0.5) the combined score stays under 0.80.
        assertTrue(1 - (1 - p.confidence) * (1 - 0.5f) < 0.80f)
    }

    @Test fun distinctAlbumWithoutMarkerIsLowConfidenceEpisode() {
        val p = parsed(RawMetadata(title = "The Name", album = "Test Show"))
        assertEquals("Test Show", p.seriesTitle)
        assertEquals("The Name", p.episodeTitle)
        assertTrue(p.confidence < 0.8f)
    }

    @Test fun albumSameAsTitleIsIgnored() {
        val p = parsed(RawMetadata(title = "Some Movie", album = "Some Movie"))
        assertEquals("Some Movie", p.title)
        assertEquals("", p.seriesTitle)
    }

    @Test fun displayTitleIsUsedWhenTitleIsBlank() {
        assertEquals("Some Movie", parsed(RawMetadata(title = " ", displayTitle = "Some Movie")).title)
    }

    @Test fun ignoresBlankAppNamePlaceholdersAndAds() {
        assertEquals(SkipReason.NO_TITLE, skipped(RawMetadata()))
        assertEquals(SkipReason.APP_NAME, skipped(RawMetadata(title = "Peacock")))
        assertEquals(SkipReason.APP_NAME, skipped(RawMetadata(title = "paramount+"), "Paramount+"))
        assertEquals(SkipReason.AD_OR_PLACEHOLDER, skipped(RawMetadata(title = "Advertisement")))
        assertEquals(SkipReason.AD_OR_PLACEHOLDER, skipped(RawMetadata(title = "Ad 1 of 3")))
        assertEquals(SkipReason.AD_OR_PLACEHOLDER, skipped(RawMetadata(title = "Loading")))
    }

    @Test fun appNameWithoutSpacesIsStillTheAppName() {
        // Real Prime Video behaviour: the description title is "PrimeVideo".
        assertEquals(SkipReason.APP_NAME, skipped(RawMetadata(title = "PrimeVideo"), "Prime Video"))
    }

    @Test fun realTitleInTheOtherTitleFieldIsUsed() {
        val p = parsed(RawMetadata(title = "PrimeVideo", displayTitle = "Some Movie"), "Prime Video")
        assertEquals("Some Movie", p.title)
    }

    @Test fun shortItemsAreNotShelfMaterial() {
        assertEquals(SkipReason.TOO_SHORT, skipped(RawMetadata(title = "Some Trailer Clip", durationMs = 30_000)))
        // Unknown duration (0) is allowed through: some apps never report it.
        parsed(RawMetadata(title = "Some Movie", durationMs = 0))
    }

    @Test fun onlyWebArtworkIsKept() {
        assertEquals("https://img.example/a.jpg", parsed(RawMetadata(title = "A", artUri = "https://img.example/a.jpg")).artworkRef)
        assertEquals("", parsed(RawMetadata(title = "A", artUri = "content://other.app/art/1")).artworkRef)
    }
}
