package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinBindingPolicyTest {
    private val vin = "LB37752Z3RH000001"
    private val hash = VinBindingPolicy.vinHash(vin, "ABCD1234ABCD1234")

    @Test
    fun vinRequiredStaysOffUntilHardwareValidation() = assertFalse(VinBindingPolicy.VIN_REQUIRED_ENABLED)

    @Test
    fun offPassesEveryLicense() {
        assertTrue(VinBindingPolicy.bindingOk(false, LicenseType.STANDARD, "", null))
    }

    @Test
    fun vipNeverNeedsVin() {
        assertTrue(VinBindingPolicy.bindingOk(true, LicenseType.VIP, "", null))
        assertTrue(VinBindingPolicy.bindingOk(true, LicenseType.VIP, hash, "other"))
    }

    @Test
    fun standardNeedsMatchingVinWhenRequired() {
        assertTrue(VinBindingPolicy.bindingOk(true, LicenseType.STANDARD, hash, hash))
        assertFalse(VinBindingPolicy.bindingOk(true, LicenseType.STANDARD, hash, "other"))
        assertFalse(VinBindingPolicy.bindingOk(true, LicenseType.STANDARD, hash, null))
        assertFalse(VinBindingPolicy.bindingOk(true, LicenseType.STANDARD, "", hash))
    }

    @Test
    fun hashDependsOnVinAndDeviceAndHidesVin() {
        assertNotEquals(hash, VinBindingPolicy.vinHash(vin, "FFFF0000FFFF0000"))
        assertFalse(hash.contains(vin, ignoreCase = true))
        assertEquals(64, hash.length)
    }

    @Test
    fun licenseTypeFromServer() {
        assertEquals(LicenseType.VIP, LicenseType.fromWire(" VIP "))
        assertEquals(LicenseType.STANDARD, LicenseType.fromWire(null))
        assertEquals(LicenseType.STANDARD, LicenseType.fromWire("gold"))
    }

    @Test
    fun vinMismatchIsBindingMismatchInGate() {
        assertEquals(
            LicenseGateState.BINDING_MISMATCH,
            LicenseGateEvaluator.evaluate(
                hasRecord = true, requestMatches = true, vinBindingOk = false, activationPresent = true,
                expiresAt = 0L, lastSeenAt = 0L, remoteBlocked = false, now = 1L, rollbackToleranceMs = 0L
            )
        )
    }
}
