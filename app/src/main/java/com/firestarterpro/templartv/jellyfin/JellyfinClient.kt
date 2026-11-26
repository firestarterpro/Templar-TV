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

enum class JellyfinKind(val itemType: String) { MOVIE("Movie"), SERIES("Series"), ALBUM("MusicAlbum") }

/** Minimal read-only Jellyfin client: lists the libraries, browses into them, and gives stream URLs. */
object JellyfinClient {

    @Volatile private var cachedUser: Pair<String, String>? = null

    private fun userId(base: String, key: String): String {
        val tag = "$base|$key"
        cachedUser?.let { if (it.first == tag) return it.second }
        val users = JSONArray(get("$base/Users", key))
        val id = (0 until users.length()).map { users.getJSONObject(it) }
            .firstOrNull { it.optJSONObject("Policy")?.optBoolean("IsDisabled", false) != true }
            ?.getString("Id") ?: error("No Jellyfin user found")
        cachedUser = tag to id
        return id
    }

    /** Top level of a tab: every movie, every series or every album. */
    suspend fun list(config: JellyfinConfig, kind: JellyfinKind): Result<List<JellyfinItem>> =
        query(config, "IncludeItemTypes=${kind.itemType}&Recursive=true&SortBy=SortName")

    /** What is inside a series (seasons), a season (episodes) or an album (tracks). */
    suspend fun children(config: JellyfinConfig, parentId: String): Result<List<JellyfinItem>> =
        query(config, "ParentId=$parentId&SortBy=ParentIndexNumber,IndexNumber,SortName")

    private suspend fun query(config: JellyfinConfig, q: String): Result<List<JellyfinItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = config.serverUrl
            val key = config.apiKey
            val uid = userId(base, key)
            val items = JSONObject(get("$base/Users/$uid/Items?$q&Limit=2000&Fields=ProductionYear", key)).getJSONArray("Items")
            (0 until items.length()).map { items.getJSONObject(it) }.map { o -> o.toItem(base) }
        }
    }

    private fun JSONObject.toItem(base: String): JellyfinItem {
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
    fun streamUrl(config: JellyfinConfig, item: JellyfinItem): String =
        if (item.type == "Audio") "${config.serverUrl}/Audio/${item.id}/stream?static=true"
        else "${config.serverUrl}/Videos/${item.id}/stream?static=true"

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
