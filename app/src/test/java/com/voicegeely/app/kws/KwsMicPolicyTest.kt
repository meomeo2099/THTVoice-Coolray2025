package com.voicegeely.app.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KwsMicPolicyTest {
    @Test fun yieldsDuringCallsAndWhenSilenced() {
        assertTrue(KwsMicPolicy.shouldYield(KwsMicPolicy.MODE_IN_CALL, false))
        assertTrue(KwsMicPolicy.shouldYield(KwsMicPolicy.MODE_IN_COMMUNICATION, false))
        assertTrue(KwsMicPolicy.shouldYield(KwsMicPolicy.MODE_RINGTONE, false))
        assertTrue(KwsMicPolicy.shouldYield(KwsMicPolicy.MODE_NORMAL, true))
        assertFalse(KwsMicPolicy.shouldYield(KwsMicPolicy.MODE_NORMAL, false))
    }

    @Test fun retryDelayGrowsAndIsBounded() {
        assertEquals(2_000L, KwsMicPolicy.retryDelayMs(0))
        assertEquals(5_000L, KwsMicPolicy.retryDelayMs(1))
        assertEquals(30_000L, KwsMicPolicy.retryDelayMs(3))
        assertEquals(60_000L, KwsMicPolicy.retryDelayMs(50))
    }
}
