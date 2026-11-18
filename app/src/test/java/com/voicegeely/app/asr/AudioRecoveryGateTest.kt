package com.voicegeely.app.asr

import com.voicegeely.app.kws.WakeAudioGate
import org.junit.Assert.*
import org.junit.Test

class AudioRecoveryGateTest {
    @Test fun recoveryHasTwoRetriesAndFourSecondsOfSignalRestoreBudget() {
        val r = MicRecoveryPolicy()
        assertTrue(r.tryRecover()); assertTrue(r.tryRecover()); assertFalse(r.tryRecover())
        repeat(49) { r.observe(ShortArray(1280) { 1 }, 1280, 1_000L + it * 80) }
        r.observe(ShortArray(1280) { 1 }, 1280, 5_000)
        assertTrue(r.tryRecover())
    }
    @Test fun silenceCannotRestoreBudgetAndGraceExpiresWithoutFrames() {
        val r = MicRecoveryPolicy()
        r.tryRecover(); r.tryRecover()
        repeat(100) { r.observe(ShortArray(1280), 1280, it * 80L) }
        assertFalse(r.tryRecover())
        r.reopened(10_000)
        assertFalse(r.observe(shortArrayOf(), 0, 10_399))
        assertTrue(r.observe(shortArrayOf(), 0, 10_400))
        assertTrue(r.observe(shortArrayOf(), -1, 10_401))
    }
    @Test fun leadSelectionKeepsNewestSamplesAndConsumesOnce() {
        val r = PcmPreRoll()
        r.add(FloatArray(40_000) { it.toFloat() }, 100)
        val tail = r.take(101, 6_400)
        assertEquals(6400, tail.size)
        assertEquals(33600f, tail.first(), 0f)
        assertEquals(39999f, tail.last(), 0f)
        assertTrue(r.take(101).isEmpty())
        r.add(FloatArray(32_000), 200)
        assertEquals(32000, r.take(200).size)
    }
    @Test fun gateRetainsLeadAndHangoverWithoutDuplicatingCurrentFrame() {
        val gate = WakeAudioGate()
        repeat(10) { assertTrue(gate.accept(FloatArray(1280) { 0.0003f }, it * 80L).samples.isEmpty()) }
        val opened = gate.accept(FloatArray(1280) { 0.04f }, 800)
        assertEquals(10240, opened.samples.size)
        assertEquals(1280, opened.samples.count { it == 0.04f })
        repeat(10) { assertEquals(1280, gate.accept(FloatArray(1280) { 0.0003f }, 880 + it * 80L).samples.size) }
        assertTrue(gate.accept(FloatArray(1280) { 0.0003f }, 1680).samples.isEmpty())
        gate.reset()
        assertTrue(gate.accept(FloatArray(1280), 1760).samples.isEmpty())
    }
    @Test fun quietDecoderResetIsOneShotUntilSpeechReturns() {
        val gate = WakeAudioGate()
        var resets = 0
        repeat(60) { if (gate.accept(FloatArray(1280), it * 80L).resetDecoder) resets++ }
        assertEquals(1, resets)
        gate.accept(FloatArray(1280) { 0.1f }, 5000)
        repeat(20) { if (gate.accept(FloatArray(1280), 5080 + it * 80L).resetDecoder) resets++ }
        assertEquals(2, resets)
    }
    @Test fun gateDurationsAreIndependentOfPartialReads() {
        val gate = WakeAudioGate()
        repeat(80) { gate.accept(FloatArray(160) { 0.0003f }, it * 10L) }
        assertEquals(9120, gate.accept(FloatArray(160) { 0.05f }, 800).samples.size)
        repeat(80) { assertEquals(160, gate.accept(FloatArray(160), 810 + it * 10L).samples.size) }
        assertTrue(gate.accept(FloatArray(160), 1610).samples.isEmpty())
    }

    @Test fun noiseFloorRisesSlowlyAndDropsImmediately() {
        val gate = WakeAudioGate()
        gate.accept(FloatArray(1280) { 0.0003f }, 0)
        val initial = gate.floorDb
        repeat(20) { gate.accept(FloatArray(1280) { 0.003f }, 80L + it * 80) }
        assertEquals(initial + 1.0, gate.floorDb, 0.00001)
        gate.accept(FloatArray(1280) { 0.0001f }, 1700)
        assertTrue(gate.floorDb < initial)
    }

    @Test fun processedMicBypassesGateEvenForQuietFirstWord() {
        val gate = WakeAudioGate()
        val quietSpeech = FloatArray(1280) { 0.0003f }
        repeat(30) {
            assertArrayEquals(quietSpeech, gate.accept(quietSpeech, it * 80L, gateEnabled = false).samples, 0f)
        }
    }
}
