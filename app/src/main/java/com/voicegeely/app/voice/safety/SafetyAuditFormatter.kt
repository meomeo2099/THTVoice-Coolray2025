package com.voicegeely.app.voice.safety

/** Human-readable safety diagnostics for on-device debug screens and logs. */
object SafetyAuditFormatter {
    fun format(event: SafetyEvent): String = buildString {
        append("[").append(event.phase).append("] ")
        append(event.command)
        append(" • ").append(event.decision)
        append(" • requirement=").append(event.requirement)
        event.speedKph?.let { append(" • speed=").append(trimFloat(it)).append(" km/h") }
        event.gear?.let { append(" • gear=").append(gearName(it)) }
        event.confirmation?.let { append(" • confirmation=").append(it) }
        event.result?.let { append(" • result=").append(it) }
        event.detail?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
    }

    fun formatLatest(logger: SafetyEventLogger, limit: Int = 20): List<String> {
        if (limit <= 0) return emptyList()
        return logger.snapshot().takeLast(limit).map(::format)
    }

    private fun gearName(gear: Int): String = when (gear) {
        1 -> "N"
        2 -> "R"
        4 -> "P"
        8 -> "D"
        else -> gear.toString()
    }

    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(java.util.Locale.US, value)
}
