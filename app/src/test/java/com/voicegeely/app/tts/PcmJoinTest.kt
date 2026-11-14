package com.voicegeely.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmJoinTest {
    @Test(expected = IllegalArgumentException::class) fun invalidRateRejected() { PcmJoin.trim(shortArrayOf(500), 0) }
    @Test(expected = IllegalArgumentException::class) fun outputIsBounded() { PcmJoin.join(listOf(ShortArray(2_500_001) { 500 }), emptyList(), 24000) }
    private val rate = 24_000
    private fun clip(silenceHead: Int, sound: Int, silenceTail: Int) =
        ShortArray(silenceHead) { 0 } + ShortArray(sound) { 5000 } + ShortArray(silenceTail) { 10 }

    @Test fun trimKeepsShortMargins() {
        val t = PcmJoin.trim(clip(2400, 1000, 4800), rate)
        val head = rate * PcmJoin.KEEP_HEAD_MS / 1000
        val tail = rate * PcmJoin.KEEP_TAIL_MS / 1000
        assertEquals(head + 1000 + tail, t.size)
        assertEquals(0, PcmJoin.trim(ShortArray(500) { 3 }, rate).size)
    }

    @Test fun joinUsesWordAndSentenceGaps() {
        val a = clip(1200, 1000, 1200)
        val b = clip(1200, 2000, 1200)
        val c = clip(0, 500, 0)
        val joined = PcmJoin.join(listOf(a, b, c), listOf(false, true, false), rate)
        val trimmed = listOf(a, b, c).sumOf { PcmJoin.trim(it, rate).size }
        val gaps = rate * PcmJoin.WORD_GAP_MS / 1000 + rate * PcmJoin.SENTENCE_GAP_MS / 1000
        assertEquals(trimmed + gaps, joined.size)
        // Pieces of one sentence lose their padding: shorter than playing them back to back.
        val pieces = PcmJoin.join(listOf(a, b), listOf(false, false), rate)
        assertTrue(pieces.size < a.size + b.size)
    }
}
