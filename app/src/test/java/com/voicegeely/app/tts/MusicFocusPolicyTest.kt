package com.voicegeely.app.tts

import com.voicegeely.app.voice.MediaAction
import com.voicegeely.app.voice.VoiceCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFocusPolicyTest {
    private val pause = VoiceCommand.MediaControl(MediaAction.PAUSE)

    @Test
    fun pauseModeFollowsSetting() {
        assertTrue(MusicFocusPolicy.pauseMusic(true))
        assertFalse(MusicFocusPolicy.pauseMusic(false))
    }

    @Test
    fun spokenPauseStaysPausedInPauseMode() {
        assertTrue(MusicFocusPolicy.repauseAfterRelease(true, pause, commandSucceeded = true))
    }

    @Test
    fun otherCasesLetMusicResume() {
        assertFalse(MusicFocusPolicy.repauseAfterRelease(false, pause, true)) // duck mode: nothing paused by us
        assertFalse(MusicFocusPolicy.repauseAfterRelease(true, pause, false)) // pause command failed
        assertFalse(MusicFocusPolicy.repauseAfterRelease(true, VoiceCommand.MediaControl(MediaAction.NEXT), true))
        assertFalse(MusicFocusPolicy.repauseAfterRelease(true, null, false)) // nothing heard
    }
}
