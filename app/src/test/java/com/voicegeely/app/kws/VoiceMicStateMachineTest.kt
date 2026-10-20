package com.voicegeely.app.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMicStateMachineTest {
    @Test
    fun kwsAndAsrCannotOwnMicAtSameTime() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginKws())
        assertEquals(VoiceMicState.KWS_LISTENING, state.state)
        assertFalse(state.beginAsr())

        state.endKws()
        assertTrue(state.beginAsr())
        assertEquals(VoiceMicState.ASR_LISTENING, state.state)
    }

    @Test
    fun asrMovesThroughProcessingThenReturnsIdle() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginAsr())
        state.beginProcessing()
        assertEquals(VoiceMicState.PROCESSING, state.state)

        state.endProcessing()
        assertEquals(VoiceMicState.IDLE, state.state)
        assertTrue(state.beginKws())
    }

    @Test
    fun repeatedKwsAcquireIsRejectedUntilRelease() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginKws())
        assertFalse(state.beginKws())
        assertEquals(VoiceMicState.KWS_LISTENING, state.state)

        state.endKws()
        assertEquals(VoiceMicState.IDLE, state.state)
        assertTrue(state.beginKws())
    }

    @Test
    fun processingBlocksKwsAndSecondAsrUntilSessionCompletes() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginAsr())
        state.beginProcessing()
        assertEquals(VoiceMicState.PROCESSING, state.state)
        assertFalse(state.beginKws())
        assertFalse(state.beginAsr())

        state.endProcessing()
        assertEquals(VoiceMicState.IDLE, state.state)
        assertTrue(state.beginKws())
    }

    @Test
    fun endCallsDoNotUnlockWrongOwner() {
        val state = VoiceMicStateMachine()

        assertTrue(state.beginKws())
        state.endProcessing()
        assertEquals(VoiceMicState.KWS_LISTENING, state.state)

        state.endKws()
        assertTrue(state.beginAsr())
        state.endKws()
        assertEquals(VoiceMicState.ASR_LISTENING, state.state)
    }

    @Test
    fun closedStateRejectsNewOwnersAndCannotBeReopenedAccidentally() {
        val state = VoiceMicStateMachine()
        assertTrue(state.beginKws())
        state.close()

        assertEquals(VoiceMicState.CLOSED, state.state)
        state.endKws()
        state.endProcessing()
        assertEquals(VoiceMicState.CLOSED, state.state)
        assertFalse(state.beginKws())
        assertFalse(state.beginAsr())
    }
}
