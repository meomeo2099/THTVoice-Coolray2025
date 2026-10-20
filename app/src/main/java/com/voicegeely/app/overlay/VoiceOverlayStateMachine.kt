package com.voicegeely.app.overlay

enum class VoiceOverlayPhase {
    HIDDEN,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR
}

data class VoiceOverlayState(
    val phase: VoiceOverlayPhase = VoiceOverlayPhase.HIDDEN,
    val title: String = "",
    val text: String = "",
    val transcript: String = "",
    val result: String = ""
)

/**
 * Pure state holder for the floating voice UI. Keeping state transitions free
 * of Android UI code makes partial/final/result behavior regression-testable.
 */
class VoiceOverlayStateMachine {
    var state: VoiceOverlayState = VoiceOverlayState()
        private set

    @Synchronized
    fun listening(): VoiceOverlayState = assign(
        VoiceOverlayPhase.LISTENING,
        title = "Đang nghe",
        text = "",
        transcript = "",
        result = ""
    )

    @Synchronized
    fun partial(text: String): VoiceOverlayState {
        val normalized = text.trim()
        return assign(
            VoiceOverlayPhase.LISTENING,
            title = "Đang nghe",
            text = normalized,
            transcript = normalized,
            result = ""
        )
    }

    @Synchronized
    fun processing(finalText: String): VoiceOverlayState {
        val normalized = finalText.trim()
        return assign(
            VoiceOverlayPhase.PROCESSING,
            title = "Đang xử lý",
            text = normalized,
            transcript = normalized.ifBlank { state.transcript },
            result = "Vui lòng chờ trong giây lát..."
        )
    }

    @Synchronized
    fun result(message: String): VoiceOverlayState {
        val normalized = message.trim()
        return assign(
            VoiceOverlayPhase.RESULT,
            title = "Hoàn tất",
            text = normalized,
            transcript = state.transcript,
            result = normalized
        )
    }

    @Synchronized
    fun error(message: String): VoiceOverlayState {
        val normalized = message.trim()
        return assign(
            VoiceOverlayPhase.ERROR,
            title = "Lỗi",
            text = normalized,
            transcript = state.transcript,
            result = normalized
        )
    }

    @Synchronized
    fun hide(): VoiceOverlayState = assign(VoiceOverlayPhase.HIDDEN, "", "", "", "")

    @Synchronized
    private fun assign(
        phase: VoiceOverlayPhase,
        title: String,
        text: String,
        transcript: String,
        result: String
    ): VoiceOverlayState {
        state = VoiceOverlayState(phase, title, text, transcript, result)
        return state
    }
}
