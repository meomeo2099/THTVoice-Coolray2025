package com.voicegeely.app.security

/**
 * Keeps the running app in sync with the license gate:
 * - every voice trigger re-checks the local gate (a license that expires or is blocked while
 *   the Voice core is running stops it at the next trigger, not only at the next restart);
 * - the server status (renewal / remote block) is refreshed in the background at most every
 *   [REMOTE_REFRESH_INTERVAL_MS]. Offline refresh failures never lock a valid local license.
 */
object LicenseSessionPolicy {
    const val REMOTE_REFRESH_INTERVAL_MS = 12L * 60L * 60L * 1000L

    fun remoteRefreshDue(remoteCheckedAt: Long, now: Long): Boolean =
        remoteCheckedAt <= 0L || now < remoteCheckedAt || now - remoteCheckedAt >= REMOTE_REFRESH_INTERVAL_MS
}
