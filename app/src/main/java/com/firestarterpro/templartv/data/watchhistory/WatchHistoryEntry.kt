package com.firestarterpro.templartv.data.watchhistory

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlin.time.DurationKt.minute

@Entity(tableName = "watch_history")
data class WatchHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    /** Local profile identifier */
    @ColumnInfo(name = "profile_id")
    var profileId: String = "",

    /** Provider name */
    @ColumnInfo(name = "provider")
    var provider: String = "",

    /** Provider account alias without credentials */
    @ColumnInfo(name = "provider_alias")
    var providerAlias: String = "",

    /** Application package */
    @ColumnInfo(name = "package_name")
    var packageName: String = "",

    /** Content type */
    @ColumnInfo(name = "content_type")
    var contentType: String = "",

    /** Canonical title */
    @ColumnInfo(name = "canonical_title")
    var canonicalTitle: String = "",

    /** Original title */
    @ColumnInfo(name = "original_title")
    var originalTitle: String = "",

    /** Series title */
    @ColumnInfo(name = "series_title")
    var seriesTitle: String = "",

    /** Season number */
    @ColumnInfo(name = "season_number")
    var seasonNumber: Int = 0,

    /** Episode number */
    @ColumnInfo(name = "episode_number")
    var episodeNumber: Int = 0,

    /** Episode title */
    @ColumnInfo(name = "episode_title")
    var episodeTitle: String = "",

    /** Provider content ID */
    @ColumnInfo(name = "provider_content_id")
    var providerContentId: String = "",

    /** TMDB ID */
    @ColumnInfo(name = "tmdb_id")
    var tmdbId: Long = 0,

    /** IMDb ID */
    @ColumnInfo(name = "imdb_id")
    var imdbId: String = "",

    /** TVDB ID */
    @ColumnInfo(name = "tvdb_id")
    var tvdbId: String = "",

    /** Jellyfin item ID */
    @ColumnInfo(name = "jellyfin_item_id")
    var jellyfinItemId: Long = 0,

    /** Start timestamp */
    @ColumnInfo(name = "start_timestamp")
    var startTimestamp: Long = 0,

    /** Stop timestamp */
    @ColumnInfo(name = "stop_timestamp")
    var stopTimestamp: Long = 0,

    /** Duration watched (milliseconds) */
    @ColumnInfo(name = "duration_watched")
    var durationWatched: Long = 0,

    /** Playback position (milliseconds) */
    @ColumnInfo(name = "playback_position")
    var playbackPosition: Long = 0,

    /** Media runtime (milliseconds) */
    @ColumnInfo(name = "media_runtime")
    var mediaRuntime: Long = 0,

    /** Completion percentage */
    @ColumnInfo(name = "completion_percentage")
    var completionPercentage: Int = 0,

    /** Playback state */
    @ColumnInfo(name = "playback_state")
    var playbackState: String = "",

    /** Watched status */
    @ColumnInfo(name = "watched_status")
    var watchedStatus: Boolean = false,

    /** Rewatch count */
    @ColumnInfo(name = "rewatch_count")
    var rewatchCount: Int = 0,

    /** Candidate title matches */
    @ColumnInfo(name = "candidate_matches")
    var candidateMatches: String = "",

    /** Match confidence (0.0 to 1.0) */
    @ColumnInfo(name = "match_confidence")
    var matchConfidence: Float = 0.0f,

    /** Evidence source */
    @ColumnInfo(name = "evidence_source")
    var evidenceSource: String = "",

    /** Evidence details */
    @ColumnInfo(name = "evidence_details")
    var evidenceDetails: String = "",

    /** Manual correction flag */
    @ColumnInfo(name = "manual_correction")
    var manualCorrection: Boolean = false,

    /** Artwork reference */
    @ColumnInfo(name = "artwork_reference")
    var artworkReference: String = "",

    /** Metadata source */
    @ColumnInfo(name = "metadata_source")
    var metadataSource: String = "",

    /** Sync state */
    @ColumnInfo(name = "sync_state")
    var syncState: String = "",

    /** Created timestamp */
    @ColumnInfo(name = "created_timestamp")
    var createdTimestamp: Long = 0,

    /** Updated timestamp */
    @ColumnInfo(name = "updated_timestamp")
    var updatedTimestamp: Long = 0
) : Parcelable