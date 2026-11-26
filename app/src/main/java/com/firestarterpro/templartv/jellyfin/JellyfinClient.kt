package com.firestarterpro.templartv.jellyfin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Where the Jellyfin server is. Stored on this device only (never in git). */
class JellyfinConfig(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("templar_jellyfin", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("url", DEFAULT_URL).orEmpty().trim().trimEnd('/')
        set(v) { prefs.edit().putString("url", v.trim().trimEnd('/')).apply() }

    var apiKey: String
        get() = prefs.getString("key", "").orEmpty().trim()
        set(v) { prefs.edit().putString("key", v.trim()).apply() }

    /** The Jellyfin user Templar browses as, once chosen. Empty until the first load. */
    var userId: String
        get() = prefs.getString("user", "").orEmpty()
        set(v) { prefs.edit().putString("user", v).apply() }

    val isConfigured: Boolean get() = serverUrl.isNotEmpty() && apiKey.isNotEmpty()

    companion object {
        const val DEFAULT_URL = "http://192.168.0.150:8096"
    }
}

data class JellyfinItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    /** Jellyfin type: Movie, Series, Season, Episode, MusicAlbum, Audio, ... */
    val type: String = "",
    /** True for things that hold other things (series, seasons, albums). */
    val isFolder: Boolean = false,
)

/** [collectionType] is the Jellyfin library type that holds this kind of item. */
enum class JellyfinKind(val itemType: String, val collectionType: String) {
    MOVIE("Movie", "movies"), SERIES("Series", "tvshows"), ALBUM("MusicAlbum", "music"),
}

data class JellyfinUser(val id: String, val isAdmin: Boolean, val disabled: Boolean)
data class JellyfinLibrary(val id: String, val name: String, val collectionType: String)

/** Minimal read-only Jellyfin client: lists the libraries, browses into them, and gives stream URLs. */
object JellyfinClient {

    /**
     * Which user to browse as: the saved one if it still exists and is enabled, else the first
     * administrator, else the first enabled user.
     */
    fun pickUser(users: List<JellyfinUser>, saved: String): JellyfinUser? {
        val enabled = users.filter { !it.disabled }
        return enabled.firstOrNull { it.id == saved && saved.isNotEmpty() }
            ?: enabled.firstOrNull { it.isAdmin }
            ?: enabled.firstOrNull()
    }

    /** Libraries for a tab. Preview libraries (name contains "preview") are left out so items aren't listed twice. */
    fun pickLibraries(libraries: List<JellyfinLibrary>, kind: JellyfinKind): List<JellyfinLibrary> =
        libraries.filter { it.collectionType.equals(kind.collectionType, ignoreCase = true) && !it.name.contains("preview", ignoreCase = true) }

    /** Drops repeats of the same title (and artist or year), keeping the first. */
    fun dedupe(items: List<JellyfinItem>): List<JellyfinItem> =
        items.distinctBy { it.type + "|" + it.title.trim().lowercase() + "|" + it.subtitle.trim().lowercase() }

    private fun userId(config: JellyfinConfig): String {
        val base = config.serverUrl
        val key = config.apiKey
        val arr = JSONArray(get("$base/Users", key))
        val users = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            val policy = it.optJSONObject("Policy")
            JellyfinUser(it.getString("Id"), policy?.optBoolean("IsAdministrator", false) == true, policy?.optBoolean("IsDisabled", false) == true)
        }
        val user = pickUser(users, config.userId) ?: error("No Jellyfin user found")
        if (user.id != config.userId) config.userId = user.id
        return user.id
    }

    /** Top level of a tab: every movie, every series or every album, from the real (non-preview) libraries. */
    suspend fun list(config: JellyfinConfig, kind: JellyfinKind): Result<List<JellyfinItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = config.serverUrl
            val key = config.apiKey
            val uid = userId(config)
            val views = JSONObject(get("$base/Users/$uid/Views", key)).getJSONArray("Items")
            val libs = pickLibraries(
                (0 until views.length()).map { views.getJSONObject(it) }.map {
                    JellyfinLibrary(it.getString("Id"), it.optString("Name", ""), it.optString("CollectionType", ""))
                },
                kind,
            )
            val q = "IncludeItemTypes=${kind.itemType}&Recursive=true&SortBy=SortName"
            val items = if (libs.isEmpty()) fetch(config, uid, q)
            else libs.flatMap { fetch(config, uid, "$q&ParentId=${it.id}") }.sortedBy { it.title.lowercase() }
            dedupe(items)
        }
    }

    /** What is inside a series (seasons), a season (episodes) or an album (tracks). */
    suspend fun children(config: JellyfinConfig, parentId: String): Result<List<JellyfinItem>> = withContext(Dispatchers.IO) {
        runCatching { fetch(config, userId(config), "ParentId=$parentId&SortBy=ParentIndexNumber,IndexNumber,SortName") }
    }

    private fun fetch(config: JellyfinConfig, uid: String, q: String): List<JellyfinItem> {
        val base = config.serverUrl
        val items = JSONObject(get("$base/Users/$uid/Items?$q&Limit=2000&Fields=ProductionYear", config.apiKey)).getJSONArray("Items")
        return (0 until items.length()).map { items.getJSONObject(it) }.map { o -> o.toItem(base) }
    }

    internal fun JSONObject.toItem(base: String): JellyfinItem {
        val id = getString("Id")
        val type = optString("Type", "")
        val index = optInt("IndexNumber", 0)
        val sub = when (type) {
            "Movie", "Series" -> optInt("ProductionYear", 0).takeIf { it > 0 }?.toString().orEmpty()
            "MusicAlbum" -> optString("AlbumArtist", "")
            "Episode" -> if (index > 0) "Episode $index" else ""
            "Audio" -> optJSONArray("Artists")?.optString(0).orEmpty()
            else -> ""
        }
        val title = optString("Name", id).let { if (type == "Audio" && index > 0) "$index. $it" else it }
        return JellyfinItem(
            id = id,
            title = title,
            subtitle = sub,
            imageUrl = "$base/Items/$id/Images/Primary?maxWidth=420",
            type = type,
            isFolder = optBoolean("IsFolder", false),
        )
    }

    /** Direct stream of the original file. Needs [authHeader] on the request. */
    fun streamUrl(config: JellyfinConfig, item: JellyfinItem): String = streamUrl(config.serverUrl, item)

    fun streamUrl(base: String, item: JellyfinItem): String =
        if (item.type == "Audio") "$base/Audio/${item.id}/stream?static=true"
        else "$base/Videos/${item.id}/stream?static=true"

    /** Newer Jellyfin (10.11+) can refuse the legacy X-Emby-Token / api_key forms; this one always works. */
    fun authHeader(key: String): String =
        "MediaBrowser Client=\"Templar TV\", Device=\"Google TV Streamer\", DeviceId=\"templar-tv\", Version=\"0.1.0\", Token=\"$key\""

    private fun get(url: String, key: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6_000
            c.readTimeout = 15_000
            c.setRequestProperty("Authorization", authHeader(key))
            c.setRequestProperty("X-Emby-Token", key)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code == 401 || code == 403) error("Jellyfin rejected the key (HTTP $code)")
            if (code !in 200..299) error("Jellyfin answered HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
