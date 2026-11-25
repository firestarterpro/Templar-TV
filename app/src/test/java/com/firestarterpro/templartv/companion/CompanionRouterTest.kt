package com.firestarterpro.templartv.companion

import com.firestarterpro.templartv.data.sync.SyncChanges
import com.firestarterpro.templartv.data.sync.SyncFavourite
import com.firestarterpro.templartv.data.sync.SyncProfile
import com.firestarterpro.templartv.data.sync.SyncRow
import com.firestarterpro.templartv.data.sync.SyncShelfEntry
import com.firestarterpro.templartv.data.sync.SyncStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/** In-memory SyncStore following the SyncStore rules closely enough for routing tests. */
private class FakeSyncStore(private val clock: () -> Long) : SyncStore {
    val favs = linkedMapOf<String, SyncFavourite>()
    val shelf = linkedMapOf<String, SyncShelfEntry>()
    val rows = linkedMapOf(
        "continue_watching" to SyncRow("default", "continue_watching", "CONTINUE_WATCHING", position = 0),
        "shelf:peacock" to SyncRow("default", "shelf:peacock", "PROVIDER_SHELF", provider = "peacock", position = 1),
    )

    override suspend fun profiles() = listOf(SyncProfile("default", "Default", false))

    override suspend fun favouritesSince(profileId: String, since: Long) =
        SyncChanges(favs.values.filter { it.profileId == profileId && it.updatedAt >= since }.sortedBy { it.updatedAt }, clock())

    override suspend fun upsertFavourite(favourite: SyncFavourite): SyncFavourite? {
        if (favourite.title.isBlank()) return null
        val pos = favs[favourite.syncId]?.position ?: favs.size
        return favourite.copy(position = pos, updatedAt = clock(), removedAt = null).also { favs[it.syncId] = it }
    }

    override suspend fun reorderFavourites(profileId: String, orderedSyncIds: List<String>): Boolean {
        if (orderedSyncIds.any { it !in favs }) return false
        val rest = favs.values.sortedBy { it.position }.map { it.syncId }.filter { it !in orderedSyncIds }
        (orderedSyncIds + rest).forEachIndexed { i, id -> favs[id] = favs.getValue(id).copy(position = i, updatedAt = clock()) }
        return true
    }

    override suspend fun softDeleteFavourite(syncId: String): Boolean {
        val f = favs[syncId] ?: return false
        favs[syncId] = f.copy(removedAt = clock(), updatedAt = clock()); return true
    }

    override suspend fun restoreFavourite(syncId: String): Boolean {
        val f = favs[syncId] ?: return false
        favs[syncId] = f.copy(removedAt = null, updatedAt = clock()); return true
    }

    override suspend fun shelfEntriesSince(profileId: String, since: Long) =
        SyncChanges(shelf.values.filter { it.updatedAt >= since }.sortedBy { it.updatedAt }, clock())

    override suspend fun upsertShelfEntry(entry: SyncShelfEntry): SyncShelfEntry? {
        if (entry.title.isBlank()) return null
        // Same show already on that provider's shelf: return the stored one.
        shelf.values.firstOrNull { it.provider == entry.provider && it.externalId.isNotEmpty() && it.externalId == entry.externalId && it.syncId != entry.syncId }
            ?.let { return it }
        return entry.copy(updatedAt = clock()).also { shelf[it.syncId] = it }
    }

    override suspend fun reorderShelf(profileId: String, provider: String, orderedSyncIds: List<String>) = orderedSyncIds.all { it in shelf }
    override suspend fun softDeleteShelfEntry(syncId: String) = shelf.containsKey(syncId)
    override suspend fun restoreShelfEntry(syncId: String) = shelf.containsKey(syncId)

    override suspend fun rowsSince(profileId: String, since: Long) =
        SyncChanges(rows.values.filter { it.updatedAt >= since }.sortedBy { it.updatedAt }, clock())

    override suspend fun upsertRow(row: SyncRow): SyncRow? {
        if (row.rowId !in rows) return null
        return row.copy(updatedAt = clock()).also { rows[it.rowId] = it }
    }

    override suspend fun reorderRows(profileId: String, orderedRowIds: List<String>) = orderedRowIds.all { it in rows }
}

private class FakeHost : CompanionHost {
    var historyOn = false
    val favs = mutableSetOf<String>()
    val launched = mutableListOf<String>()
    var quickMenuOpened = 0
    override val appVersion = "test"
    override fun historyEnabled() = historyOn
    override suspend fun apps() = listOf("com.peacocktv.peacockandroid", "org.jellyfin.androidtv")
        .map { CompanionApp(it, it.substringAfterLast('.'), it in favs, true) }
    override suspend fun setFavouriteApp(packageName: String, favourite: Boolean): Boolean {
        if (apps().none { it.packageName == packageName }) return false
        if (favourite) favs += packageName else favs -= packageName
        return true
    }
    override fun launch(packageName: String): Boolean? =
        if (packageName == "org.jellyfin.androidtv") { launched += packageName; false } else null
    override fun openQuickMenu(): Boolean { quickMenuOpened++; return false }
    override suspend fun history(profileId: String, limit: Int) = listOf(
        CompanionHistoryItem(1, profileId, "peacock", "The Office", "The Office", 2, 3, 1000, 2000, 50, false, 0.9f, "MEDIA_SESSION", 1, 2),
    )
}

