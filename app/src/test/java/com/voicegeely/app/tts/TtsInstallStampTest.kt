package com.voicegeely.app.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsInstallStampTest {
    private val source = "speaker_0=ngoc_huyen\nspeaker_1=diem_trinh\n"

    @Test
    fun sameApkAndAssetsReuseCopy() {
        val stamp = TtsInstallStamp.expected(source, 1_000L)
        assertTrue(TtsInstallStamp.isCurrent("$stamp\n", stamp))
    }

    @Test
    fun newApkInstallForcesRecopy() {
        assertNotEquals(TtsInstallStamp.expected(source, 1_000L), TtsInstallStamp.expected(source, 2_000L))
    }

    @Test
    fun changedAssetsForceRecopy() {
        assertNotEquals(TtsInstallStamp.expected(source, 1_000L), TtsInstallStamp.expected(source + "x", 1_000L))
    }

    @Test
    fun missingStampFromOldOrInterruptedCopyIsNotCurrent() {
        assertFalse(TtsInstallStamp.isCurrent(null, TtsInstallStamp.expected(source, 1_000L)))
    }
}
