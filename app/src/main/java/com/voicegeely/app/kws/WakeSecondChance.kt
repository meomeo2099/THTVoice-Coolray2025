package com.voicegeely.app.kws

/**
 * "Cơ hội thứ hai" (R03 WakeDecider second chance, adapted to Sherpa which gives no score): a
 * short burst of speech the length of a wake phrase (0.4–1.6 s) that did NOT trigger is a likely
 * near miss — the driver called softly. For [WINDOW_MS] after it the spotter runs with lenient
 * lines, so calling again is enough; the Vietnamese confirmation still decides. A wake inside the
 * window is logged as a near miss for the Nhật ký. Pure Kotlin; feed gate state per chunk.
 */
class WakeSecondChance(private val clock: () -> Long) {
    companion object {
        const val MIN_BURST_MS = 400L
        const val MAX_BURST_MS = 1_600L
        const val WINDOW_MS = 4_000L
    }

    private var speechStartedAt = -1L
    private var lastSpeechAt = -1L
    private var windowUntil = 0L
    var nearMisses = 0
        private set

    val active: Boolean get() = clock() < windowUntil

    /** Returns true when a near miss just opened the lenient window. */
    fun onChunk(speech: Boolean, detected: Boolean, allowed: Boolean): Boolean {
        val now = clock()
        if (detected) { speechStartedAt = -1; windowUntil = 0; return false }
        if (speech) {
            if (speechStartedAt < 0) speechStartedAt = now
            lastSpeechAt = now
            return false
        }
        if (speechStartedAt >= 0 && now - lastSpeechAt >= 250) {
            val burst = lastSpeechAt - speechStartedAt
            speechStartedAt = -1
            if (allowed && burst in MIN_BURST_MS..MAX_BURST_MS && !active) {
                windowUntil = now + WINDOW_MS
                nearMisses++
                return true
            }
        }
        return false
    }

    fun close() { windowUntil = 0 }
}
