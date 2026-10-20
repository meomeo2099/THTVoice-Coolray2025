package com.voicegeely.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackStateVerifierTest {
    @Test
    fun playPauseAndToggleHaveDeterministicExpectedState() {
        assertTrue(MediaPlaybackStateVerifier.expectedActive(MediaAction.PLAY, false) == true)
        assertTrue(MediaPlaybackStateVerifier.expectedActive(MediaAction.PAUSE, true) == false)
        assertTrue(MediaPlaybackStateVerifier.expectedActive(MediaAction.TOGGLE, true) == false)
        assertTrue(MediaPlaybackStateVerifier.expectedActive(MediaAction.TOGGLE, false) == true)
    }

    @Test
    fun nextAndPreviousAreNeverConfirmedFromMusicActiveAlone() {
        assertNull(MediaPlaybackStateVerifier.expectedActive(MediaAction.NEXT, true))
        assertNull(MediaPlaybackStateVerifier.expectedActive(MediaAction.PREVIOUS, true))
    }

    @Test
    fun confirmationRequiresObservedStateToMatchExpectation() {
        assertTrue(MediaPlaybackStateVerifier.isConfirmed(MediaAction.PLAY, false, true))
        assertTrue(MediaPlaybackStateVerifier.isConfirmed(MediaAction.PAUSE, true, false))
        assertFalse(MediaPlaybackStateVerifier.isConfirmed(MediaAction.PLAY, false, false))
        assertFalse(MediaPlaybackStateVerifier.isConfirmed(MediaAction.NEXT, true, true))
    }
}
