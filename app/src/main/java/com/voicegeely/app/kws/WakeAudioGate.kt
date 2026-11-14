package com.voicegeely.app.kws

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/** Lightweight wake-only RMS/noise detector and dB gate; ASR endpointing stays separate. */
class WakeAudioGate(private val sampleRate: Int = 16_000, @Volatile var marginDb: Double = 9.0) {
    /** The last chunk was louder than the tracked noise (used by the "second chance" rule). */
    var lastSpeech = false
        private set
    data class Frame(val samples: FloatArray, val resetDecoder: Boolean)
    private val lead = WakePcmBuffer(sampleRate * 560 / 1000)
    internal var floorDb = Double.NaN
        private set
    private var noise = 60.0
    private var open = false
    private var quietMs = 0.0
    private var silenceMs = 0.0
    private var voicedMs = 0.0
    private var resetSent = false

    fun reset() {
        lead.clear(); floorDb = Double.NaN; noise = 60.0; open = false
        quietMs = 0.0; silenceMs = 0.0; voicedMs = 0.0; resetSent = false
    }

    fun accept(samples: FloatArray, nowMs: Long, gateEnabled: Boolean = true): Frame {
        if (samples.isEmpty()) return Frame(samples, false)
        val ms = samples.size * 1000.0 / sampleRate
        // Input is normalized Float PCM; reference thresholds use signed 16-bit units.
        val energy = samples.sumOf { it.toDouble() * it } / samples.size * 32768.0 * 32768.0
        val rms = sqrt(energy)
        val db = 10.0 * log10(energy + 1.0)
        if (db >= 3) floorDb = if (floorDb.isNaN()) db else minOf(db, floorDb + 0.05 * ms / 80.0)
        val threshold = maxOf(60.0, noise * maxOf(1.35, 2.5 - noise / 400.0))
        val speech = rms > threshold
        lastSpeech = speech
        if (speech) { voicedMs += ms; silenceMs = 0.0; resetSent = false }
        else {
            voicedMs = 0.0; silenceMs += ms
            val alpha = 1.0 - exp(-ms / 80.0 * -kotlin.math.ln(0.95))
            noise += alpha * (rms - noise)
        }
        val reset = !resetSent && (silenceMs >= 1500.0 || voicedMs >= 6000.0)
        if (reset) { resetSent = true; voicedMs = 0.0 }
        // Processed VOICE_RECOGNITION/COMMUNICATION audio must not be gated a second time.
        if (!gateEnabled) { lead.clear(); open = false; return Frame(samples, reset) }
        val pass = !floorDb.isNaN() && (floorDb >= 45.0 || db > floorDb + marginDb)
        if (!open) {
            if (!pass) { lead.add(samples); return Frame(FloatArray(0), reset) }
            open = true; quietMs = 0.0
            return Frame(lead.drainWith(samples), reset)
        }
        quietMs = if (pass) 0.0 else quietMs + ms
        if (quietMs >= 800.0) { open = false; quietMs = 0.0; lead.clear() }
        return Frame(samples, reset)
    }
}
