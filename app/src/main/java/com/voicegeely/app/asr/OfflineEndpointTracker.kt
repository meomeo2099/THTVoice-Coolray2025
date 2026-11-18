package com.voicegeely.app.asr

import kotlin.math.sqrt

/**
 * Pure audio buffer + energy endpoint for the offline (whole-utterance) Sherpa recogniser.
 *
 * 16/10/2026 fix: the old engine stopped taking audio once it had decoded at the first endpoint,
 * so the 2.5 s "keep listening after a joining word" window (UtteranceContinuation) never got the
 * rest of the sentence ("mở youtube rồi … tạm dừng nhạc" lost "tạm dừng nhạc"). Now every chunk
 * is kept; speech after a decode makes that decode stale, and the next endpoint decodes the whole
 * utterance again from the start.
 */
class OfflineEndpointTracker(
    private val sampleRate: Int,
    private val configProvider: () -> AsrEndpointConfig
) {
    private val chunks = ArrayList<FloatArray>()
    private val speechGate = AdaptiveSpeechGate()
    var totalSamples = 0L
        private set
    private var speechSeen = false
    /** Samples counted as speech so far; grows only while the driver talks. */
    var speechSamples = 0L
        private set
    private var trailingSilenceSamples = 0L

    /** True while the last decode still covers all speech heard. */
    var decodedValid = false
        private set
    /** How many times speech came back after a decode (diagnostics). */
    var resumedCount = 0
        private set

    fun reset() {
        chunks.clear()
        speechGate.reset()
        totalSamples = 0L
        speechSeen = false
        speechSamples = 0L
        trailingSilenceSamples = 0L
        decodedValid = false
        resumedCount = 0
    }

    fun accept(samples: FloatArray) {
        if (samples.isEmpty()) return
        val config = configProvider().normalized()
        if (totalSamples >= config.maxListenMs * sampleRate / 1000L) return
        chunks += samples.copyOf()
        totalSamples += samples.size
        if (speechGate.isSpeech(rms(samples), config.speechRmsThreshold, samples.size, sampleRate)) {
            if (decodedValid) {
                decodedValid = false
                resumedCount++
            }
            speechSeen = true
            speechSamples += samples.size
            trailingSilenceSamples = 0L
        } else if (speechSeen) {
            trailingSilenceSamples += samples.size
        }
    }

    fun endpointReached(): Boolean {
        val config = configProvider().normalized()
        if (totalSamples >= config.maxListenMs * sampleRate / 1000L) return true
        if (!speechSeen) return false
        val minSpeech = config.minSpeechMs * sampleRate / 1000L
        val silence = config.trailingSilenceMs * sampleRate / 1000L
        return speechSamples >= minSpeech && trailingSilenceSamples >= silence
    }

    /** No speech at all for [AsrEndpointConfig.DEFAULT_NO_SPEECH_MS] (never longer than max listen). */
    fun noSpeechTimedOut(): Boolean {
        if (hasSpeechForDecode()) return false
        val config = configProvider().normalized()
        val limitMs = minOf(AsrEndpointConfig.DEFAULT_NO_SPEECH_MS, config.maxListenMs)
        return totalSamples >= limitMs * sampleRate / 1000L
    }

    fun markDecoded() { decodedValid = true }

    /** A timeout is not evidence of speech: never decode silence or a short noise click (Coolray 2025). */
    fun hasSpeechForDecode(): Boolean = speechSeen &&
        speechSamples >= configProvider().normalized().minSpeechMs * sampleRate / 1000L

    /** Enough speech, then a pause of at least [pauseMs] (shorter than the real endpoint). */
    fun pauseReached(pauseMs: Long): Boolean {
        if (!speechSeen) return false
        val config = configProvider().normalized()
        return speechSamples >= config.minSpeechMs * sampleRate / 1000L &&
            trailingSilenceSamples >= pauseMs * sampleRate / 1000L
    }

    /** The whole utterance so far, in order. */
    fun audio(): FloatArray {
        val out = FloatArray(totalSamples.toInt())
        var pos = 0
        for (c in chunks) { c.copyInto(out, pos); pos += c.size }
        return out
    }

    companion object {
        fun rms(samples: FloatArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (sample in samples) sum += sample.toDouble() * sample.toDouble()
            return sqrt(sum / samples.size).toFloat()
        }
    }
}
