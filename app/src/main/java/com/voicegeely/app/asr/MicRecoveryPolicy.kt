package com.voicegeely.app.asr

/** Retry budget belongs to a consecutive fault sequence, not the service lifetime. */
class MicRecoveryPolicy(private val sampleRate: Int = 16_000) {
    private var retries = 0
    private var healthySamples = 0L
    private var graceUntil = 0L

    fun reopened(nowMs: Long) { healthySamples = 0; graceUntil = nowMs + 400 }

    /** Ignore watchdog during 400 ms of driver settling; audio still reaches the recognizer. */
    fun observe(pcm: ShortArray, count: Int, nowMs: Long): Boolean {
        if (nowMs < graceUntil) return false
        if (count <= 0) { healthySamples = 0; return true }
        if ((0 until count).any { pcm[it] != 0.toShort() }) {
            healthySamples += count
            if (healthySamples >= sampleRate * 4L) retries = 0
        } else healthySamples = 0
        return true
    }

    fun tryRecover(): Boolean {
        healthySamples = 0
        if (retries >= 2) return false
        retries++
        return true
    }
}