class CompanionRouterTest {
    private var now = 1_000L
    private lateinit var pairing: CompanionPairing
    private lateinit var store: FakeSyncStore
    private lateinit var host: FakeHost
    private lateinit var router: CompanionRouter
    private val base = CompanionRouter.BASE

    @Before
    fun setUp() {
        pairing = CompanionPairing(InMemoryPairedDeviceStore(), clock = { now })
        store = FakeSyncStore { now }
        host = FakeHost()
        router = CompanionRouter(pairing, store, host)
    }

    private suspend fun call(method: String, path: String, body: String = "", token: String? = null, query: Map<String, String> = emptyMap()) =
        router.handle(CompanionRequest(method, base + path, query,
            if (token != null) mapOf("authorization" to "Bearer $token") else emptyMap(), body))

    private fun json(r: CompanionResponse) = JSONObject(r.body)

    private suspend fun pairToken(): String {
        val code = pairing.startPairing()
        val r = call("POST", "/pair", """{"code":"$code","deviceName":"Tab 6"}""")
        assertEquals(200, r.status)
        return json(r).getString("token")
    }

    @Test fun statusIsOpenAndReportsAuthorization() = runTest {
        val r = call("GET", "/status")
        assertEquals(200, r.status)
        assertFalse(json(r).getBoolean("authorized"))
        assertEquals(1, json(r).getInt("apiVersion"))
        val t = pairToken()
        assertTrue(json(call("GET", "/status", token = t)).getBoolean("authorized"))
    }

    @Test fun everythingElseNeedsAToken() = runTest {
        assertEquals(401, call("GET", "/apps").status)
        assertEquals(401, call("GET", "/favourites", token = "nope").status)
    }

    @Test fun pairingCodeIsSingleUseAndOnlyHashesAreStored() = runTest {
        val devices = InMemoryPairedDeviceStore()
        pairing = CompanionPairing(devices, clock = { now })
        router = CompanionRouter(pairing, store, host)
        val code = pairing.startPairing()
        val token = json(call("POST", "/pair", """{"code":"$code","deviceName":"Tab"}""")).getString("token")
        assertEquals(64, token.length)
        assertEquals(CompanionPairing.sha256(token), devices.load().single().tokenHash)
        assertTrue(devices.load().none { it.tokenHash == token })
        assertEquals(410, call("POST", "/pair", """{"code":"$code"}""").status)
    }

    @Test fun pairingCodeExpires() = runTest {
        val code = pairing.startPairing()
        now += CompanionPairing.CODE_TTL_MS + 1
        assertEquals(410, call("POST", "/pair", """{"code":"$code"}""").status)
    }

    @Test fun wrongCodesLockPairing() = runTest {
        val code = pairing.startPairing()
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(CompanionPairing.MAX_FAILURES) { assertEquals(401, call("POST", "/pair", """{"code":"$wrong"}""").status) }
        assertEquals(429, call("POST", "/pair", """{"code":"$code"}""").status)
        now += CompanionPairing.LOCK_MS + 1
        // Lock lifted, but the old code was cancelled.
        assertEquals(410, call("POST", "/pair", """{"code":"$code"}""").status)
    }

    @Test fun unpairAllRevokesTokens() = runTest {
        val t = pairToken()
        pairing.unpairAll()
        assertEquals(401, call("GET", "/apps", token = t).status)
    }

    @Test fun appsFavouritesAndLaunch() = runTest {
        val t = pairToken()
        assertEquals(200, call("PUT", "/apps/favourites/org.jellyfin.androidtv", """{"favourite":true}""", t).status)
        val apps = json(call("GET", "/apps", token = t)).getJSONArray("items")
        val jf = (0 until apps.length()).map { apps.getJSONObject(it) }.single { it.getString("packageName") == "org.jellyfin.androidtv" }
        assertTrue(jf.getBoolean("favourite"))
        assertEquals(404, call("PUT", "/apps/favourites/x.y", """{"favourite":true}""", t).status)
        assertEquals(400, call("PUT", "/apps/favourites/org.jellyfin.androidtv", """{"favourite":"yes"}""", t).status)
        assertEquals(200, call("POST", "/launch", """{"packageName":"org.jellyfin.androidtv"}""", t).status)
        assertEquals(listOf("org.jellyfin.androidtv"), host.launched)
        assertEquals(404, call("POST", "/launch", """{"packageName":"x.y"}""", t).status)
        assertEquals(400, call("POST", "/launch", "{}", t).status)
        assertEquals(200, call("POST", "/quick-menu", token = t).status)
        assertEquals(1, host.quickMenuOpened)
    }

