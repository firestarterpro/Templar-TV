package com.firestarterpro.templartv.observation

import com.firestarterpro.templartv.data.DedupeKey

/**
 * What an app published in its MediaSession metadata, as plain strings. The Android observer copies
 * the fields it can read into this; the parser below is pure Kotlin so it is unit-tested without a
 * device. Every field is optional because providers differ and some publish almost nothing.
 */
data class RawMetadata(
    val title: String? = null,
    val displayTitle: String? = null,
    val displaySubtitle: String? = null,
    val displayDescription: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long = 0,
    val mediaId: String? = null,
    /** Only http(s) links are kept; content:// and bitmaps belong to the other app. */
    val artUri: String? = null,
    val trackNumber: Long = 0,
    val discNumber: Long = 0,
)

/** Why a snapshot was ignored. Logged by name only, never with a title. */
enum class SkipReason { NO_TITLE, APP_NAME, AD_OR_PLACEHOLDER, TOO_SHORT }

sealed interface ParseResult {
    data class Parsed(
        /** The tile title: the show for an episode, the film for a movie. */
        val title: String,
        val seriesTitle: String = "",
        val seasonNumber: Int = 0,
        val episodeNumber: Int = 0,
        val episodeTitle: String = "",
        /** "episode", "movie" or "" when the metadata does not say. */
        val contentType: String = "",
        /** Trust in this reading, 0..1. Below the confirm line it waits in the review queue. */
        val confidence: Float = 1f,
        /** Which fields the reading used. Safe to log: field names only, no titles. */
        val fields: String = "",
        val providerContentId: String = "",
        val artworkRef: String = "",
        val runtimeMs: Long = 0,
    ) : ParseResult

    data class Skipped(val reason: SkipReason) : ParseResult
}

/**
 * Turns provider metadata into a title, series, season and episode, and says how sure it is.
 *
 * Providers disagree about which field holds what, so this reads every text field for an episode
 * marker ("S2:E5", "Season 2, Episode 5", "2x05", "Episode 5") first, since that is the strongest
 * hint. Without a marker it keeps the title as is and lowers confidence if the metadata looks
 * ambiguous, so uncertain readings go to the review queue instead of becoming shelf tiles.
 * Short items (ads, trailers) and the app's own name are ignored.
 */
object MediaMetadataParser {

    /** Anything this short is an ad, a trailer or a clip, not something to put on a shelf. */
    const val MIN_FEATURE_MS = 90_000L

    /** Confidence when an episode reading is clean (marker plus a series name). */
    private const val CLEAN = 1f
    /** Confidence when there is no marker but a second field might be the show name. */
    private const val AMBIGUOUS = 0.7f
    private const val NO_SERIES = 0.85f

    private val placeholders = setOf(
        "advertisement", "ad", "ads", "commercial", "sponsored", "loading", "untitled",
        "null", "undefined", "unknown", "preview", "trailer",
    )
    private val adPattern = Regex("""^(ad|advertisement)\s*(\d+\s*(of|/)\s*\d+)?$""", RegexOption.IGNORE_CASE)

    private class Marker(val range: IntRange, val season: Int, val episode: Int)

