package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.MediaContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMediaAppResolverTest {
    @Test
    fun selectedRevancedIsFirstForMusic() {
        val result = DefaultMediaAppResolver.packageCandidates(
            DefaultMediaApp.REVANCED,
            MediaContentKind.MUSIC
        )
        assertEquals("app.revanced.android.youtube", result.first())
        assertEquals(result.distinct(), result)
    }

    @Test
    fun autoVideoUsesVideoFamilyOnlyAtFront() {
        val result = DefaultMediaAppResolver.packageCandidates(
            DefaultMediaApp.AUTO,
            MediaContentKind.VIDEO
        )
        assertTrue(result.take(5).contains("com.google.android.youtube"))
        assertTrue(result.contains("org.smarttube.stable"))
        assertTrue(!result.contains("com.spotify.music"))
    }

    @Test
    fun explicitSmartTubeHasAllKnownCandidates() {
        val result = DefaultMediaAppResolver.packagesFor(DefaultMediaApp.SMARTTUBE)
        assertTrue(result.contains("org.smarttube.stable"))
        assertTrue(result.contains("org.smarttube.beta"))
        assertTrue(result.contains("com.liskovsoft.smarttubetv"))
    }
}
