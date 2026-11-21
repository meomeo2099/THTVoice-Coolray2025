package com.voicegeely.app.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner report 19/11/2026: ordinary talk in the car woke THT Voice after 1.0.20. */
class WakeDefaultsTest {
    @Test
    fun lowKeepsThePre120Tuning() {
        // Thấp = the Coolray 2025 tuning before the sensitivity setting existed.
        assertEquals(9.0, WakeSensitivity.LOW.gateMarginDb, 0.0)
        assertEquals(0.25f, WakeSensitivity.LOW.keywordsThreshold, 0f)
        assertFalse(WakeSensitivity.LOW.acceptBlankConfirmation)
    }
}
