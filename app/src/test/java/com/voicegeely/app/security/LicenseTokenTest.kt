package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Vectors signed by worker/src/crypto.js (WebCrypto ECDSA P-256, raw r||s). */
class LicenseTokenTest {
    private val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEt/5QVvf7zrgsyjDZfgWxb/0qJAMuV11/WltfYv+YuyucO/ZCf+vpz16f5WHAGAsdNnvZ7NSw8l+j6XKyLA+SOQ=="
    private val otherPub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEqsjk/kA+z/5p7uTSBCAFuk3VA+DMWqQnia2QAQtptT6ozwd9gPCoBxIF/Gnk8ZkyphTEAGkwBqDBtR5jVf4rBg=="
    private val vip = "eyJ2IjoxLCJraWQiOiJrMSIsImxpZCI6NywicmVxIjoiMDEyMzQ1Njc4OUFCQ0RFRiIsInR5cCI6InZpcCIsImV4cCI6MTgwMDAwMDAwMDAwMCwiaWF0IjoxNzkwMDAwMDAwMDAwfQ.4Mb4h7A-j2bYJs8izN63AAsCWe9wd1f5F4vK0BH_x8zb0VwGMHqfzNzPP4LKAuhEZnZQul6kiw6JaDNaDh5ozA"
    private val lifetime = "eyJ2IjoxLCJraWQiOiJrMSIsImxpZCI6OCwicmVxIjoiMDEyMzQ1Njc4OUFCQ0RFRiIsInR5cCI6InN0YW5kYXJkIiwiZXhwIjowLCJpYXQiOjE3OTAwMDAwMDAwMDB9.nHnbQxEZ-bWGdwVGEtD-eb2PGLRWX1kDHrFu-Er_aFu2Vl_-Rm37gNUbXCTtgSxlvhy7-yMSynogSDoCbjlgGQ"

    @Test
    fun serverSignedTokenVerifies() {
        val keys = LicenseToken.parseKeys("k1:$pub")
        val p = LicenseToken.verify(vip, keys)
        assertNotNull(p)
        assertEquals("0123456789ABCDEF", p!!.requestCode)
        assertEquals(LicenseType.VIP, p.licenseType)
        assertEquals(1800000000000L, p.expiresAt)
        assertEquals(7L, p.licenseId)
        assertEquals(0L, LicenseToken.verify(lifetime, keys)!!.expiresAt)
        assertEquals(LicenseType.STANDARD, LicenseToken.verify(lifetime, keys)!!.licenseType)
    }

    @Test
    fun rotatedKeysAreAcceptedByKeyId() {
        assertNotNull(LicenseToken.verify(vip, LicenseToken.parseKeys("k2:$otherPub,k1:$pub")))
        assertNull(LicenseToken.verify(vip, LicenseToken.parseKeys("k1:$otherPub")))
        assertNull(LicenseToken.verify(vip, LicenseToken.parseKeys("k2:$pub")))
        assertNull(LicenseToken.verify(vip, emptyMap()))
    }

    @Test
    fun editedPayloadOrSignatureFails() {
        val keys = LicenseToken.parseKeys("k1:$pub")
        val (body, sig) = vip.split('.')
        val forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            String(java.util.Base64.getUrlDecoder().decode(body)).replace("1800000000000", "0").toByteArray()
        )
        assertNull(LicenseToken.verify("$forged.$sig", keys))
        assertNull(LicenseToken.verify("$body.${sig.dropLast(2)}AA", keys))
        assertNull(LicenseToken.verify(body, keys))
        assertNull(LicenseToken.verify("", keys))
    }

    @Test
    fun flatJsonParser() {
        val m = LicenseToken.parseFlatJson("""{"a":"x\"y","b":-12,"c":true,"d":null}""")!!
        assertEquals("x\"y", m["a"])
        assertEquals(-12L, m["b"])
        assertEquals(true, m["c"])
        assertNull(LicenseToken.parseFlatJson("""{"a":1}x"""))
        assertNull(LicenseToken.parseFlatJson("""{"a":{"b":1}}"""))
    }

    @Test
    fun derEncodingHandlesHighBit() {
        val raw = ByteArray(64) { if (it == 0 || it == 32) 0x80.toByte() else 1 }
        val der = LicenseToken.rawToDer(raw)
        assertEquals(0x30, der[0].toInt())
        assertEquals(der.size - 2, der[1].toInt())
        assertEquals(33, der[3].toInt())
    }
}
