package com.voicegeely.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWatchdogPolicyTest {
    @Test fun normalSessionIsNotStuck() {
        assertFalse(SessionWatchdogPolicy.sessionStuck(true, 1_000L, 1_000L + 20_000L))
        assertFalse(SessionWatchdogPolicy.sessionStuck(false, 1_000L, 1_000L + 600_000L))
        assertFalse(SessionWatchdogPolicy.sessionStuck(true, 0L, 600_000L))
    }

    @Test fun hungSessionIsStuck() {
        assertTrue(SessionWatchdogPolicy.sessionStuck(true, 1_000L, 1_000L + 36_000L))
    }

    @Test fun stuckLimitCoversListeningPlusCommandTimeout() {
        assertTrue(SessionWatchdogPolicy.STUCK_AFTER_MS > 12_000L + SessionWatchdogPolicy.COMMAND_TIMEOUT_MS)
        assertTrue(SessionWatchdogPolicy.commandTimedOut(SessionWatchdogPolicy.COMMAND_TIMEOUT_MS))
        assertFalse(SessionWatchdogPolicy.commandTimedOut(3_000L))
    }
}
