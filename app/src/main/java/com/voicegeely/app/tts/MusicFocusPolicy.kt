package com.voicegeely.app.tts

import com.voicegeely.app.voice.MediaAction
import com.voicegeely.app.voice.VoiceCommand

/**
 * Pure rules for the music while the assistant is active (unit-tested; no Android types).
 *
 * PAUSE mode takes transient audio focus: music players pause and resume by themselves when the
 * assistant gives focus back. A spoken "tạm dừng nhạc" must win over that automatic resume, so
 * the pause is sent once more after focus is released.
 */
object MusicFocusPolicy {
    fun pauseMusic(pauseMode: Boolean): Boolean = pauseMode

    fun repauseAfterRelease(pauseMode: Boolean, command: VoiceCommand?, commandSucceeded: Boolean): Boolean =
        pauseMode && commandSucceeded &&
            command is VoiceCommand.MediaControl && command.action == MediaAction.PAUSE
}
