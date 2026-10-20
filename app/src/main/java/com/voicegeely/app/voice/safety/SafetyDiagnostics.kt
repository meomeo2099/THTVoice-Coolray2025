package com.voicegeely.app.voice.safety

/** Read-only facade for showing recent safety decisions in debug UI or support logs. */
object SafetyDiagnostics {
    data class Snapshot(
        val totalBuffered: Int,
        val blocked: Int,
        val confirmationsRequired: Int,
        val confirmedExecutions: Int,
        val failedExecutions: Int,
        val lines: List<String>
    )

    fun snapshot(limit: Int = 20, logger: SafetyEventLogger = SafetyAuditRegistry.logger): Snapshot {
        val events = logger.snapshot()
        val safeLimit = limit.coerceAtLeast(0)
        return Snapshot(
            totalBuffered = events.size,
            blocked = events.count { it.decision == SafetyDecisionType.BLOCK },
            confirmationsRequired = events.count { it.decision == SafetyDecisionType.REQUIRE_CONFIRMATION },
            confirmedExecutions = events.count { it.phase == "RESULT" && it.result == "CONFIRMED" },
            failedExecutions = events.count { it.phase == "RESULT" && it.result == "FAILED" },
            lines = events.takeLast(safeLimit).map(SafetyAuditFormatter::format)
        )
    }

    fun clear(logger: SafetyEventLogger = SafetyAuditRegistry.logger) = logger.clear()
}
