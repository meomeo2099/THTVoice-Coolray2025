package com.voicegeely.app.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 16/10/2026: speech after a decoded endpoint must be kept and decoded again. */
class OfflineEndpointTrackerTest {
    private val rate = 16_000
    private val config = AsrEndpointConfig(speechRmsThreshold = 0.012f, trailingSilenceMs = 500, minSpeechMs = 180, maxListenMs = 8_000)
    private fun tracker() = OfflineEndpointTracker(rate) { config }
    private fun speech(ms: Int) = FloatArray(rate * ms / 1000) { if (it % 2 == 0) 0.2f else -0.2f }
    private fun silence(ms: Int) = FloatArray(rate * ms / 1000)
    private fun OfflineEndpointTracker.feed(chunk: FloatArray) { chunk.toList().chunked(1600).forEach { accept(it.toFloatArray()) } }

    @Test fun endpointAfterSpeechAndSilence() {
        val t = tracker()
        t.feed(silence(300)); assertFalse(t.endpointReached())
        t.feed(speech(600)); assertFalse(t.endpointReached())
        t.feed(silence(400)); assertFalse("400 ms < 500 ms", t.endpointReached())
        t.feed(silence(100)); assertTrue(t.endpointReached())
    }

    @Test fun speechAfterDecodeIsKeptAndDecodedAgain() {
        val t = tracker()
        t.feed(speech(600)); t.feed(silence(500))
        assertTrue(t.endpointReached())
        t.markDecoded()
        assertTrue(t.decodedValid)
        val before = t.totalSamples
        t.feed(silence(800))                       // grace window after "rồi": still the same decode
        assertTrue(t.decodedValid)
        t.feed(speech(700))                        // "… tạm dừng nhạc"
        assertFalse("old decode is stale", t.decodedValid)
        assertFalse("still speaking", t.endpointReached())
        assertEquals(1, t.resumedCount)
        t.feed(silence(500))
        assertTrue(t.endpointReached())
        assertEquals("every sample kept, in order", t.totalSamples, t.audio().size.toLong())
        assertTrue(t.totalSamples > before)
    }

    @Test fun maxListenStopsAndCapsTheBuffer() {
        val t = tracker()
        t.feed(speech(9_000))
        assertTrue(t.endpointReached())
        assertEquals(rate * 8L, t.totalSamples)
    }

    @Test fun noSpeechNoEndpointUntilMax() {
        val t = tracker()
        t.feed(silence(3_000))
        assertFalse(t.endpointReached())
    }

    @Test fun noSpeechStopsAfterFourSeconds() {
        val t = tracker()
        t.feed(silence(3_900)); assertFalse(t.noSpeechTimedOut())
        t.feed(silence(100)); assertTrue(t.noSpeechTimedOut())
    }

    @Test fun anySpeechCancelsTheNoSpeechStop() {
        val t = tracker()
        t.feed(silence(2_000)); t.feed(speech(300)); t.feed(silence(3_000))
        assertFalse(t.noSpeechTimedOut())
    }
}
