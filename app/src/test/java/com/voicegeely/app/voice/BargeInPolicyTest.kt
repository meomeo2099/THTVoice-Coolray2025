package com.voicegeely.app.voice

import com.voicegeely.app.kws.VoiceMicState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BargeInPolicyTest {
    @Test
    fun pressDuringAnswerListensAgain() {
        assertTrue(BargeInPolicy.shouldBargeIn(VoiceMicState.PROCESSING))
    }

    @Test
    fun pressWhileListeningOrIdleIsNotBargeIn() {
        assertFalse(BargeInPolicy.shouldBargeIn(VoiceMicState.ASR_LISTENING))
        assertFalse(BargeInPolicy.shouldBargeIn(VoiceMicState.IDLE))
        assertFalse(BargeInPolicy.shouldBargeIn(VoiceMicState.KWS_LISTENING))
        assertFalse(BargeInPolicy.shouldBargeIn(VoiceMicState.CLOSED))
    }
}
