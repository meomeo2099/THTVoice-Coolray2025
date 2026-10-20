package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackToneSynthTest {
    @Test
    fun everyCueIsAudibleAndShort() {
        for (cue in VoiceFeedbackCue.values()) {
            val pcm = FeedbackToneSynth.pcm(cue)
            val ms = pcm.size * 1000 / FeedbackToneSynth.SAMPLE_RATE
            assertTrue("$cue too long: $ms ms", ms in 50..600)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$cue silent", peak > Short.MAX_VALUE / 4)
        }
    }

    @Test
    fun startsAndEndsSilentToAvoidClicks() {
        val pcm = FeedbackToneSynth.pcm(VoiceFeedbackCue.SUCCESS)
        assertEquals(0, pcm.first().toInt())
        assertTrue(kotlin.math.abs(pcm.last().toInt()) < 800)
    }

    @Test
    fun startEndAndErrorAreThreeDistinctSounds() {
        val start = FeedbackToneSynth.segments(VoiceFeedbackCue.LISTENING)
        val end = FeedbackToneSynth.segments(VoiceFeedbackCue.PROCESSING)
        val error = FeedbackToneSynth.segments(VoiceFeedbackCue.ERROR)
        assertEquals(3, setOf(start, end, error).size)
        // Start rises, end falls, error is low.
        assertTrue(start.first().first < start.last().first)
        assertTrue(end.first().first > end.last().first)
        assertTrue(error.filter { it.first > 0 }.all { it.first < 400 })
    }
}
