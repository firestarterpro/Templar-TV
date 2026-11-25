package com.firestarterpro.templartv.companion

import com.firestarterpro.templartv.data.sync.SyncChanges
import com.firestarterpro.templartv.data.sync.SyncFavourite
import com.firestarterpro.templartv.data.sync.SyncProfile
import com.firestarterpro.templartv.data.sync.SyncRow
import com.firestarterpro.templartv.data.sync.SyncShelfEntry
import com.firestarterpro.templartv.data.sync.SyncStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket

/** Real sockets on loopback: request parsing, headers, limits. */
class CompanionServerTest {
    private val emptyStore = object : SyncStore {
        override suspend fun profiles() = listOf(SyncProfile("default", "Default", false))
        override suspend fun favouritesSince(profileId: String, since: Long) = SyncChanges(emptyList<SyncFavourite>(), 1)
        override suspend fun upsertFavourite(favourite: SyncFavourite): SyncFavourite? = null
        override suspend fun reorderFavourites(profileId: String, orderedSyncIds: List<String>) = false
        override suspend fun softDeleteFavourite(syncId: String) = false
        override suspend fun restoreFavourite(syncId: String) = false
        override suspend fun shelfEntriesSince(profileId: String, since: Long) = SyncChanges(emptyList<SyncShelfEntry>(), 1)
        override suspend fun upsertShelfEntry(entry: SyncShelfEntry): SyncShelfEntry? = null
        override suspend fun reorderShelf(profileId: String, provider: String, orderedSyncIds: List<String>) = false
        override suspend fun softDeleteShelfEntry(syncId: String) = false
        override suspend fun restoreShelfEntry(syncId: String) = false
        override suspend fun rowsSince(profileId: String, since: Long) = SyncChanges(emptyList<SyncRow>(), 1)
        override suspend fun upsertRow(row: SyncRow): SyncRow? = null
        override suspend fun reorderRows(profileId: String, orderedRowIds: List<String>) = false
    }
    private val host = object : CompanionHost {
        override val appVersion = "t"
        override fun historyEnabled() = false
        override suspend fun apps() = emptyList<CompanionApp>()
        override suspend fun setFavouriteApp(packageName: String, favourite: Boolean) = false
        override fun launch(packageName: String): Boolean? = null
        override fun openQuickMenu() = false
        override suspend fun history(profileId: String, limit: Int) = emptyList<CompanionHistoryItem>()
    }
    private lateinit var pairing: CompanionPairing
    private lateinit var server: CompanionServer
    private var port = 0

    @Before fun start() {
        port = ServerSocket(0).use { it.localPort }
        pairing = CompanionPairing(InMemoryPairedDeviceStore())
        server = CompanionServer(CompanionRouter(pairing, emptyStore, host), port)
        server.start()
    }

    @After fun stop() = server.stop()

    private fun raw(request: String): String = Socket("127.0.0.1", port).use { s ->
        s.soTimeout = 5000
        s.getOutputStream().apply { write(request.toByteArray()); flush() }
        s.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    @Test fun statusOverHttp() {
        val r = raw("GET /api/companion/v1/status HTTP/1.1\r\nHost: tv\r\n\r\n")
        assertTrue(r.startsWith("HTTP/1.1 200 OK"))
        assertTrue(r.contains("Content-Type: application/json"))
        assertFalse(r.contains("Access-Control-Allow-Origin"))
        assertTrue(r.contains("\"apiVersion\":1"))
    }

    @Test fun pairAndUseTokenOverHttp() {
        val code = pairing.startPairing()
        val body = """{"code":"$code","deviceName":"Tab"}"""
        val r = raw("POST /api/companion/v1/pair HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body")
        assertTrue(r, r.startsWith("HTTP/1.1 200"))
        val token = Regex("\"token\":\"([0-9a-f]{64})\"").find(r)!!.groupValues[1]
        val q = raw("GET /api/companion/v1/rows?profile=default&since=0 HTTP/1.1\r\nAuthorization: Bearer $token\r\n\r\n")
        assertTrue(q, q.startsWith("HTTP/1.1 200"))
        assertTrue(raw("GET /api/companion/v1/rows HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 401"))
    }

    @Test fun malformedAndOversizedRequestsAreRejected() {
        assertTrue(raw("NONSENSE\r\n\r\n").startsWith("HTTP/1.1 400"))
        assertTrue(raw("POST /api/companion/v1/pair HTTP/1.1\r\nContent-Length: 999999\r\n\r\n").startsWith("HTTP/1.1 413"))
        assertTrue(raw("POST /api/companion/v1/pair HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 400"))
    }

    @Test fun stopClosesThePort() {
        server.stop()
        assertFalse(server.isRunning)
        val refused = runCatching { Socket("127.0.0.1", port).close() }.isFailure
        assertEquals(true, refused)
    }
}
