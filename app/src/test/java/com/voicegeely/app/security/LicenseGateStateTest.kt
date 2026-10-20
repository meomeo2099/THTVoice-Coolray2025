package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseGateStateTest {
    private val now = 1_000_000L
    private val tolerance = 100L

    @Test
    fun noRecord_isUnlicensed() {
        assertEquals(
            LicenseGateState.UNLICENSED,
            evaluate(hasRecord = false)
        )
    }

    @Test
    fun missingActivationPayload_isUnlicensedEvenWhenRecordExists() {
        assertEquals(
            LicenseGateState.UNLICENSED,
            evaluate(hasRecord = true, activationPresent = false)
        )
    }

    @Test
    fun matchingValidRecord_isActive() {
        assertEquals(LicenseGateState.ACTIVE, evaluate())
    }

    @Test
    fun requestMismatch_isBindingMismatch() {
        assertEquals(
            LicenseGateState.BINDING_MISMATCH,
            evaluate(requestMatches = false)
        )
    }

    @Test
    fun bindingMismatchFailsBeforeRemoteOrExpiryStateCanBeTrusted() {
        assertEquals(
            LicenseGateState.BINDING_MISMATCH,
            evaluate(
                requestMatches = false,
                remoteBlocked = true,
                expiresAt = now - 1
            )
        )
    }

    @Test
    fun remoteBlock_takesPriority() {
        assertEquals(
            LicenseGateState.REMOTE_BLOCKED,
            evaluate(remoteBlocked = true, expiresAt = now - 1)
        )
    }

    @Test
    fun expiredRecord_isExpired() {
        assertEquals(
            LicenseGateState.EXPIRED,
            evaluate(expiresAt = now - 1)
        )
    }

    @Test
    fun expirationAtExactCurrentTime_isStillActive() {
        assertEquals(
            LicenseGateState.ACTIVE,
            evaluate(expiresAt = now)
        )
    }

    @Test
    fun clockRollback_isRejected() {
        assertEquals(
            LicenseGateState.CLOCK_INVALID,
            evaluate(lastSeenAt = now + tolerance + 1)
        )
    }

    @Test
    fun clockRollbackAtToleranceBoundary_isStillActive() {
        assertEquals(
            LicenseGateState.ACTIVE,
            evaluate(lastSeenAt = now + tolerance)
        )
    }

    @Test
    fun nonExpiringRecordWithNoPreviousClockSample_isActive() {
        assertEquals(
            LicenseGateState.ACTIVE,
            evaluate(expiresAt = 0L, lastSeenAt = 0L)
        )
    }

    private fun evaluate(
        hasRecord: Boolean = true,
        requestMatches: Boolean = true,
        activationPresent: Boolean = true,
        expiresAt: Long = 0L,
        lastSeenAt: Long = now,
        remoteBlocked: Boolean = false
    ): LicenseGateState = LicenseGateEvaluator.evaluate(
        hasRecord = hasRecord,
        requestMatches = requestMatches,
        activationPresent = activationPresent,
        expiresAt = expiresAt,
        lastSeenAt = lastSeenAt,
        remoteBlocked = remoteBlocked,
        now = now,
        rollbackToleranceMs = tolerance
    )

    @Test
    fun resignedAppIsTamperedEvenWithValidLicense() {
        val state = LicenseGateEvaluator.evaluate(
            hasRecord = true,
            requestMatches = true,
            activationPresent = true,
            expiresAt = 0L,
            lastSeenAt = 0L,
            remoteBlocked = false,
            now = 1_000L,
            rollbackToleranceMs = 0L,
            genuineApp = false
        )
        org.junit.Assert.assertEquals(LicenseGateState.TAMPERED, state)
    }
}
