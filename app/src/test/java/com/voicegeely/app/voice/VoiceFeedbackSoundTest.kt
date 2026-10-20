package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceFeedbackSoundTest {
    @Test fun listeningPlaysStartSound() {
        assertEquals(VoiceFeedbackSound.START, VoiceFeedbackSound.forCue(VoiceFeedbackCue.LISTENING))
    }

    @Test fun confirmedCommandPlaysSuccessNotStop() {
        val cue = VoiceFeedbackPolicy.forExecution(CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK))
        assertEquals(VoiceFeedbackSound.SUCCESS, VoiceFeedbackSound.forCue(cue))
    }

    @Test fun unverifiedFailedAndErrorsPlayStop() {
        listOf(
            CommandExecutionResult.AcceptedUnverified("pending"),
            CommandExecutionResult.Rejected("no"),
            CommandExecutionResult.Failed("x")
        ).forEach { result ->
            assertEquals(VoiceFeedbackSound.STOP, VoiceFeedbackSound.forCue(VoiceFeedbackPolicy.forExecution(result)))
        }
        assertEquals(VoiceFeedbackSound.STOP, VoiceFeedbackSound.forCue(VoiceFeedbackCue.ERROR))
    }
}
