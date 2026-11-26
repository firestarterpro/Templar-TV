package com.firestarterpro.templartv.jellyfin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

data class JellyfinItem(val id: String, val title: String, val subtitle: String, val imageUrl: String)

enum class JellyfinKind(val itemType: String) { MOVIE("Movie"), ALBUM("MusicAlbum") }

/** Minimal read-only Jellyfin client: lists movies or albums with their poster URLs. */
object JellyfinClient {

    suspend fun list(config: JellyfinConfig, kind: JellyfinKind): Result<List<JellyfinItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = config.serverUrl
            val key = config.apiKey
            val users = JSONArray(get("$base/Users", key))
            val userId = (0 until users.length()).map { users.getJSONObject(it) }
                .firstOrNull { it.optJSONObject("Policy")?.optBoolean("IsDisabled", false) != true }
                ?.getString("Id") ?: error("No Jellyfin user found")
            val q = "IncludeItemTypes=${kind.itemType}&Recursive=true&SortBy=SortName&Limit=1000&Fields=ProductionYear"
            val items = JSONObject(get("$base/Users/$userId/Items?$q", key)).getJSONArray("Items")
            (0 until items.length()).map { items.getJSONObject(it) }.map { o ->
                val id = o.getString("Id")
                val sub = when (kind) {
                    JellyfinKind.MOVIE -> o.optInt("ProductionYear", 0).takeIf { it > 0 }?.toString().orEmpty()
                    JellyfinKind.ALBUM -> o.optString("AlbumArtist", "")
                }
                JellyfinItem(
                    id = id,
                    title = o.optString("Name", id),
                    subtitle = sub,
                    imageUrl = "$base/Items/$id/Images/Primary?maxWidth=420&api_key=${URLEncoder.encode(key, "UTF-8")}",
                )
            }
        }
    }

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
