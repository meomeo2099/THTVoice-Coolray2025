package com.voicegeely.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MutedSpeechGuardTest {
    private class FakeAudio(var muted: Boolean, private val grantFocus: Boolean = true) : MutedSpeechGuard.Audio {
        val events = mutableListOf<String>()
        var mutedWhileSpeaking: Boolean? = null
        override fun isMediaMuted() = muted
        override fun requestSpeechFocus(): Boolean { events += "focus"; return grantFocus }
        override fun abandonSpeechFocus() { events += "abandon" }
        override fun setMediaMuted(muted: Boolean) { events += if (muted) "mute" else "unmute"; this.muted = muted }
    }

    @Test
    fun notMutedSpeaksWithoutTouchingAudio() {
        val audio = FakeAudio(muted = false)
        val guard = MutedSpeechGuard(audio)
        assertTrue(guard.speak { true })
        assertEquals(emptyList<String>(), audio.events)
        assertEquals(MutedSpeechGuard.Mode.NOT_MUTED, guard.lastMode)
    }

    @Test
    fun mutedAssistantStillSpeaksThenMuteIsRestored() {
        val audio = FakeAudio(muted = true)
        val guard = MutedSpeechGuard(audio)
        guard.speak { audio.mutedWhileSpeaking = audio.muted }
        assertEquals(false, audio.mutedWhileSpeaking)
        assertEquals(listOf("focus", "unmute", "mute", "abandon"), audio.events)
        assertTrue(audio.muted)
        assertEquals(MutedSpeechGuard.Mode.UNMUTED_FOR_SPEECH, guard.lastMode)
    }

    @Test
    fun muteIsRestoredEvenWhenPlaybackThrows() {
        val audio = FakeAudio(muted = true)
        runCatching { MutedSpeechGuard(audio).speak { error("playback") } }
        assertTrue(audio.muted)
        assertEquals(listOf("focus", "unmute", "mute", "abandon"), audio.events)
    }

    @Test
    fun refusedFocusNeverExposesMusic() {
        val audio = FakeAudio(muted = true, grantFocus = false)
        val guard = MutedSpeechGuard(audio)
        guard.speak { audio.mutedWhileSpeaking = audio.muted }
        assertEquals(true, audio.mutedWhileSpeaking)
        assertEquals(listOf("focus"), audio.events)
        assertEquals(MutedSpeechGuard.Mode.FOCUS_REFUSED_STAYS_MUTED, guard.lastMode)
    }
}
