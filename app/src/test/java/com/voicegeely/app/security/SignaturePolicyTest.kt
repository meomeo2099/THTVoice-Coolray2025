package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePolicyTest {
    private val official = "ab".repeat(32)
    private val other = "cd".repeat(32)

    @Test
    fun normalizesColonFingerprint() {
        val colon = official.chunked(2).joinToString(":").uppercase()
        assertEquals(official, SignaturePolicy.normalize(colon))
        assertEquals("", SignaturePolicy.normalize("not-a-hash"))
        assertEquals("", SignaturePolicy.normalize(null))
    }

    @Test
    fun offWhenNoExpectedCertificate() {
        assertFalse(SignaturePolicy.enforced(""))
        assertTrue(SignaturePolicy.isGenuine("", listOf(other)))
        assertTrue(SignaturePolicy.isGenuine(null, emptyList()))
    }

    @Test
    fun officialCertificatePasses() {
        assertTrue(SignaturePolicy.isGenuine(official, listOf(official)))
        assertTrue(SignaturePolicy.isGenuine(official.uppercase(), listOf(other, official)))
    }

    @Test
    fun resignedOrUnreadableFails() {
        assertFalse(SignaturePolicy.isGenuine(official, listOf(other)))
        assertFalse(SignaturePolicy.isGenuine(official, emptyList()))
    }
}
