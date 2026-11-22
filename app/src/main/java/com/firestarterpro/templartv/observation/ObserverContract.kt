package com.firestarterpro.templartv.observation

/**
 * Clean module boundaries for the detection layers.
 * 
 * Preferred evidence order:
 * 1. Exact Jellyfin playback events
 * 2. MediaSession metadata and state
 * 3. Provider-published TV rows
 * 4. Focused-tile accessibility metadata
 * 5. Visible playback overlay metadata
 * 6. Consent-based occasional OCR
 * 7. Provider package plus duration and title-candidate matching
 * 8. Manual correction
 * 
 * MediaSession should ultimately be the first general streaming observer.
 * The observer should be able to capture when exposed:
 * - Active application/package
 * - Playing, Paused, Buffering, Stopped
 * - Playback position
 * - Playback speed
 * - Media title
 * - Episode title
 * - Artist
 * - Artwork or metadata references
 * 
 * Do not assume every provider supplies full metadata.
 * Focused-tile observation and OCR must remain experimental.
 * Do not create continuous screen recording.
 * Do not attempt to capture protected video frames.
 * Do not bypass secure surfaces or DRM.
 * Build interfaces/stubs only where implementation cannot be verified
 * without the physical device.
 */
interface ObserverContract {

    /** Media playback state */
    data class PlaybackState(
        val isPlaying: Boolean,
        val isPaused: Boolean,
        val isBuffering: Boolean,
        val isStopped: Boolean,
        val playbackPosition: Long,
        val playbackSpeed: Double,
        val mediaTitle: String?,
        val episodeTitle: String?,
        val artist: String?,
        val artworkReference: String?
    )

    /** Evidence source types */
    enum class EvidenceSource {
        MediaSession,
        FocusedTile,
        VisibleText,
        Ocr,
        TvProviderRow,
        JellyfinEvent,
        ExternalCatalog,
        ManualCorrection,
        AppSessionOnly,
        Unknown
    }

    /** Confidence level for title matches */
    data class MatchConfidence(
        val score: Float,  // 0.0 to 1.0
        val source: EvidenceSource,
        val evidenceDetails: String?,
        val manualCorrection: Boolean
    )

    /** Observation result */
    sealed class ObservationResult {
        object Success(val title: String, val confidence: MatchConfidence) : ObservationResult()
        object UnknownSession(val durationMillis: Long, val evidence: List<String>) : ObservationResult()
        object UserCancelled
        object NoActivePlayback
    }
}