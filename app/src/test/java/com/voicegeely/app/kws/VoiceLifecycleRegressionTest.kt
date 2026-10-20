package com.voicegeely.app.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the production KWS -> ASR -> processing -> KWS lifecycle. */
class VoiceLifecycleRegressionTest {
    @Test
    fun ecarxStyleTriggerHandsMicFromKwsToAsrWithoutOverlap() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginKws())
        assertEquals(VoiceMicState.KWS_LISTENING, state.state)

        // VoiceAssistantService stops KWS and waits for AudioRecord release first.
        state.endKws()
        assertEquals(VoiceMicState.IDLE, state.state)
        assertTrue(state.beginAsr())
        assertEquals(VoiceMicState.ASR_LISTENING, state.state)

        // A second hardware-key trigger must not acquire another ASR owner.
        assertFalse(state.beginAsr())
        assertFalse(state.beginKws())
    }

    @Test
    fun processingBlocksSecondVoiceSessionUntilResultCompletes() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginAsr())
        state.beginProcessing()
        assertEquals(VoiceMicState.PROCESSING, state.state)

        assertFalse(state.beginAsr())
        assertFalse(state.beginKws())

        state.endProcessing()
        assertEquals(VoiceMicState.IDLE, state.state)
        assertTrue(state.beginKws())
    }

    @Test
    fun wakeWordAndHardwareKeyCyclesCanRepeatWithoutLeakingMicOwnership() {
        val state = VoiceMicStateMachine()

        repeat(100) {
            assertTrue("cycle=$it KWS acquire", state.beginKws())
            assertEquals(VoiceMicState.KWS_LISTENING, state.state)

            state.endKws()
            assertTrue("cycle=$it ASR acquire", state.beginAsr())
            assertEquals(VoiceMicState.ASR_LISTENING, state.state)

            state.beginProcessing()
            assertEquals(VoiceMicState.PROCESSING, state.state)
            state.endProcessing()
            assertEquals(VoiceMicState.IDLE, state.state)
        }

        assertTrue(state.beginKws())
    }

    @Test
    fun serviceCloseDuringAnyActiveOwnerIsFailClosedAndCannotRearm() {
        val kws = VoiceMicStateMachine()
        assertTrue(kws.beginKws())
        kws.close()
        kws.endKws()
        assertEquals(VoiceMicState.CLOSED, kws.state)
        assertFalse(kws.beginAsr())
        assertFalse(kws.beginKws())

        val asr = VoiceMicStateMachine()
        assertTrue(asr.beginAsr())
        asr.close()
        asr.endProcessing()
        assertEquals(VoiceMicState.CLOSED, asr.state)
        assertFalse(asr.beginAsr())
        assertFalse(asr.beginKws())
    }

    @Test
    fun staleReleaseFromPreviousOwnerCannotUnlockCurrentOwner() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginKws())
        state.endKws()
        assertTrue(state.beginAsr())

        // A late KWS cleanup callback must not release ASR ownership.
        state.endKws()
        assertEquals(VoiceMicState.ASR_LISTENING, state.state)
        assertFalse(state.beginKws())

        state.beginProcessing()
        // A stale ASR/KWS release must not skip PROCESSING.
        state.endKws()
        assertEquals(VoiceMicState.PROCESSING, state.state)
        state.endProcessing()
        assertEquals(VoiceMicState.IDLE, state.state)
    }
}
