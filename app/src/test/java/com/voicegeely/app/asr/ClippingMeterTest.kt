package com.voicegeely.app.asr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClippingMeterTest {
    @Test fun normalSpeechIsNotClipped() {
        val m = ClippingMeter()
        val pcm = ShortArray(16_000) { (if (it % 2 == 0) 12_000 else -12_000).toShort() }
        m.accept(pcm, pcm.size)
        assertFalse(m.clipped())
    }

    @Test fun manyFullScaleSamplesAreClipped() {
        val m = ClippingMeter()
        val pcm = ShortArray(16_000) { if (it % 100 == 0) Short.MAX_VALUE else 1_000 }
        m.accept(pcm, pcm.size)
        assertTrue(m.clipped())
        m.reset()
        assertFalse(m.clipped())
    }

    @Test fun aFewPeaksAreNotEnough() {
        val m = ClippingMeter()
        val pcm = ShortArray(16_000) { if (it < 10) Short.MIN_VALUE else 0 }
        m.accept(pcm, pcm.size)
        assertFalse(m.clipped())
    }
}
