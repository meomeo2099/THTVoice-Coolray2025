package com.voicegeely.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptPolicyTest {
    private val h = 60L * 60 * 1000

    @Test
    fun autoCheckAtMostEverySixHours() {
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(now = 10 * h, lastCheckAt = 0))
        assertFalse(UpdatePromptPolicy.shouldAutoCheck(now = 10 * h, lastCheckAt = 7 * h))
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(now = 13 * h, lastCheckAt = 7 * h))
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(now = 5 * h, lastCheckAt = 7 * h)) // clock went back
    }

    @Test
    fun requiredOnlyWhileOlderThanTheForcedVersion() {
        assertTrue(UpdatePromptPolicy.isRequired(requiredVersionCode = 3, currentVersionCode = 2))
        assertFalse(UpdatePromptPolicy.isRequired(requiredVersionCode = 3, currentVersionCode = 3))
        assertFalse(UpdatePromptPolicy.isRequired(requiredVersionCode = 0, currentVersionCode = 1))
    }

    @Test
    fun assistantIsHeldOnlyWhenParked() {
        assertTrue(UpdatePromptPolicy.blockAssistant(updateRequired = true, parked = true))
        assertFalse(UpdatePromptPolicy.blockAssistant(updateRequired = true, parked = false))
        assertFalse(UpdatePromptPolicy.blockAssistant(updateRequired = false, parked = true))
    }
}
