package com.voicegeely.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeyDebounceTest {
    @Test
    fun secondPathForTheSamePressIsDropped() {
        val d = VoiceKeyDebounce()
        assertTrue(d.accept(10_000))
        assertFalse("runtime + manifest receiver for one press", d.accept(10_005))
        assertFalse("press/release pair", d.accept(10_900))
        assertTrue("next press", d.accept(11_000))
    }

    @Test
    fun clockGoingBackwardsNeverBlocksTheKey() {
        val d = VoiceKeyDebounce()
        assertTrue(d.accept(50_000))
        assertTrue(d.accept(1_000))
    }
}
