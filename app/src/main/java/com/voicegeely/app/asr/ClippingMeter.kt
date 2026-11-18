package com.voicegeely.app.asr

/**
 * R02 1.3.0 (REF-15): a voice too close / too loud clips the 16-bit samples and the recogniser
 * hears noise. Counts samples at full scale during a session; [clipped] = enough of them that the
 * driver should be told to speak further from the microphone.
 */
class ClippingMeter {
    private var total = 0L
    private var atLimit = 0L

    fun reset() { total = 0L; atLimit = 0L }

    fun accept(pcm: ShortArray, count: Int) {
        for (i in 0 until count) {
            val v = pcm[i].toInt()
            if (v >= LIMIT || v <= -LIMIT) atLimit++
        }
        total += count
    }

    fun clipped(): Boolean = atLimit >= MIN_SAMPLES && atLimit * 1_000L >= total * PER_MILLE

    companion object {
        const val LIMIT = 32_600
        const val MIN_SAMPLES = 80L
        /** 2 ‰ of the audio at full scale. */
        const val PER_MILLE = 2L
        const val MESSAGE = "Tiếng bị vỡ do nói quá to. Bạn nói xa micrô hơn một chút nhé."
    }
}
