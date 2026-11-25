package com.firestarterpro.templartv.companion

import com.firestarterpro.templartv.data.ProfileIds
import com.firestarterpro.templartv.data.sync.SyncChanges
import com.firestarterpro.templartv.data.sync.SyncFavourite
import com.firestarterpro.templartv.data.sync.SyncRow
import com.firestarterpro.templartv.data.sync.SyncShelfEntry
import com.firestarterpro.templartv.data.sync.SyncStore
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLDecoder

data class CompanionRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    /** Header names lower-cased. */
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
)

data class CompanionResponse(val status: Int, val body: String)

data class CompanionApp(val packageName: String, val label: String, val favourite: Boolean, val isLeanback: Boolean)

data class CompanionHistoryItem(
    val id: Long, val profileId: String, val provider: String, val title: String, val seriesTitle: String,
    val seasonNumber: Int, val episodeNumber: Int, val positionMs: Long, val runtimeMs: Long,
    val completion: Int, val watched: Boolean, val confidence: Float, val evidence: String,
    val startedAt: Long, val stoppedAt: Long,
)

/** What the router needs from Android. Tests pass a fake. */
interface CompanionHost {
    val appVersion: String
    fun historyEnabled(): Boolean
    suspend fun apps(): List<CompanionApp>
    suspend fun setFavouriteApp(packageName: String, favourite: Boolean): Boolean
    /** Returns null when the package is not launchable, else whether Android may block the start. */
    fun launch(packageName: String): Boolean?
    /** Returns whether Android may block the start. */
    fun openQuickMenu(): Boolean
    suspend fun history(profileId: String, limit: Int): List<CompanionHistoryItem>
}

/**
 * Pure request → response mapping for the companion API (see docs/companion-api.md).
 * No sockets here, so it is unit-tested directly.
 */
