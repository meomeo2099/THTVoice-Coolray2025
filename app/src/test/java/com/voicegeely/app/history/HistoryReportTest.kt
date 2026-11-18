package com.voicegeely.app.history

import org.junit.Assert.*
import org.junit.Test

class HistoryReportTest {
    private val snapshot = HistorySnapshot(listOf(
        HistoryCommand("a", "Bật điều hòa", "Đã xác nhận", "CONFIRMED", 1),
        HistoryCommand("b", "Hạ kính", "API từ chối", "REJECTED", 3),
        HistoryCommand("c", "Mở cốp", "Chưa xác nhận", "ACCEPTED_UNVERIFIED", 4)),
        listOf(HistoryDiagnostic("d", "Binder disconnected", 2)))

    @Test fun filtersUseOutcomeAndText() {
        assertEquals(listOf("ACCEPTED_UNVERIFIED", "REJECTED"), HistoryReport.rows(snapshot, HistoryFilter.PROBLEMS, "").map { it.outcome })
        assertEquals(1, HistoryReport.rows(snapshot, HistoryFilter.CONFIRMED, "ĐIỀU HÒA").size)
        assertEquals("DIAGNOSTIC", HistoryReport.rows(snapshot, HistoryFilter.DIAGNOSTICS, "binder").single().outcome)
        assertTrue(HistoryReport.rows(snapshot, HistoryFilter.DIAGNOSTICS, "kính").isEmpty())
        assertEquals(listOf(4L, 3L, 2L, 1L), HistoryReport.rows(snapshot, HistoryFilter.ALL, "").map { it.timestampMs })
    }

    @Test fun reportRedactsAndBoundsPrivateFields() {
        val privateText = "VIN L6T12345678901234\nphone +84 912 345 678\napi_key=supersecret\nAuthorization: Bearer hidden\nhttps://host/path?token=private\nuser@example.com"
        val text = HistoryReport.export(listOf(HistoryLine(0, "FAILED", privateText)))
        listOf("L6T12345678901234", "912 345 678", "supersecret", "hidden", "https://", "user@example.com").forEach { assertFalse(it, text.contains(it)) }
        val huge = HistoryReport.export(List(3000) { HistoryLine(it.toLong(), "FAILED", "x".repeat(2000)) })
        assertTrue(huge.length <= HistoryReport.MAX_CHARS)
        val many = HistorySnapshot(List(2000) { HistoryCommand("$it", "x", "y", "FAILED", it.toLong()) }, emptyList())
        assertEquals(500, HistoryReport.rows(many, HistoryFilter.ALL, "").size)
    }

    @Test fun capabilityTokenExpiresAndCannotBeReplayedAfterStop() {
        var now = 0L
        val a = HistoryShareServer("test", 100, { now })
        val b = HistoryShareServer("test", 100, { now })
        assertEquals(48, a.token.length)
        assertNotEquals(a.token, b.token)
        val path = "/history/${a.token}.txt"
        assertTrue(a.authorized("GET", path))
        assertFalse(a.authorized("POST", path))
        assertFalse(a.authorized("GET", "$path?extra=1"))
        assertFalse(a.authorized("GET", "/history/${b.token}.txt"))
        now = 100
        assertFalse(a.authorized("GET", path))
        now = 1
        assertFalse(a.authorized("GET", path))
        a.stop()
        assertFalse(a.authorized("GET", path))
    }

    @Test fun refusesPublicAndLoopbackBinding() {
        for (ip in listOf("127.0.0.1", "8.8.8.8")) {
            assertNull(HistoryShareServer("test", address = { java.net.InetAddress.getByName(ip) }).start())
        }
    }

    @Test fun realHttpServesOnlyTokenAndRejectsOversizedHeaders() {
        val host = HistoryShareServer.lanAddress()
        org.junit.Assume.assumeNotNull(host)
        val server = HistoryShareServer("api_key=hidden\nvisible diagnostic", address = { host })
        try {
            val url = java.net.URL(server.start()!!)
            fun request(header: String): String = java.net.Socket(url.host, url.port).use { socket ->
                socket.soTimeout = 4000
                socket.getOutputStream().write(header.toByteArray())
                socket.getInputStream().bufferedReader().readText()
            }
            assertTrue(request("GET /wrong HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 404"))
            val reply = request("GET ${url.path} HTTP/1.1\r\nHost: test\r\n\r\n")
            assertTrue(reply.startsWith("HTTP/1.1 200"))
            assertTrue(reply.contains("visible diagnostic"))
            assertFalse(reply.contains("hidden"))
            assertTrue(request("GET ${url.path} HTTP/1.1\r\nX: " + "x".repeat(5000)).startsWith("HTTP/1.1 404"))
        } finally { server.stop() }
    }
}
