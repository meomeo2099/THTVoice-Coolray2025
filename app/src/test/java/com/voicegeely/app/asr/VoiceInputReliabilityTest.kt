package com.voicegeely.app.asr

import org.junit.Assert.*
import org.junit.Test

class VoiceInputReliabilityTest {
    @Test fun preRollIsOrderedBoundedSingleUseAndExpires() {
        val ring = PcmPreRoll(4)
        ring.add(floatArrayOf(1f, 2f, 3f), 100)
        ring.add(floatArrayOf(4f, 5f), 200)
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f), ring.take(300), 0f)
        assertEquals(0, ring.take(300).size)
        ring.add(floatArrayOf(8f), 400)
        assertEquals(0, ring.take(1_401).size)
        ring.add(floatArrayOf(9f), 1_500)
        ring.clear()
        assertEquals(0, ring.take(1_501).size)
    }
    @Test fun digitalZeroTriggersAfterTwoSecondsButQuietNoiseDoesNot() {
        val w = MicSignalWatchdog()
        w.reset(0)
        repeat(19) { assertFalse(w.stalled(ShortArray(1600), 1600, it * 100L)) }
        assertTrue(w.stalled(ShortArray(1600), 1600, 2_000))
        w.reset(3_000)
        repeat(50) { assertFalse(w.stalled(ShortArray(1600) { 1 }, 1600, 3_000 + it * 100L)) }
    }
    @Test fun missingFramesAndDriverErrorAreDetected() {
        val w = MicSignalWatchdog()
        w.reset(1_000)
        assertFalse(w.stalled(shortArrayOf(), 0, 2_999))
        assertTrue(w.stalled(shortArrayOf(), 0, 3_000))
        w.reset(4_000)
        assertTrue(w.stalled(shortArrayOf(), -6, 4_001))
    }
    @Test fun oneNonZeroFrameResetsTheZeroRun() {
        val w = MicSignalWatchdog()
        repeat(19) { w.stalled(ShortArray(1600), 1600, it * 100L) }
        assertFalse(w.stalled(shortArrayOf(1), 1, 1_901))
        assertFalse(w.stalled(ShortArray(1600), 1600, 2_001))
    }
    @Test fun learnsFanNoiseWithoutLearningLoudSpeech() {
        val g = AdaptiveSpeechGate()
        repeat(40) { assertFalse(g.isSpeech(0.008f, 0.012f, 1600, 16000)) }
        assertFalse("fan fluctuation below adapted threshold", g.isSpeech(0.015f, 0.012f, 1600, 16000))
        repeat(40) { assertTrue(g.isSpeech(0.04f, 0.012f, 1600, 16000)) }
        g.reset()
        assertTrue("reset restores configured baseline", g.isSpeech(0.015f, 0.012f, 1600, 16000))
    }
    @Test fun fanNoiseThenSpeechThenSilenceReachesEndpoint() {
        val t = OfflineEndpointTracker(16000) { AsrEndpointConfig() }
        repeat(20) { t.accept(FloatArray(1600) { 0.008f }) }
        assertFalse(t.endpointReached())
        repeat(5) { t.accept(FloatArray(1600) { 0.04f }) }
        repeat(7) { t.accept(FloatArray(1600) { 0.008f }) }
        assertTrue(t.endpointReached())
        t.reset()
        assertEquals(0, t.audio().size)
        assertFalse(t.endpointReached())
    }
}
