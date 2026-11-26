package com.firestarterpro.templartv.jellyfin

import com.firestarterpro.templartv.companion.CompanionServer
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * A tiny web page for entering the Jellyfin server and key from a phone or PC instead of the
 * remote. It runs only while the Jellyfin setup form is on screen, answers LAN addresses only,
 * and needs the 6-digit code shown on the TV. After 5 wrong codes it stops.
 */
class JellyfinSetupServer(
    private val defaultUrl: String,
    private val onSave: (serverUrl: String, apiKey: String) -> Unit,
    private val port: Int = PORT,
) {
    val code: String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var failures = 0

    fun start() {
        if (socket != null) return
        val ss = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port), 4) } }.getOrNull() ?: return
        socket = ss
        Thread({
            while (!ss.isClosed) {
                val c = try { ss.accept() } catch (_: Exception) { break }
                if (!CompanionServer.isLanAddress(c.inetAddress)) { runCatching { c.close() }; continue }
                Thread({ serve(c) }, "jellyfin-setup-worker").apply { isDaemon = true; start() }
            }
        }, "jellyfin-setup").apply { isDaemon = true; start() }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun serve(s: Socket) = s.use {
        runCatching {
            s.soTimeout = 10_000
            val input = BufferedInputStream(s.getInputStream())
            val head = readHead(input) ?: return@runCatching
            val lines = head.split("\r\n")
            val method = lines.first().substringBefore(' ')
            val length = lines.drop(1).firstOrNull { it.lowercase().startsWith("content-length:") }
                ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceIn(0, 8 * 1024) ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
            val html = if (method == "POST") handlePost(String(body, 0, read, Charsets.UTF_8)) else page("")
            val bytes = html.toByteArray(Charsets.UTF_8)
            val out = s.getOutputStream()
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
            out.write(bytes)
            out.flush()
        }
    }

    private fun handlePost(form: String): String {
        val f = form.split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        if (failures >= 5) return page("Too many wrong codes. Open Jellyfin setup on the TV again for a new code.")
        if (f["code"].orEmpty().trim() != code) {
            failures++
            if (failures >= 5) stop()
            return page("Wrong code. Check the 6 digits on the TV.")
        }
        val url = f["url"].orEmpty().trim().ifEmpty { defaultUrl }
        val key = f["key"].orEmpty().trim()
        if (key.isEmpty()) return page("The API key is empty.")
        onSave(url, key)
        return "<!doctype html><meta name=viewport content='width=device-width'><body style='font-family:sans-serif;padding:24px'>" +
            "<h2>Saved</h2><p>Look at your TV: Templar TV is loading your Jellyfin library.</p></body>"
    }

    private fun page(message: String): String {
        val msg = if (message.isEmpty()) "" else "<p style='color:#b00'>${esc(message)}</p>"
        return """<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width'>
<title>Templar TV Jellyfin setup</title></head><body style='font-family:sans-serif;padding:24px;max-width:520px'>
<h2>Templar TV: connect Jellyfin</h2>$msg
<form method=post>
<p>Code shown on the TV<br><input name=code inputmode=numeric autocomplete=off style='font-size:20px;width:100%'></p>
<p>Server address<br><input name=url value='${esc(defaultUrl)}' style='font-size:18px;width:100%'></p>
<p>API key (Jellyfin dashboard, API Keys)<br><input name=key autocomplete=off style='font-size:18px;width:100%'></p>
<p><button style='font-size:20px;padding:10px 24px'>Save to TV</button></p>
</form><p style='color:#666'>The key is saved on the TV only.</p></body></html>"""
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&#39;").replace("\"", "&quot;")

    private fun readHead(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (out.size() > 8 * 1024) return null
        }
        return out.toString(Charsets.ISO_8859_1.name())
    }

    companion object { const val PORT = 8781 }
}
