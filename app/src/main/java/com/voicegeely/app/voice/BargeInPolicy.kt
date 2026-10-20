package com.voicegeely.app.voice

import com.voicegeely.app.kws.VoiceMicState

/**
 * Voice key pressed while a session is still running (owner decision 01/10/2026):
 * - mic already listening → nothing to do (it is listening);
 * - command being handled or the answer being spoken → stop the answer and listen again.
 */
object BargeInPolicy {
    fun shouldBargeIn(state: VoiceMicState): Boolean = state == VoiceMicState.PROCESSING
}
