package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceFeedbackPolicyTest {
    @Test fun confirmedUsesSuccessCue() {
        assertEquals(
            VoiceFeedbackCue.SUCCESS,
            VoiceFeedbackPolicy.forExecution(
                CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK)
            )
        )
    }

    @Test fun unverifiedDoesNotPretendSuccess() {
        assertEquals(
            VoiceFeedbackCue.PROCESSING,
            VoiceFeedbackPolicy.forExecution(CommandExecutionResult.AcceptedUnverified("pending read-back"))
        )
    }

    @Test fun rejectedAndFailedUseErrorCue() {
        assertEquals(
            VoiceFeedbackCue.ERROR,
            VoiceFeedbackPolicy.forExecution(CommandExecutionResult.Rejected("unsupported"))
        )
        assertEquals(
            VoiceFeedbackCue.ERROR,
            VoiceFeedbackPolicy.forExecution(CommandExecutionResult.Failed("backend"))
        )
    }
}