    private val markers: List<Regex> = listOf(
        // S2:E5, S02E05, S2 E5, S2.E5, s2 - e5
        Regex("""\bS(\d{1,2})\s*[:.\-]?\s*E(\d{1,3})\b""", RegexOption.IGNORE_CASE),
        // Season 2, Episode 5 / Season 2 Ep. 5 / Season 2 - Episode 5
        Regex("""\bSeason\s+(\d{1,2})\s*[,:.\-]*\s*(?:Episode|Ep\.?)\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE),
        // 2x05
        Regex("""\b(\d{1,2})x(\d{2,3})\b""", RegexOption.IGNORE_CASE),
    )
    private val episodeOnly = Regex("""\b(?:Episode|Ep\.?)\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val seasonOnly = Regex("""\bSeason\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    private val separators = " -–—:|,·•.\t"

    fun parse(raw: RawMetadata, providerName: String): ParseResult {
        // Some apps put their own name in one title field (Prime Video publishes "PrimeVideo") and
        // the real title in the other, so take the first one that is not the app's name.
        val candidates = listOf(clean(raw.title), clean(raw.displayTitle)).filter { it.isNotBlank() }
        if (candidates.isEmpty()) return ParseResult.Skipped(SkipReason.NO_TITLE)
        val title = candidates.firstOrNull { !isAppName(it, providerName) }
            ?: return ParseResult.Skipped(SkipReason.APP_NAME)
        if (isPlaceholder(title)) return ParseResult.Skipped(SkipReason.AD_OR_PLACEHOLDER)
        if (raw.durationMs in 1 until MIN_FEATURE_MS) return ParseResult.Skipped(SkipReason.TOO_SHORT)

        val art = raw.artUri.orEmpty().takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()
        val id = clean(raw.mediaId)

        // Text fields in the order markers are most likely to appear.
        val fields = listOf(
            "title" to title,
            "display_title" to clean(raw.displayTitle),
            "display_subtitle" to clean(raw.displaySubtitle),
            "album" to clean(raw.album),
            "artist" to clean(raw.artist),
            "display_description" to clean(raw.displayDescription),
        ).filter { it.second.isNotBlank() }

        for ((name, text) in fields) {
            val m = findMarker(text) ?: continue
            return fromMarker(name, text, m, title, raw, providerName, art, id)
        }

        // No marker. A distinct album field is the most likely show name; artist is usually a cast
        // or network credit, so it is ignored here.
        val album = clean(raw.album)
        val series = album.takeIf { it.isNotBlank() && !isAppName(it, providerName) && !same(it, title) }
        if (series != null) {
            return ParseResult.Parsed(
                title = series, seriesTitle = series, episodeTitle = title,
                seasonNumber = raw.discNumber.toIntOrZero(), episodeNumber = raw.trackNumber.toIntOrZero(),
                contentType = "episode", confidence = AMBIGUOUS, fields = "title+album",
                providerContentId = id, artworkRef = art, runtimeMs = raw.durationMs,
            )
        }
        return ParseResult.Parsed(
            title = title, contentType = "", confidence = NO_SERIES, fields = "title",
            providerContentId = id, artworkRef = art, runtimeMs = raw.durationMs,
        )
    }

    private fun fromMarker(
        fieldName: String, text: String, m: Marker, title: String, raw: RawMetadata,
        providerName: String, art: String, id: String,
    ): ParseResult {
        val pre = text.substring(0, m.range.first).trim(*separators.toCharArray())
        val post = text.substring(m.range.last + 1).trim(*separators.toCharArray())
        val inTitle = fieldName == "title" || fieldName == "display_title"

        var series = ""
        var episodeTitle = post
        if (pre.isNotBlank() && !isAppName(pre, providerName)) series = pre
        if (series.isBlank()) {
            series = when {
                // The marker is in a subtitle, so the title field is the show.
                !inTitle -> title
                // The marker is in the title with nothing before it ("S1:E2 Name"): look elsewhere.
                else -> listOf(raw.album, raw.artist, raw.albumArtist).map { clean(it) }
                    .firstOrNull { it.isNotBlank() && !isAppName(it, providerName) && !same(it, episodeTitle) }.orEmpty()
            }
        }
        if (episodeTitle.isBlank() && !inTitle && !same(title, series)) episodeTitle = title
        if (series.isBlank()) {
            // Only a marker and an episode name: keep what we have and ask for a review.
            return ParseResult.Parsed(
                title = episodeTitle.ifBlank { title }, episodeTitle = episodeTitle,
                seasonNumber = m.season, episodeNumber = m.episode, contentType = "episode",
                confidence = AMBIGUOUS, fields = "$fieldName:marker", providerContentId = id,
                artworkRef = art, runtimeMs = raw.durationMs,
            )
        }
        return ParseResult.Parsed(
            title = series, seriesTitle = series, episodeTitle = episodeTitle,
            seasonNumber = m.season, episodeNumber = m.episode, contentType = "episode",
            confidence = CLEAN, fields = "$fieldName:marker", providerContentId = id,
            artworkRef = art, runtimeMs = raw.durationMs,
        )
    }

    private fun findMarker(text: String): Marker? {
        for (re in markers) {
            val match = re.find(text) ?: continue
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            // 1920x1080-style resolutions are not seasons.
            if (re === markers[2] && (season > 40 || episode > 200)) continue
            return Marker(match.range, season, episode)
        }
        val ep = episodeOnly.find(text) ?: return null
        val season = seasonOnly.find(text)?.groupValues?.get(1)?.toInt() ?: 0
        val range = if (season > 0) {
            val s = seasonOnly.find(text)!!.range
            minOf(s.first, ep.range.first)..maxOf(s.last, ep.range.last)
        } else ep.range
        return Marker(range, season, ep.groupValues[1].toInt())
    }

    private fun clean(s: String?): String = s.orEmpty().replace(Regex("\\s+"), " ").trim()

    private fun same(a: String, b: String) = DedupeKey.normalize(a) == DedupeKey.normalize(b)

    private fun isAppName(text: String, providerName: String): Boolean {
        // Spaces are ignored: "Prime Video", "PrimeVideo" and "primevideo" are the same name.
        val n = DedupeKey.normalize(text).replace(" ", "")
        val p = DedupeKey.normalize(providerName).replace(" ", "")
        return n.isEmpty() || n == p || n == p + "tv" || n == p + "app"
    }

    private fun isPlaceholder(text: String): Boolean =
        DedupeKey.normalize(text) in placeholders || adPattern.matches(text.trim())

    private fun Long.toIntOrZero(): Int = if (this in 1..200) toInt() else 0
}
