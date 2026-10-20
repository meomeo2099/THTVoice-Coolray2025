package com.voicegeely.app.asr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoCancelPolicyTest {
    @Test
    fun onlyWhilePlayingAndNeverForWakeWord() {
        assertFalse(EchoCancelPolicy.shouldAttach(forWakeWord = false, enabled = true, musicActive = false, replySpeaking = false))
        assertTrue(EchoCancelPolicy.shouldAttach(forWakeWord = false, enabled = true, musicActive = true, replySpeaking = false))
        assertTrue(EchoCancelPolicy.shouldAttach(forWakeWord = false, enabled = true, musicActive = false, replySpeaking = true))
        assertFalse(EchoCancelPolicy.shouldAttach(forWakeWord = true, enabled = true, musicActive = true, replySpeaking = true))
        assertFalse(EchoCancelPolicy.shouldAttach(forWakeWord = false, enabled = false, musicActive = true, replySpeaking = true))
    }
}
