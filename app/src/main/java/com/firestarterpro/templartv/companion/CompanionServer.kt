package com.firestarterpro.templartv.companion

import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Minimal HTTP/1.1 server for the companion API: one request per connection, JSON only, LAN only.
 * Deliberately small and dependency-free; anything unusual is answered with 400 and the socket closed.
 */
class CompanionServer(private val router: CompanionRouter, private val port: Int = DEFAULT_PORT) {

    @Volatile private var socket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workers: ExecutorService? = null

    val isRunning: Boolean get() = socket?.isClosed == false

    @Synchronized
    fun start() {
        if (isRunning) return
        val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port), 16) }
        socket = ss
        val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "companion-worker").apply { isDaemon = true } }
        workers = pool
        acceptThread = Thread({
            while (!ss.isClosed) {
                val client = try { ss.accept() } catch (_: Exception) { break }
                if (!isLanAddress(client.inetAddress)) { client.closeQuietly(); continue }
                try { pool.execute { serve(client) } } catch (_: RejectedExecutionException) { client.closeQuietly() }
            }
        }, "companion-accept").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        workers?.shutdownNow()
        workers = null
        acceptThread = null
    }

    private fun serve(client: Socket) {
        client.use { s ->
            s.soTimeout = 5_000
            val response = try {
                val req = parse(BufferedInputStream(s.getInputStream()))
                if (req == null) CompanionRouter.error(400, "bad_request", "Malformed request")
                else runBlocking { router.handle(req) }
            } catch (_: TooLarge) {
                CompanionRouter.error(413, "bad_request", "Request too large")
            } catch (_: Exception) {
                return
            }
            runCatching { write(s, response) }
        }
    }

    private class TooLarge : Exception()

    private fun parse(input: InputStream): CompanionRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return null
        val headers = lines.drop(1).filter { it.isNotEmpty() }.mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        if (headers["transfer-encoding"] != null) return null
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0) return null
        if (length > MAX_BODY) throw TooLarge()
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val query = if (q >= 0) target.substring(q + 1).split('&').filter { it.isNotEmpty() }.associate { kv ->
            val e = kv.indexOf('=')
            if (e < 0) decode(kv) to "" else decode(kv.substring(0, e)) to decode(kv.substring(e + 1))
        } else emptyMap()
        return CompanionRequest(parts[0], path, query, headers, String(body, Charsets.UTF_8))
    }

    private fun readHead(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (out.size() > MAX_HEAD) throw TooLarge()
        }
        return out.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
    }

    private fun write(s: Socket, r: CompanionResponse) {
        val body = r.body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 ${r.status} ${reason(r.status)}\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        s.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); write(body); flush() }
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"
        405 -> "Method Not Allowed"; 410 -> "Gone"; 413 -> "Payload Too Large"; 422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"; else -> "Error"
    }

    private fun Socket.closeQuietly() = runCatching { close() }

    companion object {
        const val DEFAULT_PORT = 8780
        private const val MAX_BODY = 64 * 1024
        private const val MAX_HEAD = 8 * 1024

        /** Private (RFC 1918 / unique-local), link-local or loopback only. */
        fun isLanAddress(a: InetAddress?): Boolean = when (a) {
            null -> false
            is Inet4Address -> a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress
            is Inet6Address -> a.isLinkLocalAddress || a.isLoopbackAddress || a.isSiteLocalAddress ||
                (a.address[0].toInt() and 0xFE) == 0xFC || // fc00::/7 unique local
                a.isIPv4CompatibleAddress && isLanAddress(InetAddress.getByAddress(a.address.copyOfRange(12, 16)))
            else -> false
        }
    }
}
