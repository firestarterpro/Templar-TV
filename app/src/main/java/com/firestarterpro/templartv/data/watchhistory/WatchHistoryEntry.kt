package com.firestarterpro.templartv.data.watchhistory

import android.os.Parcelable
import android.os.Parcel
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

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
) : Parcelable {
    override fun describeContents(): Int = 0
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(profileId)
        parcel.writeString(provider)
        parcel.writeString(providerAlias)
        parcel.writeString(packageName)
        parcel.writeString(contentType)
        parcel.writeString(canonicalTitle)
        parcel.writeString(originalTitle)
        parcel.writeString(seriesTitle)
        parcel.writeInt(seasonNumber)
        parcel.writeInt(episodeNumber)
        parcel.writeString(episodeTitle)
        parcel.writeString(providerContentId)
        parcel.writeLong(tmdbId)
        parcel.writeString(imdbId)
        parcel.writeString(tvdbId)
        parcel.writeLong(jellyfinItemId)
        parcel.writeLong(startTimestamp)
        parcel.writeLong(stopTimestamp)
        parcel.writeLong(durationWatched)
        parcel.writeLong(playbackPosition)
        parcel.writeLong(mediaRuntime)
        parcel.writeInt(completionPercentage)
        parcel.writeString(playbackState)
        parcel.writeInt(if (watchedStatus) 1 else 0)
        parcel.writeInt(rewatchCount)
        parcel.writeString(candidateMatches)
        parcel.writeFloat(matchConfidence)
        parcel.writeString(evidenceSource)
        parcel.writeString(evidenceDetails)
        parcel.writeInt(if (manualCorrection) 1 else 0)
        parcel.writeString(artworkReference)
        parcel.writeString(metadataSource)
        parcel.writeString(syncState)
        parcel.writeLong(createdTimestamp)
        parcel.writeLong(updatedTimestamp)
    }

    companion object {
        @JvmStatic
        fun createFromParcel(parcel: Parcel): WatchHistoryEntry {
            return WatchHistoryEntry(
                id = parcel.readInt(),
                profileId = parcel.readString() ?: "",
                provider = parcel.readString() ?: "",
                providerAlias = parcel.readString() ?: "",
                packageName = parcel.readString() ?: "",
                contentType = parcel.readString() ?: "",
                canonicalTitle = parcel.readString() ?: "",
                originalTitle = parcel.readString() ?: "",
                seriesTitle = parcel.readString() ?: "",
                seasonNumber = parcel.readInt(),
                episodeNumber = parcel.readInt(),
                episodeTitle = parcel.readString() ?: "",
                providerContentId = parcel.readString() ?: "",
                tmdbId = parcel.readLong(),
                imdbId = parcel.readString() ?: "",
                tvdbId = parcel.readString() ?: "",
                jellyfinItemId = parcel.readLong(),
                startTimestamp = parcel.readLong(),
                stopTimestamp = parcel.readLong(),
                durationWatched = parcel.readLong(),
                playbackPosition = parcel.readLong(),
                mediaRuntime = parcel.readLong(),
                completionPercentage = parcel.readInt(),
                playbackState = parcel.readString() ?: "",
                watchedStatus = parcel.readInt() == 1,
                rewatchCount = parcel.readInt(),
                candidateMatches = parcel.readString() ?: "",
                matchConfidence = parcel.readFloat(),
                evidenceSource = parcel.readString() ?: "",
                evidenceDetails = parcel.readString() ?: "",
                manualCorrection = parcel.readInt() == 1,
                artworkReference = parcel.readString() ?: "",
                metadataSource = parcel.readString() ?: "",
                syncState = parcel.readString() ?: "",
                createdTimestamp = parcel.readLong(),
                updatedTimestamp = parcel.readLong()
            )
        }

        @JvmField
        val CREATOR: android.os.Parcelable.Creator<WatchHistoryEntry> =
            object : android.os.Parcelable.Creator<WatchHistoryEntry> {
                override fun createFromParcel(parcel: android.os.Parcel): WatchHistoryEntry =
                    WatchHistoryEntry.createFromParcel(parcel)

                override fun newArray(size: Int): Array<WatchHistoryEntry?> = arrayOfNulls(size)
            }
    }
}
