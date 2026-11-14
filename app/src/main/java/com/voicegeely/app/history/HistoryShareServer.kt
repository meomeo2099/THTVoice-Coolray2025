package com.voicegeely.app.history

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/** Explicit LAN-only, read-only snapshot. No persistent service and no request body. */
class HistoryShareServer(
    report: String,
    private val ttlMs: Long = 5 * 60_000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val address: () -> InetAddress? = { lanAddress() }
) {
    private val body = HistoryReport.redact(report.take(HistoryReport.MAX_CHARS)).toByteArray(Charsets.UTF_8)
    val token: String = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val created = clock()
    @Volatile private var stopped = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    private var started = false

    @Synchronized fun start(): String? {
        if (started || expired()) return null
        val host = address()?.takeIf { it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress } ?: return null
        val socket = ServerSocket()
        try { socket.bind(InetSocketAddress(host, 0), 2) } catch (e: Exception) { socket.close(); throw e }
        started = true
        server = socket
        socket.soTimeout = 500
        Thread({ serve(socket) }, "THTVoice-HistoryShare").apply { isDaemon = true }.start()
        Thread({
            while (!expired()) { try { Thread.sleep(250) } catch (_: InterruptedException) { break } }
            stop()
        }, "THTVoice-HistoryExpiry").apply { isDaemon = true }.start()
        return "http://${host.hostAddress}:${socket.localPort}/history/$token.txt"
    }

    fun stop() {
        stopped = true
        runCatching { client?.close() }
        runCatching { server?.close() }
    }

    private fun expired(): Boolean {
        if (stopped) return true
        if (clock() - created !in 0 until ttlMs.coerceIn(1, 300_000)) stopped = true
        return stopped
    }

    fun authorized(method: String?, path: String?): Boolean = !expired() && method == "GET" && path == "/history/$token.txt"

    private fun serve(socket: ServerSocket) {
        var attempts = 0
        try {
            while (!expired() && attempts < 16) {
                val connection = try { socket.accept() } catch (_: java.net.SocketTimeoutException) { continue }
                attempts++
                client = connection
                connection.use { c ->
                    try {
                        if (!c.inetAddress.isSiteLocalAddress) return@use
                        c.soTimeout = 1000
                        // Absolute deadline prevents slow clients from extending the share lifetime.
                        val deadline = minOf(created + ttlMs.coerceIn(1, 300_000), clock() + 2000)
                        val input = c.getInputStream()
                        val header = StringBuilder()
                        var complete = false
                        while (header.length < 4096 && clock() < deadline && !expired()) {
                            val b = input.read()
                            if (b < 0 || b > 127) break
                            header.append(b.toChar())
                            if (header.endsWith("\r\n\r\n")) { complete = true; break }
                        }
                        val parts = header.toString().substringBefore("\r\n").split(' ')
                        val ok = complete && parts.size == 3 && parts[2] in setOf("HTTP/1.0", "HTTP/1.1") && authorized(parts[0], parts[1])
                        val bytes = if (ok) body else ByteArray(0)
                        val status = if (ok) "200 OK" else "404 Not Found"
                        val response = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Disposition: attachment; filename=tht-voice-log.txt\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
                        c.getOutputStream().apply { write(response.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
                    } catch (_: Exception) { /* bounded client failure */ }
                }
                client = null
            }
        } catch (_: Exception) { /* stop closes accept */ } finally { stop() }
    }

    companion object {
        fun lanAddress(): InetAddress? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().asSequence().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
        }.getOrNull()
    }
}
