package com.voicegeely.app.kws

data class WakeWordInitResult(
    val ready: Boolean,
    val detail: String? = null
)

data class WakeWordDetection(
    val keyword: String,
    val detectedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Small local-only contract used by the foreground voice service.
 * Implementations must never upload microphone audio or recognized wake data.
 */
interface OfflineWakeWordEngine {
    val sampleRate: Int
    val isReady: Boolean

    fun initialize(): WakeWordInitResult
    fun startStream(): Boolean
    fun acceptWaveform(samples: FloatArray): WakeWordDetection?
    fun reset()
    fun close()
}
