package com.voicegeely.app.asr

/** Exact digital zero / no delivered frames, not ordinary quiet cabin audio. */
class MicSignalWatchdog(private val sampleRate: Int = 16_000, private val timeoutMs: Long = 2_000) {
    private var zeros = 0L
    private var lastFrameAt = -1L

    fun reset(nowMs: Long) { zeros = 0; lastFrameAt = nowMs }

    fun stalled(pcm: ShortArray, count: Int, nowMs: Long): Boolean {
        if (lastFrameAt < 0) reset(nowMs)
        if (count < 0) return true
        if (count == 0) return nowMs - lastFrameAt >= timeoutMs
        lastFrameAt = nowMs
        if ((0 until count).any { pcm[it] != 0.toShort() }) zeros = 0
        else zeros += count
        return zeros >= sampleRate * timeoutMs / 1000
    }
}
