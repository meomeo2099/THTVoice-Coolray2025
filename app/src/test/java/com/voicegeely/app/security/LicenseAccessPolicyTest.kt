package com.voicegeely.app.security

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseAccessPolicyTest {
    @Test fun freeModeLetsCarsWithoutAKeyRun() {
        for (gate in listOf(LicenseGateState.UNLICENSED, LicenseGateState.EXPIRED, LicenseGateState.BINDING_MISMATCH, LicenseGateState.CLOCK_INVALID))
            assertTrue(gate.name, LicenseAccessPolicy.allows(gate, keyRequired = false))
        assertTrue(LicenseAccessPolicy.allows(LicenseGateState.ACTIVE, keyRequired = false))
    }

    @Test fun keyRequiredLocksEverythingButAnActiveKey() {
        for (gate in LicenseGateState.values())
            assertEquals(gate.name, gate == LicenseGateState.ACTIVE, LicenseAccessPolicy.allows(gate, keyRequired = true))
    }

    @Test fun blockedDeviceAndRepackedApkStayLockedInFreeMode() {
        assertFalse(LicenseAccessPolicy.allows(LicenseGateState.REMOTE_BLOCKED, keyRequired = false))
        assertFalse(LicenseAccessPolicy.allows(LicenseGateState.TAMPERED, keyRequired = false))
    }

    @Test fun signedPolicyDecidesAndDefaultsToRequired() {
        val req = "0123456789ABCDEF"
        val day = 24L * 60L * 60L * 1000L
        val free = PolicyTokenPayload(keyRequired = false, requestCode = req, issuedAt = 10 * day)
        val required = PolicyTokenPayload(keyRequired = true, requestCode = req, issuedAt = 10 * day)
        assertTrue(LicenseAccessPolicy.keyRequired(null, req, 11 * day))
        assertFalse(LicenseAccessPolicy.keyRequired(free, req, 11 * day))
        assertTrue(LicenseAccessPolicy.keyRequired(required, req, 11 * day))
        assertTrue(LicenseAccessPolicy.keyRequired(required, req, 400 * day))
        // Another car's reply counts as none.
        assertTrue(LicenseAccessPolicy.keyRequired(free, "FEDCBA9876543210", 11 * day))
        // An old "free" (or one dated in the future) stops working.
        assertTrue(LicenseAccessPolicy.keyRequired(free, req, 41 * day))
        assertTrue(LicenseAccessPolicy.keyRequired(free, req, 9 * day))
    }

    @Test fun policyTokenIsSignedPerDeviceAndNeverALicense() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val keys = LicenseToken.parseKeys("k1:" + Base64.getEncoder().encodeToString(pair.public.encoded))
        fun sign(payload: String): String {
            val head = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
            val der = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(head.toByteArray()) }.sign()
            return head + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(derToRaw(der))
        }
        val policy = """{"v":1,"kid":"k1","kind":"policy","mode":"free","req":"0123456789ABCDEF","iat":5}"""
        assertEquals(PolicyTokenPayload(false, "0123456789ABCDEF", 5L), LicenseToken.verifyPolicy(sign(policy), keys))
        assertEquals(true, LicenseToken.verifyPolicy(sign(policy.replace("free", "required")), keys)?.keyRequired)
        assertNull(LicenseToken.verify(sign(policy), keys))
        assertNull(LicenseToken.verifyPolicy(sign("""{"v":1,"kid":"k1","lid":7,"req":"0123456789ABCDEF","typ":"vip","exp":0,"iat":1}"""), keys))
        assertNull(LicenseToken.verifyPolicy(sign(policy.replace("free", "open")), keys))
        val edited = sign(policy).replaceFirst(
            Base64.getUrlEncoder().withoutPadding().encodeToString(policy.toByteArray()),
            Base64.getUrlEncoder().withoutPadding().encodeToString(policy.replace("free", "required").toByteArray())
        )
        assertNull(LicenseToken.verifyPolicy(edited, keys))
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        fun int(): ByteArray {
            require(der[i].toInt() == 0x02); val len = der[i + 1].toInt(); val v = der.copyOfRange(i + 2, i + 2 + len); i += 2 + len
            val b = BigInteger(1, v).toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
            return ByteArray(32 - b.size) + b
        }
        return int() + int()
    }

    @Test fun refreshEvery12Hours() {
        val h = 60L * 60L * 1000L
        assertTrue(LicenseAccessPolicy.refreshDue(0L, 1000L))
        assertFalse(LicenseAccessPolicy.refreshDue(10 * h, 11 * h))
        assertTrue(LicenseAccessPolicy.refreshDue(10 * h, 22 * h))
        assertTrue(LicenseAccessPolicy.refreshDue(10 * h, 5 * h))
    }
}
