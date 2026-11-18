package com.voicegeely.app.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3AutoUpdatePolicyTest {
    private val now = 1_800_000_000_000L

    @Test fun firstRunWithoutPackDownloads() =
        assertTrue(Mp3AutoUpdatePolicy.shouldCheck(licensed = true, online = true, hasPack = false, lastCheckAt = 0L, now = now))

    @Test fun neverWithoutLicenseOrNetwork() {
        assertFalse(Mp3AutoUpdatePolicy.shouldCheck(false, true, false, 0L, now))
        assertFalse(Mp3AutoUpdatePolicy.shouldCheck(true, false, false, 0L, now))
    }

    @Test fun missingPackRetriesAfterFifteenMinutes() {
        val last = now - Mp3AutoUpdatePolicy.RETRY_WITHOUT_PACK_MS
        assertFalse(Mp3AutoUpdatePolicy.shouldCheck(true, true, false, last + 1, now))
        assertTrue(Mp3AutoUpdatePolicy.shouldCheck(true, true, false, last, now))
    }

    @Test fun installedPackChecksOncePerDay() {
        assertFalse(Mp3AutoUpdatePolicy.shouldCheck(true, true, true, now - 60 * 60 * 1000L, now))
        assertTrue(Mp3AutoUpdatePolicy.shouldCheck(true, true, true, now - Mp3AutoUpdatePolicy.CHECK_WITH_PACK_MS, now))
    }

    @Test fun clockMovedBackChecksAgain() =
        assertTrue(Mp3AutoUpdatePolicy.shouldCheck(true, true, true, now + 1000L, now))
}
