package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupPolicyTest {
    @Test
    fun waitsRightAfterBootAtMostTwentySeconds() {
        assertEquals(20_000L, StartupPolicy.loadDelayMs(uptimeMs = 10_000L))
        assertEquals(5_000L, StartupPolicy.loadDelayMs(uptimeMs = 40_000L))
    }

    @Test
    fun loadsAtOnceWhenTheUnitHasBeenUpForAWhile() {
        assertEquals(0L, StartupPolicy.loadDelayMs(uptimeMs = 45_000L))
        assertEquals(0L, StartupPolicy.loadDelayMs(uptimeMs = 3_600_000L))
    }
}
