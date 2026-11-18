package com.voicegeely.app.history

enum class HistoryFilter(val label: String) {
    ALL("Tất cả"), PROBLEMS("Lỗi / chưa xác nhận"), CONFIRMED("Đã xác nhận"), DIAGNOSTICS("Chẩn đoán")
}

data class HistoryLine(val timestampMs: Long, val outcome: String, val text: String)

/** Pure read-only presentation; no replay or vehicle access. */
object HistoryReport {
    const val MAX_ROWS = 500
    const val MAX_CHARS = 60_000

    fun rows(snapshot: HistorySnapshot, filter: HistoryFilter, query: String): List<HistoryLine> {
        val commands = snapshot.commands.take(1000).map { HistoryLine(it.timestampMs, it.outcome, "${it.command}\n${it.result}") }
        val diagnostics = snapshot.diagnostics.take(2000).map { HistoryLine(it.timestampMs, "DIAGNOSTIC", it.message) }
        val text = query.trim().take(240)
        return (commands + diagnostics).asSequence().filter {
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.PROBLEMS -> it.outcome in setOf("FAILED", "REJECTED", "ACCEPTED_UNVERIFIED", "INTERRUPTED")
                HistoryFilter.CONFIRMED -> it.outcome == "CONFIRMED"
                HistoryFilter.DIAGNOSTICS -> it.outcome == "DIAGNOSTIC"
            }
        }.filter { text.isEmpty() || (it.outcome + " " + it.text).contains(text, ignoreCase = true) }
            .sortedByDescending { it.timestampMs }.take(MAX_ROWS).toList()
    }

    fun export(rows: List<HistoryLine>): String = buildString {
        append("THT Voice — nhật ký chẩn đoán (tối đa 500 mục)\n")
        val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        rows.take(MAX_ROWS).forEach {
            if (length < MAX_CHARS) append("${date.format(java.util.Date(it.timestampMs))} UTC [${redact(it.outcome).take(40)}] ${redact(it.text.take(1200))}\n")
        }
    }.take(MAX_CHARS)

    fun redact(value: String): String = value
        .replace(Regex("(?im)^.*(?:authorization|bearer|api[ _-]?key|token|secret|password|passwd|activation|license[ _-]?key|cookie|private[ _-]?key).*$"), "[REDACTED]")
        .replace(Regex("(?i)https?://\\S+"), "[URL]")
        .replace(Regex("(?i)\\b[A-HJ-NPR-Z0-9]{17}\\b"), "[VIN]")
        .replace(Regex("(?<![\\p{L}\\d])\\+?\\d[\\d .()-]{7,}\\d(?![\\p{L}\\d])"), "[PHONE]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]")
        .replace(Regex("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{24,}(?![A-Za-z0-9])"), "[IDENTIFIER]")
        .replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ")
}
