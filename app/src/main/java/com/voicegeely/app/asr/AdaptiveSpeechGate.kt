package com.voicegeely.app.asr

/** Bounded noise-floor estimate; learn only below the current speech threshold.
 * Never learn speech as background or reduce the user's configured baseline.
 */
class AdaptiveSpeechGate {
    private var noise = 0f
    fun reset() { noise = 0f }

    fun isSpeech(rms: Float, baseline: Float, samples: Int, sampleRate: Int): Boolean {
        if (!rms.isFinite()) return false
        val threshold = maxOf(baseline, noise * 2.5f).coerceAtMost(baseline * 2f)
        val speech = rms >= threshold
        if (!speech) {
            // Time-based EMA, independent of AudioRecord's partial-read/chunk size.
            val alpha = (1.0 - kotlin.math.exp(-samples.toDouble() / sampleRate / 0.6)).toFloat()
            noise += alpha * (rms - noise)
        }
        return speech
    }
}
