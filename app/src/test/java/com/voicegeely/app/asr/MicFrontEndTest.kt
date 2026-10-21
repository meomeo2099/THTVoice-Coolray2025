package com.voicegeely.app.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicFrontEndTest {
    @Test fun quietSpeechIsAmplifiedLinearly() {
        val out = MicFrontEnd.applyGain(floatArrayOf(0.1f, -0.2f, 0.3f), 2f)
        assertEquals(0.2f, out[0], 1e-6f)
        assertEquals(-0.4f, out[1], 1e-6f)
        assertEquals(0.6f, out[2], 1e-6f)
    }

    @Test fun loudSpeechIsSoftLimitedNeverClipped() {
        val out = MicFrontEnd.applyGain(floatArrayOf(0.5f, -0.9f, 1f), 2f)
        assertTrue(out.all { kotlin.math.abs(it) < 1f })
        assertTrue(out[0] > MicFrontEnd.KNEE && out[2] > out[0])
        assertTrue(out[1] < -MicFrontEnd.KNEE)
    }

    @Test fun unityGainIsPassThroughAndLimiterIsContinuousAtKnee() {
        val input = floatArrayOf(0.95f, -0.3f)
        assertTrue(MicFrontEnd.applyGain(input, 1f) === input)
        assertEquals(MicFrontEnd.KNEE, MicFrontEnd.softLimit(MicFrontEnd.KNEE), 1e-6f)
        assertEquals(MicFrontEnd.KNEE, MicFrontEnd.softLimit(MicFrontEnd.KNEE + 1e-6f), 1e-4f)
    }

    @Test fun missingAssetsFallBackToGainOnly() {
        val fe = MicFrontEnd.create(null, 16_000)
        assertEquals(false, fe.denoiseActive)
        assertEquals(0.4f, fe.process(floatArrayOf(0.2f))[0], 1e-6f)
        assertEquals(0, fe.finish().size)
    }
}
