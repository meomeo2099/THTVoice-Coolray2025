package com.voicegeely.app.kws

enum class VoiceMicState {
    IDLE,
    KWS_LISTENING,
    ASR_LISTENING,
    PROCESSING,
    CLOSED
}

/**
 * Process-local microphone ownership guard.
 *
 * The foreground service owns the actual AudioRecord instances; this class
 * makes illegal KWS/ASR overlap explicit and unit-testable.
 */
class VoiceMicStateMachine {
    @Volatile
    var state: VoiceMicState = VoiceMicState.IDLE
        private set

    @Synchronized
    fun beginKws(): Boolean {
        if (state != VoiceMicState.IDLE) return false
        state = VoiceMicState.KWS_LISTENING
        return true
    }

    @Synchronized
    fun endKws() {
        if (state == VoiceMicState.KWS_LISTENING) state = VoiceMicState.IDLE
    }

    @Synchronized
    fun beginAsr(): Boolean {
        if (state != VoiceMicState.IDLE) return false
        state = VoiceMicState.ASR_LISTENING
        return true
    }

    @Synchronized
    fun beginProcessing() {
        if (state == VoiceMicState.ASR_LISTENING) state = VoiceMicState.PROCESSING
    }

    @Synchronized
    fun endProcessing() {
        if (state == VoiceMicState.PROCESSING || state == VoiceMicState.ASR_LISTENING) {
            state = VoiceMicState.IDLE
        }
    }

    /** Watchdog only: give up a session that never finished (state back to IDLE unless closed). */
    @Synchronized
    fun forceIdle() {
        if (state != VoiceMicState.CLOSED) state = VoiceMicState.IDLE
    }

    @Synchronized
    fun close() {
        state = VoiceMicState.CLOSED
    }
}
