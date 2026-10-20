package com.voicegeely.app.tts

/**
 * Owner report 12/10/2026 (video of "Thử giọng"): a short extra sound after the sentence. Kokoro
 * sometimes adds a brief burst after the final word, separated from it by a pause. This removes
 * that tail only when it is short ([MAX_TAIL_MS]) and follows a clear pause ([MIN_GAP_MS]); a real
 * last word after a comma is longer. It also drops trailing silence and fades out the last
 * [FADE_MS] so the reply never ends with a click. Speech before the tail is never changed.
 */
object TtsTailTrim {
    const val FRAME_MS = 10
    const val MIN_GAP_MS = 150
    const val MAX_TAIL_MS = 200
    const val KEEP_AFTER_MS = 60
    const val END_TOUCH_MS = 40
    const val MIN_GAP_AT_END_MS = 100
    const val MAX_TAIL_AT_END_MS = 320
    const val FADE_MS = 15
    private const val SOUND_RATIO = 0.08f

    fun trim(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty() || sampleRate <= 0) return samples
        val frame = (sampleRate * FRAME_MS / 1000).coerceAtLeast(1)
        val frames = (samples.size + frame - 1) / frame
        val rms = FloatArray(frames) { f ->
            var sum = 0.0
            val start = f * frame
            val end = minOf(samples.size, start + frame)
            for (i in start until end) sum += samples[i] * samples[i]
            kotlin.math.sqrt(sum / (end - start)).toFloat()
        }
        val peak = rms.maxOrNull() ?: return samples
        if (peak <= 0f) return samples
        val sound = BooleanArray(frames) { rms[it] >= peak * SOUND_RATIO }
        var last = sound.lastIndexOf(true)
        if (last < 0) return samples

        // Final burst: sound frames back from `last` (gaps under 40 ms belong to the same burst).
        var start = last
        var silentRun = 0
        var i = last - 1
        while (i >= 0) {
            if (sound[i]) { start = i; silentRun = 0 } else if (++silentRun * FRAME_MS >= 40) break
            i--
        }
        // Gap before the burst, and is there speech before it at all?
        var prev = start - 1
        while (prev >= 0 && !sound[prev]) prev--
        val gapMs = (start - prev - 1) * FRAME_MS
        val tailMs = (last - start + 1) * FRAME_MS
        // Measured with the real pack (tools/tts-probe, 12/10/2026): the extra sound sits at the very
        // end of the buffer, 70–110 ms long after a ~200 ms pause, while a real last word is
        // followed by trailing silence. A burst touching the buffer end may be a bit longer.
        val touchesEnd = (frames - 1 - last) * FRAME_MS <= END_TOUCH_MS
        val artifact = prev >= 0 && (
            (gapMs >= MIN_GAP_MS && tailMs <= MAX_TAIL_MS) ||
                (touchesEnd && gapMs >= MIN_GAP_AT_END_MS && tailMs <= MAX_TAIL_AT_END_MS)
            )
        if (artifact) last = prev

        val endSample = minOf(samples.size, (last + 1) * frame + sampleRate * KEEP_AFTER_MS / 1000)
        val out = samples.copyOf(endSample)
        val fade = minOf(out.size, sampleRate * FADE_MS / 1000)
        for (k in 0 until fade) {
            val idx = out.size - fade + k
            out[idx] = out[idx] * (1f - (k + 1).toFloat() / fade)
        }
        return out
    }
}
