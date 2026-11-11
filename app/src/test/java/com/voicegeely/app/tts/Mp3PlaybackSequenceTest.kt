package com.voicegeely.app.tts

import org.junit.Assert.*
import org.junit.Test

class Mp3PlaybackSequenceTest {
    @Test fun failureAfterFirstClipRequiresFallback() {
        val visited = mutableListOf<String>()
        assertFalse(Mp3PlaybackSequence.play(listOf("prefix", "22", "degree"), { false }) {
            visited.add(it)
            it != "22"
        })
        assertEquals(listOf("prefix", "22"), visited)
    }
    @Test fun exceptionAfterFirstClipRequiresFallback() {
        assertFalse(Mp3PlaybackSequence.play(listOf("prefix", "22"), { false }) {
            if (it == "22") error("decoder failure")
            true
        })
    }
    @Test fun completeSequencePlaysInOrder() {
        val visited = mutableListOf<String>()
        assertTrue(Mp3PlaybackSequence.play(listOf("prefix", "22", "degree"), { false }) {
            visited.add(it); true
        })
        assertEquals(listOf("prefix", "22", "degree"), visited)
    }
    @Test fun voiceKeyStopsSequenceWithoutFallback() {
        var cancelled = false
        val visited = mutableListOf<String>()
        assertTrue(Mp3PlaybackSequence.play(listOf("prefix", "22"), { cancelled }) {
            visited.add(it); cancelled = true; false
        })
        assertEquals(listOf("prefix"), visited)
    }
    @Test fun alreadyCancelledDoesNotStartAudio() {
        assertTrue(Mp3PlaybackSequence.play(listOf("prefix"), { true }) {
            fail("Audio must not start"); false
        })
    }
    @Test fun interruptedThreadDoesNotStartFallback() {
        Thread.currentThread().interrupt()
        try {
            assertTrue(Mp3PlaybackSequence.play(listOf("prefix"), { false }) {
                fail("Audio must not start"); false
            })
        } finally { Thread.interrupted() }
    }
}
