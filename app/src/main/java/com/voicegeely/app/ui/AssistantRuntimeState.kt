package com.voicegeely.app.ui

enum class AssistantRuntimePhase {
    STARTING,
    READY,
    KWS_LISTENING,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR
}

data class AssistantHistoryEntry(
    val command: String,
    val result: String,
    val timestampMs: Long
)

/** Last engine readiness published by the Voice core; lets a newly opened screen show it at once. */
data class EngineReadiness(
    val asrReady: Boolean,
    val kwsReady: Boolean,
    val ttsReady: Boolean,
    val ttsDetail: String? = null
)

data class AssistantRuntimeSnapshot(
    val phase: AssistantRuntimePhase = AssistantRuntimePhase.STARTING,
    val statusMessage: String = "Đang khởi tạo Voice core...",
    val partialText: String = "",
    val finalText: String = "",
    val serviceReady: Boolean = false,
    val asrReady: Boolean = false,
    val kwsReady: Boolean = false,
    val ttsReady: Boolean = false,
    /** Why offline speech is not ready (shown on Home so it can be diagnosed without adb). */
    val ttsDetail: String? = null,
    val history: List<AssistantHistoryEntry> = emptyList()
)

/**
 * Process-local UI state derived only from real service broadcasts.
 *
 * It deliberately does not claim VHAL connectivity because the current service
 * does not broadcast that telemetry yet. The screen renders that limitation
 * explicitly instead of inventing a connected state.
 */
class AssistantRuntimeStore(
    private val maxHistory: Int = 6,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    var state: AssistantRuntimeSnapshot = AssistantRuntimeSnapshot()
        private set

    fun initialize(serviceReady: Boolean, readiness: EngineReadiness? = null) {
        if (!serviceReady) return
        state = state.copy(
            asrReady = readiness?.asrReady ?: state.asrReady,
            kwsReady = readiness?.kwsReady ?: state.kwsReady,
            ttsReady = readiness?.ttsReady ?: state.ttsReady,
            ttsDetail = if (readiness != null) readiness.ttsDetail else state.ttsDetail,
            serviceReady = true,
            phase = if (state.phase == AssistantRuntimePhase.STARTING) AssistantRuntimePhase.READY else state.phase,
            statusMessage = if (state.statusMessage.startsWith("Đang khởi tạo")) "Voice core đã sẵn sàng" else state.statusMessage
        )
    }

    fun onServiceReady() {
        state = state.copy(
            serviceReady = true,
            phase = AssistantRuntimePhase.READY,
            statusMessage = "Voice core đã sẵn sàng"
        )
    }

    fun onRecognition(text: String, isFinal: Boolean) {
        val clean = text.trim()
        if (clean.isBlank()) return
        state = if (isFinal) {
            val history = upsertHistory(clean, "Đang xử lý...")
            state.copy(
                phase = AssistantRuntimePhase.PROCESSING,
                partialText = clean,
                finalText = clean,
                statusMessage = "Đang xử lý...",
                history = history
            )
        } else {
            state.copy(
                phase = AssistantRuntimePhase.LISTENING,
                partialText = clean,
                statusMessage = "Đang nghe..."
            )
        }
    }

    fun onEngineStatus(
        message: String,
        asrReady: Boolean,
        kwsReady: Boolean,
        ttsReady: Boolean,
        ttsDetail: String? = null
    ) {
        val clean = message.trim().ifBlank { "THT Voice" }
        val phase = inferPhase(clean)
        // Only the command's own outcome may fill its history row. Later idle statuses
        // ("Hey Geely đang chờ lệnh", "Đã nghe Hey Geely") must not overwrite "Đã mở YouTube".
        val history = if (state.finalText.isNotBlank() && !isIdleStatus(clean) && historyPending(state.finalText) && phase in setOf(
                AssistantRuntimePhase.PROCESSING,
                AssistantRuntimePhase.RESULT,
                AssistantRuntimePhase.ERROR
            )) {
            upsertHistory(state.finalText, clean)
        } else state.history

        state = state.copy(
            phase = phase,
            statusMessage = clean,
            serviceReady = true,
            asrReady = asrReady,
            kwsReady = kwsReady,
            ttsReady = ttsReady,
            ttsDetail = ttsDetail,
            history = history
        )
    }

    fun markManualListening() {
        state = state.copy(
            phase = AssistantRuntimePhase.LISTENING,
            statusMessage = "Đang yêu cầu Voice core mở mic...",
            partialText = ""
        )
    }

    private fun upsertHistory(command: String, result: String): List<AssistantHistoryEntry> {
        val cleanCommand = command.trim()
        if (cleanCommand.isBlank()) return state.history
        val items = state.history.toMutableList()
        val index = items.indexOfFirst { it.command == cleanCommand }
        val entry = AssistantHistoryEntry(cleanCommand, result.trim(), now())
        if (index >= 0) items[index] = entry else items.add(0, entry)
        return items.sortedByDescending { it.timestampMs }.take(maxHistory.coerceAtLeast(1))
    }

    /** The row still shows "Đang xử lý..." / "Đang …", i.e. no final result has arrived yet. */
    private fun historyPending(command: String): Boolean =
        state.history.firstOrNull { it.command == command.trim() }?.result?.startsWith("Đang") ?: false

    private fun isIdleStatus(message: String): Boolean {
        val lower = message.lowercase()
        return "đang chờ lệnh" in lower || "đang nghe" in lower || lower.startsWith("đã nghe ") ||
            lower.startsWith("đã cập nhật cài đặt") || lower.startsWith("voice core") ||
            lower.startsWith("nhận giọng nói offline")
    }

    private fun inferPhase(message: String): AssistantRuntimePhase {
        val lower = message.lowercase()
        return when {
            "đang chờ lệnh" in lower || "hey tht đang chờ" in lower -> AssistantRuntimePhase.KWS_LISTENING
            "đang nghe" in lower || lower.startsWith("đã nghe ") -> AssistantRuntimePhase.LISTENING
            lower.startsWith("đang ") || "đang xử lý" in lower || "đang mở" in lower -> AssistantRuntimePhase.PROCESSING
            lower.startsWith("không ") || lower.startsWith("lỗi ") || lower.startsWith("chưa ") ||
                "bị chặn" in lower || "không thực hiện" in lower -> AssistantRuntimePhase.ERROR
            state.finalText.isNotBlank() -> AssistantRuntimePhase.RESULT
            else -> AssistantRuntimePhase.READY
        }
    }
}
