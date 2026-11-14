package com.voicegeely.app.tts

/**
 * Joins decoded reply clips into one continuous PCM buffer (owner request 13/11/2026: stitched MP3
 * replies stuttered — every clip had its own player plus the encoder's silence at both ends).
 * Pure: trims the near-silent head/tail of each clip and puts a short, fixed pause between clips.
 */
object PcmJoin {
    const val SILENCE_LEVEL = 350          // |sample| below this counts as silence (16-bit)
    const val KEEP_HEAD_MS = 8
    const val KEEP_TAIL_MS = 25
    const val WORD_GAP_MS = 35             // between phrase pieces / numbers
    const val SENTENCE_GAP_MS = 220        // after a clip that ends a sentence

    fun trim(pcm: ShortArray, sampleRate: Int): ShortArray {
        require(sampleRate in 8000..96000)
        if (pcm.isEmpty()) return pcm
        var start = 0
        while (start < pcm.size && kotlin.math.abs(pcm[start].toInt()) < SILENCE_LEVEL) start++
        var end = pcm.size - 1
        while (end > start && kotlin.math.abs(pcm[end].toInt()) < SILENCE_LEVEL) end--
        if (start >= pcm.size) return ShortArray(0)
        val from = maxOf(0, start - sampleRate * KEEP_HEAD_MS / 1000)
        val to = minOf(pcm.size, end + 1 + sampleRate * KEEP_TAIL_MS / 1000)
        return pcm.copyOfRange(from, to)
    }

    /** [sentenceEnds]: true when the clip ends a sentence (longer pause after it). */
    fun join(clips: List<ShortArray>, sentenceEnds: List<Boolean>, sampleRate: Int): ShortArray {
        val parts = clips.map { trim(it, sampleRate) }
        val gaps = parts.indices.map { i ->
            if (i == parts.lastIndex) 0 else sampleRate * (if (sentenceEnds.getOrElse(i) { false }) SENTENCE_GAP_MS else WORD_GAP_MS) / 1000
        }
        val total = parts.sumOf { it.size.toLong() } + gaps.sumOf { it.toLong() }
        require(total <= 2_500_000)
        val out = ShortArray(total.toInt())
        var pos = 0
        parts.forEachIndexed { i, p ->
            System.arraycopy(p, 0, out, pos, p.size); pos += p.size + gaps[i]
        }
        return out
    }
}
