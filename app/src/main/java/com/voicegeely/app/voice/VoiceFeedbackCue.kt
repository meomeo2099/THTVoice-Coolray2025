package com.voicegeely.app.voice

/**
 * Lightweight offline feedback used when spoken responses are disabled.
 *
 * Tones are synthesized in code so the feedback path does not depend on network, TTS
 * or bundled media assets.
 */
enum class VoiceFeedbackCue {
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR
}

object VoiceFeedbackPolicy {
    fun forExecution(result: CommandExecutionResult): VoiceFeedbackCue = when (result) {
        is CommandExecutionResult.Confirmed -> VoiceFeedbackCue.SUCCESS
        is CommandExecutionResult.AcceptedUnverified -> VoiceFeedbackCue.PROCESSING
        is CommandExecutionResult.Rejected,
        is CommandExecutionResult.Failed -> VoiceFeedbackCue.ERROR
    }
}

/**
 * Owner sounds (30/09/2026): start = overlay_start, end of listening / error = overlay_stop,
 * handled successfully = success (and then no stop sound).
 */
enum class VoiceFeedbackSound(val rawName: String) {
    START("tht_cue_start"),
    STOP("tht_cue_stop"),
    SUCCESS("tht_cue_success");

    companion object {
        fun forCue(cue: VoiceFeedbackCue): VoiceFeedbackSound = when (cue) {
            VoiceFeedbackCue.LISTENING -> START
            VoiceFeedbackCue.SUCCESS -> SUCCESS
            VoiceFeedbackCue.PROCESSING,
            VoiceFeedbackCue.ERROR -> STOP
        }
    }
}

