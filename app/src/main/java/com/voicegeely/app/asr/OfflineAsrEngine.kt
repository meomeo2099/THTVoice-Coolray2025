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