    @Test fun historyIsBehindItsOwnToggle() = runTest {
        val t = pairToken()
        assertEquals(403, call("GET", "/history", token = t).status)
        host.historyOn = true
        val r = call("GET", "/history", token = t, query = mapOf("limit" to "5"))
        assertEquals(200, r.status)
        assertEquals("The Office", json(r).getJSONArray("items").getJSONObject(0).getString("title"))
    }

    @Test fun favouritesUpsertReorderDeleteRestoreAndChanges() = runTest {
        val t = pairToken()
        val a = json(call("PUT", "/favourites/a", """{"title":"Severance","updatedAt":5}""", t))
        assertEquals(now, a.getLong("updatedAt")) // client updatedAt ignored
        now = 2_000
        call("PUT", "/favourites/b", """{"title":"Andor"}""", t)
        assertEquals(422, call("PUT", "/favourites/c", """{"title":"  "}""", t).status)
        assertEquals(200, call("POST", "/favourites/reorder", """{"ids":["b"]}""", t).status)
        assertEquals(0, store.favs.getValue("b").position)
        assertEquals(422, call("POST", "/favourites/reorder", """{"ids":["zzz"]}""", t).status)
        now = 3_000
        assertEquals(200, call("DELETE", "/favourites/a", token = t).status)
        val changes = json(call("GET", "/changes", token = t, query = mapOf("since" to "3000")))
        val f = changes.getJSONArray("favourites")
        assertEquals(1, (0 until f.length()).count { !f.getJSONObject(it).isNull("removedAt") })
        assertEquals(3_000, changes.getLong("asOf"))
        assertEquals(200, call("POST", "/favourites/a/restore", token = t).status)
        assertTrue(store.favs.getValue("a").removedAt == null)
        assertEquals(422, call("DELETE", "/favourites/nope", token = t).status)
    }

    @Test fun shelfPinsAreManualAndReturnTheStoredEntry() = runTest {
        val t = pairToken()
        val first = json(call("PUT", "/shelf-entries/s1",
            """{"provider":"jellyfin","title":"The Expanse","externalId":"abc","source":"PROVIDER_ROW"}""", t))
        assertEquals("MANUAL", first.getString("source"))
        val again = json(call("PUT", "/shelf-entries/s2", """{"provider":"jellyfin","title":"The Expanse","externalId":"abc"}""", t))
        assertEquals("s1", again.getString("syncId"))
        assertEquals(400, call("PUT", "/shelf-entries/s3", """{"title":"No provider"}""", t).status)
        assertEquals(200, call("POST", "/shelf-entries/reorder", """{"provider":"jellyfin","ids":["s1"]}""", t).status)
        assertEquals(400, call("POST", "/shelf-entries/reorder", """{"ids":["s1"]}""", t).status)
    }

    @Test fun rowsOnlyChangeVisibleTitleAndTileSize() = runTest {
        val t = pairToken()
        val r = json(call("PUT", "/rows/shelf:peacock", """{"visible":false,"kind":"HACK","tileSize":"large"}""", t))
        assertFalse(r.getBoolean("visible"))
        assertEquals("PROVIDER_SHELF", r.getString("kind"))
        assertEquals("LARGE", r.getString("tileSize"))
        assertEquals(422, call("PUT", "/rows/unknown", """{"visible":false}""", t).status)
        assertEquals(400, call("PUT", "/rows/shelf:peacock", """{"tileSize":"HUGE"}""", t).status)
        assertEquals(405, call("DELETE", "/rows/shelf:peacock", token = t).status)
    }

    @Test fun badInputIsRejectedCleanly() = runTest {
        val t = pairToken()
        assertEquals(400, call("PUT", "/favourites/a", "not json", t).status)
        assertEquals(404, call("GET", "/nothing", token = t).status)
        assertEquals(405, call("POST", "/apps", token = t).status)
        assertEquals(404, router.handle(CompanionRequest("GET", "/other")).status)
    }

    @Test fun onlyLanAddressesAreServed() {
        assertTrue(CompanionServer.isLanAddress(InetAddress.getByName("192.168.0.139")))
        assertTrue(CompanionServer.isLanAddress(InetAddress.getByName("10.1.2.3")))
        assertTrue(CompanionServer.isLanAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(CompanionServer.isLanAddress(InetAddress.getByName("fe80::1")))
        assertFalse(CompanionServer.isLanAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(CompanionServer.isLanAddress(InetAddress.getByName("2001:4860::8888")))
    }
}
