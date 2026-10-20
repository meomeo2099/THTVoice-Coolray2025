package com.voicegeely.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseSessionPolicyTest {
    private val interval = LicenseSessionPolicy.REMOTE_REFRESH_INTERVAL_MS

    @Test
    fun neverCheckedIsDue() = assertTrue(LicenseSessionPolicy.remoteRefreshDue(0L, 1_000L))

    @Test
    fun recentCheckIsNotDue() = assertFalse(LicenseSessionPolicy.remoteRefreshDue(10_000L, 10_000L + interval - 1))

    @Test
    fun oldCheckIsDue() = assertTrue(LicenseSessionPolicy.remoteRefreshDue(10_000L, 10_000L + interval))

    @Test
    fun clockBehindLastCheckIsDue() = assertTrue(LicenseSessionPolicy.remoteRefreshDue(10_000L, 5_000L))
}