class CompanionRouter(
    private val pairing: CompanionPairing,
    private val store: SyncStore,
    private val host: CompanionHost,
) {
    suspend fun handle(req: CompanionRequest): CompanionResponse = try {
        route(req)
    } catch (e: BadRequest) {
        error(400, "bad_request", e.message ?: "Bad request")
    } catch (e: JSONException) {
        error(400, "bad_request", "Body is not valid JSON for this call")
    } catch (e: Exception) {
        error(500, "internal", "Templar TV could not complete the call")
    }

    private class BadRequest(message: String) : Exception(message)

    private suspend fun route(req: CompanionRequest): CompanionResponse {
        if (!req.path.startsWith(BASE)) return error(404, "not_found", "Unknown path")
        val segs = req.path.removePrefix(BASE).trim('/').split('/').filter { it.isNotEmpty() }
            .map { URLDecoder.decode(it, "UTF-8") }
        val m = req.method.uppercase()
        val authorized = pairing.isAuthorized(req.headers["authorization"])

        // Unauthenticated calls.
        when {
            segs == listOf("status") && m == "GET" -> return ok(JSONObject()
                .put("app", "Templar TV").put("apiVersion", API_VERSION).put("appVersion", host.appVersion)
                .put("authorized", authorized).put("pairingOpen", pairing.currentCode() != null)
                .put("historyEnabled", host.historyEnabled()))
            segs == listOf("pair") -> {
                if (m != "POST") return methodNotAllowed()
                val b = body(req)
                return when (val r = pairing.pair(b.optString("code"), b.optString("deviceName"))) {
                    is PairResult.Paired -> ok(JSONObject().put("token", r.token).put("deviceName", r.deviceName))
                    PairResult.NoCode -> error(410, "no_pairing_code", "Show a pairing code on the TV first (Settings → Features)")
                    PairResult.WrongCode -> error(401, "unauthorized", "Wrong pairing code")
                    PairResult.Locked -> error(429, "too_many_attempts", "Too many wrong codes. Wait a minute and show a new code")
                }
            }
        }
        if (!authorized) return error(401, "unauthorized", "Pair first and send Authorization: Bearer <token>")

        val profile = req.query["profile"]?.takeIf { it.isNotBlank() } ?: ProfileIds.DEFAULT
        val since = req.query["since"]?.toLongOrNull() ?: 0L

        return when (segs.firstOrNull()) {
            "profiles" -> if (m == "GET") ok(items(JSONArray().apply {
                store.profiles().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("archived", it.archived)) }
            })) else methodNotAllowed()

            "apps" -> when {
                segs.size == 1 && m == "GET" -> ok(items(JSONArray().apply {
                    host.apps().forEach {
                        put(JSONObject().put("packageName", it.packageName).put("label", it.label)
                            .put("favourite", it.favourite).put("isLeanback", it.isLeanback))
                    }
                }))
                segs.size == 3 && segs[1] == "favourites" && m == "PUT" -> {
                    val fav = body(req).requireBoolean("favourite")
                    if (host.setFavouriteApp(segs[2], fav)) ok(JSONObject().put("packageName", segs[2]).put("favourite", fav))
                    else error(404, "not_found", "No such app")
                }
                else -> notFoundOrMethod(segs.size in 1..3)
            }

            "launch" -> if (m == "POST") {
                val pkg = body(req).requireString("packageName")
                when (val blocked = host.launch(pkg)) {
                    null -> error(404, "not_found", "No such launchable app")
                    else -> ok(JSONObject().put("ok", true).put("mayBeBlocked", blocked))
                }
            } else methodNotAllowed()

            "quick-menu" -> if (m == "POST") ok(JSONObject().put("ok", true).put("mayBeBlocked", host.openQuickMenu()))
            else methodNotAllowed()

            "history" -> when {
                m != "GET" -> methodNotAllowed()
                !host.historyEnabled() -> error(403, "history_disabled", "Watch history sharing is off on the TV")
                else -> {
                    val limit = (req.query["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
                    ok(items(JSONArray().apply { host.history(profile, limit).forEach { put(it.toJson()) } }))
                }
            }

            "favourites" -> collection(req, segs, m,
                list = { store.favouritesSince(profile, since).toJson { it.toJson() } },
                upsert = { id, b -> store.upsertFavourite(b.toFavourite(id, profile))?.toJson() },
                reorder = { b -> store.reorderFavourites(profile, b.requireIds()) },
                delete = { store.softDeleteFavourite(it) },
                restore = { store.restoreFavourite(it) },
            )

            "shelf-entries" -> collection(req, segs, m,
                list = { store.shelfEntriesSince(profile, since).toJson { it.toJson() } },
                upsert = { id, b -> store.upsertShelfEntry(b.toShelfEntry(id, profile))?.toJson() },
                reorder = { b -> store.reorderShelf(profile, b.requireString("provider"), b.requireIds()) },
                delete = { store.softDeleteShelfEntry(it) },
                restore = { store.restoreShelfEntry(it) },
            )

            "rows" -> collection(req, segs, m,
                list = { store.rowsSince(profile, since).toJson { it.toJson() } },
                upsert = { id, b -> upsertRow(profile, id, b) },
                reorder = { b -> store.reorderRows(profile, b.requireIds()) },
                delete = null,
                restore = null,
            )

            "changes" -> if (m == "GET") {
                val f = store.favouritesSince(profile, since)
                val s = store.shelfEntriesSince(profile, since)
                val r = store.rowsSince(profile, since)
                ok(JSONObject()
                    .put("favourites", JSONArray().apply { f.items.forEach { put(it.toJson()) } })
                    .put("shelfEntries", JSONArray().apply { s.items.forEach { put(it.toJson()) } })
                    .put("rows", JSONArray().apply { r.items.forEach { put(it.toJson()) } })
                    .put("asOf", minOf(f.asOf, s.asOf, r.asOf)))
            } else methodNotAllowed()

            else -> error(404, "not_found", "Unknown path")
        }
    }

    /** Rows keep their kind and provider; a client may change only visible, titleOverride and tileSize. */
    private suspend fun upsertRow(profile: String, rowId: String, b: JSONObject): JSONObject? {
        val current = store.rowsSince(profile, 0).items.firstOrNull { it.rowId == rowId } ?: return null
        val tileSize = b.optString("tileSize", current.tileSize).uppercase()
        if (tileSize !in TILE_SIZES) throw BadRequest("tileSize must be SMALL, MEDIUM or LARGE")
        return store.upsertRow(current.copy(
            visible = if (b.has("visible")) b.getBoolean("visible") else current.visible,
            titleOverride = b.optString("titleOverride", current.titleOverride),
            tileSize = tileSize,
        ))?.toJson()
    }

    private suspend fun collection(
        req: CompanionRequest, segs: List<String>, m: String,
        list: suspend () -> JSONObject,
        upsert: suspend (String, JSONObject) -> JSONObject?,
        reorder: suspend (JSONObject) -> Boolean,
        delete: (suspend (String) -> Boolean)?,
        restore: (suspend (String) -> Boolean)?,
    ): CompanionResponse = when {
        segs.size == 1 -> if (m == "GET") ok(list()) else methodNotAllowed()
        segs.size == 2 && segs[1] == "reorder" -> if (m == "POST") okOrRefused(reorder(body(req))) else methodNotAllowed()
        segs.size == 2 -> when (m) {
            "PUT" -> upsert(segs[1], body(req))?.let(::ok) ?: refused()
            "DELETE" -> delete?.let { okOrRefused(it(segs[1])) } ?: methodNotAllowed()
            else -> methodNotAllowed()
        }
        segs.size == 3 && segs[2] == "restore" && restore != null ->
            if (m == "POST") okOrRefused(restore(segs[1])) else methodNotAllowed()
        else -> error(404, "not_found", "Unknown path")
    }

    // ---- JSON mapping ----

    private fun body(req: CompanionRequest): JSONObject =
        if (req.body.isBlank()) JSONObject() else JSONObject(req.body)

    private fun JSONObject.requireString(key: String): String =
        optString(key).trim().takeIf { it.isNotEmpty() } ?: throw BadRequest("$key is required")

    private fun JSONObject.requireBoolean(key: String): Boolean =
        if (has(key) && opt(key) is Boolean) getBoolean(key) else throw BadRequest("$key must be true or false")

    private fun JSONObject.requireIds(): List<String> {
        val a = optJSONArray("ids") ?: throw BadRequest("ids is required")
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun JSONObject.toFavourite(syncId: String, profile: String) = SyncFavourite(
        syncId = syncId, profileId = profile, title = optString("title").trim(),
        tmdbId = optLong("tmdbId"), imdbId = optString("imdbId"), tvdbId = optString("tvdbId"),
        preferredProvider = optString("preferredProvider"), artworkRef = optString("artworkRef"),
    )

    private fun JSONObject.toShelfEntry(syncId: String, profile: String) = SyncShelfEntry(
        syncId = syncId, profileId = profile, provider = requireString("provider"), title = optString("title").trim(),
        seriesTitle = optString("seriesTitle"), contentType = optString("contentType"),
        externalId = optString("externalId"), tmdbId = optLong("tmdbId"), imdbId = optString("imdbId"),
        deepLink = optString("deepLink"), artworkRef = optString("artworkRef"),
        // A client pin is always the user's own statement.
        source = "MANUAL",
        pinned = optBoolean("pinned"), hidden = optBoolean("hidden"), watched = optBoolean("watched"),
    )

    private fun <T> SyncChanges<T>.toJson(map: (T) -> JSONObject) =
        JSONObject().put("items", JSONArray().apply { items.forEach { put(map(it)) } }).put("asOf", asOf)

    private fun SyncFavourite.toJson() = JSONObject()
        .put("syncId", syncId).put("profileId", profileId).put("title", title).put("tmdbId", tmdbId)
        .put("imdbId", imdbId).put("tvdbId", tvdbId).put("preferredProvider", preferredProvider)
        .put("artworkRef", artworkRef).put("position", position).put("updatedAt", updatedAt)
        .put("removedAt", removedAt ?: JSONObject.NULL)

    private fun SyncShelfEntry.toJson() = JSONObject()
        .put("syncId", syncId).put("profileId", profileId).put("provider", provider).put("title", title)
        .put("seriesTitle", seriesTitle).put("contentType", contentType).put("externalId", externalId)
        .put("tmdbId", tmdbId).put("imdbId", imdbId).put("deepLink", deepLink).put("artworkRef", artworkRef)
        .put("source", source).put("pinned", pinned).put("hidden", hidden).put("watched", watched)
        .put("position", position).put("updatedAt", updatedAt).put("removedAt", removedAt ?: JSONObject.NULL)

    private fun SyncRow.toJson() = JSONObject()
        .put("profileId", profileId).put("rowId", rowId).put("kind", kind).put("provider", provider)
        .put("titleOverride", titleOverride).put("visible", visible).put("tileSize", tileSize)
        .put("position", position).put("updatedAt", updatedAt)

    private fun CompanionHistoryItem.toJson() = JSONObject()
        .put("id", id).put("profileId", profileId).put("provider", provider).put("title", title)
        .put("seriesTitle", seriesTitle).put("seasonNumber", seasonNumber).put("episodeNumber", episodeNumber)
        .put("positionMs", positionMs).put("runtimeMs", runtimeMs).put("completion", completion)
        .put("watched", watched).put("confidence", confidence.toDouble()).put("evidence", evidence)
        .put("startedAt", startedAt).put("stoppedAt", stoppedAt)

    private fun items(a: JSONArray) = JSONObject().put("items", a)
    private fun ok(o: JSONObject) = CompanionResponse(200, o.toString())
    private fun okOrRefused(done: Boolean) = if (done) ok(JSONObject().put("ok", true)) else refused()
    private fun refused() = error(422, "refused", "The TV refused the change (feature off, unknown id or blank title)")
    private fun methodNotAllowed() = error(405, "method_not_allowed", "Method not allowed here")
    private fun notFoundOrMethod(knownShape: Boolean) = if (knownShape) methodNotAllowed() else error(404, "not_found", "Unknown path")

    companion object {
        const val BASE = "/api/companion/v1"
        const val API_VERSION = 1
        private val TILE_SIZES = setOf("SMALL", "MEDIUM", "LARGE")

        fun error(status: Int, code: String, message: String) =
            CompanionResponse(status, JSONObject().put("error", code).put("message", message).toString())
    }
}
