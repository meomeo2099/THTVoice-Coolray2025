package com.voicegeely.app.asr

/**
 * Boundary for the offline streaming ASR engine used by THT Voice.
 *
 * Implementations must never perform network I/O. Audio is accepted as
 * mono PCM float samples normalized to [-1, 1] at [sampleRate].
 */
interface OfflineAsrEngine {
    val isReady: Boolean
    val sampleRate: Int

    /** Loads runtime + model resources. Safe to call more than once. */
    fun initialize(): AsrInitResult

    /** Starts a fresh utterance/stream. */
    fun startStream(): Boolean

    /** Supplies the next microphone chunk. */
    fun acceptWaveform(samples: FloatArray): Boolean

    /** Latest non-final text; may be blank. */
    fun partialText(): String

    /** True when the engine's endpoint detector considers the utterance complete. */
    fun isEndpoint(): Boolean

    /**
     * True when nothing that sounds like speech was heard for the no-speech window (owner 16/10/2026:
     * stop after ~4 s instead of the full listen time when the driver says nothing).
     */
    fun noSpeechTimedOut(): Boolean = false

    /**
     * Runs one throw-away decode right after [initialize] so the first real command does not pay
     * for the model's cold first run (owner report 21/10/2026: the first command after the car
     * starts was misheard and stuck on "Đang xử lý"). Must not touch the current stream.
     */
    fun warmUp() = Unit

    /**
     * Early look at a short pause (R02 1.3.0 "zero latency"): the text decoded once per pause, or
     * null (no pause yet, already looked at this pause, or the backend has partials anyway). The
     * caller ends listening only when this is a complete command; otherwise the normal endpoint
     * still applies.
     */
    fun earlyText(): String? = null

    /** Final text for the current utterance. */
    fun finishStream(): AsrRecognitionResult

    fun reset()
    fun close()
}

data class AsrInitResult(
    val ready: Boolean,
    val detail: String? = null
)

data class AsrRecognitionResult(
    val text: String,
    val final: Boolean,
    val detail: String? = null
)
