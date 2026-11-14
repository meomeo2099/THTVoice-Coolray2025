package com.voicegeely.app.kws

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Cuts one enrollment take out of the mic stream: waits for speech (energy above the noise heard
 * just after the beep), keeps a little audio before it, and ends after a short silence or a cap.
 * Feed 16 kHz mono chunks; [done] then [take] gives the PCM.
 */
class WakeTakeSegmenter(
    private val sampleRate: Int = 16_000,
    private val marginDb: Double = 10.0,
    private val maxSpeechMs: Int = 2_500,
    private val endSilenceMs: Int = 450,
    private val waitMs: Int = 4_000
) {
    private val buf = ArrayList<Short>()
    private var calibratedMs = 0.0
    private var floorDb = Double.NaN
    private var started = false
    private var speechStart = 0
    private var lastLoud = 0
    private var elapsedMs = 0.0
    var done = false
        private set
    var timedOut = false
        private set

    /** Called before the start cue: never discard calibration frames from the spoken take. */
    fun finishCalibration(): Boolean {
        if (floorDb.isNaN() || calibratedMs < 250) return false
        calibratedMs = 250.0
        buf.clear()
        elapsedMs = 0.0
        return true
    }

    fun feed(pcm: ShortArray, count: Int) {
        if (done || count <= 0) return
        val ms = count * 1000.0 / sampleRate
        elapsedMs += ms
        var e = 0.0
        for (i in 0 until count) { val v = pcm[i].toDouble(); e += v * v }
        val db = 10 * log10(e / count + 1.0)
        if (calibratedMs < 250) {
            floorDb = if (floorDb.isNaN()) db else minOf(floorDb, db)
            calibratedMs += ms
            return
        }
        for (i in 0 until count) buf += pcm[i]
        val loud = db > floorDb + marginDb && sqrt(e / count) > 300
        val pos = buf.size
        if (loud) {
            if (!started) { started = true; speechStart = maxOf(0, pos - count - sampleRate / 5) }
            lastLoud = pos
        }
        if (started) {
            val sinceLoudMs = (pos - lastLoud) * 1000.0 / sampleRate
            val speechMs = (pos - speechStart) * 1000.0 / sampleRate
            if (sinceLoudMs >= endSilenceMs || speechMs >= maxSpeechMs) done = true
        } else if (elapsedMs >= waitMs) { done = true; timedOut = true }
    }

    /** The take (empty when nothing was said). */
    fun take(): ShortArray {
        if (!started) return ShortArray(0)
        val end = minOf(buf.size, lastLoud + sampleRate / 4)
        return ShortArray(end - speechStart) { buf[speechStart + it] }
    }
}
