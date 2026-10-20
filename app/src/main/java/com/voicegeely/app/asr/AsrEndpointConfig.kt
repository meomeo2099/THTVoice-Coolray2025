package com.voicegeely.app.asr

/**
 * Runtime VAD/endpoint tuning for the local Sherpa ASR path.
 *
 * Defaults intentionally preserve the first head-unit baseline (inherited from the base THT Voice build). Values are bounded so a bad
 * preference cannot effectively disable speech detection or leave the microphone open forever.
 * Hardware tuning still requires real cabin measurements; these settings are not a claim that
 * any non-default profile has been validated on the vehicle.
 */
data class AsrEndpointConfig(
    val speechRmsThreshold: Float = DEFAULT_SPEECH_RMS,
    val trailingSilenceMs: Long = DEFAULT_TRAILING_SILENCE_MS,
    val minSpeechMs: Long = DEFAULT_MIN_SPEECH_MS,
    val maxListenMs: Long = DEFAULT_MAX_LISTEN_MS
) {
    fun normalized(): AsrEndpointConfig = copy(
        speechRmsThreshold = speechRmsThreshold.coerceIn(MIN_SPEECH_RMS, MAX_SPEECH_RMS),
        trailingSilenceMs = trailingSilenceMs.coerceIn(MIN_TRAILING_SILENCE_MS, MAX_TRAILING_SILENCE_MS),
        minSpeechMs = minSpeechMs.coerceIn(MIN_MIN_SPEECH_MS, MAX_MIN_SPEECH_MS),
        maxListenMs = maxListenMs.coerceIn(MIN_MAX_LISTEN_MS, MAX_MAX_LISTEN_MS)
    )

    companion object {
        const val DEFAULT_SPEECH_RMS = 0.012f
        const val DEFAULT_TRAILING_SILENCE_MS = 700L
        const val DEFAULT_MIN_SPEECH_MS = 180L
        const val DEFAULT_MAX_LISTEN_MS = 12_000L
        /** Owner 16/10/2026: nothing said for 4 s → stop listening ("Không nghe rõ"). */
        const val DEFAULT_NO_SPEECH_MS = 4_000L

        const val MIN_SPEECH_RMS = 0.004f
        const val MAX_SPEECH_RMS = 0.030f
        const val MIN_TRAILING_SILENCE_MS = 350L
        const val MAX_TRAILING_SILENCE_MS = 1_600L
        const val MIN_MIN_SPEECH_MS = 120L
        const val MAX_MIN_SPEECH_MS = 600L
        const val MIN_MAX_LISTEN_MS = 4_000L
        const val MAX_MAX_LISTEN_MS = 12_000L
    }
}

/** Process-local source used by both Sherpa backends. */
object AsrEndpointRuntime {
    @Volatile
    private var current: AsrEndpointConfig = AsrEndpointConfig()

    fun snapshot(): AsrEndpointConfig = current

    fun update(config: AsrEndpointConfig) {
        current = config.normalized()
    }

    internal fun resetForTests() {
        current = AsrEndpointConfig()
    }
}
