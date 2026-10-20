package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinRulesTest {
    @Test
    fun validVin_isAcceptedAndUppercased() {
        assertEquals(
            "1HGCM82633A004352",
            VinRules.normalizeAndValidate("1hgcm82633a004352")
        )
    }

    @Test
    fun surroundingWhitespace_isRemoved() {
        assertEquals(
            "1HGCM82633A004352",
            VinRules.normalizeAndValidate("  1HGCM82633A004352  ")
        )
    }

    @Test
    fun wrongLength_isRejected() {
        assertNull(VinRules.normalizeAndValidate("1HGCM82633A00435"))
    }

    @Test
    fun forbiddenVinLetters_areRejected() {
        assertFalse(VinRules.isValid("1HGCM82633I004352"))
        assertFalse(VinRules.isValid("1HGCM82633O004352"))
        assertFalse(VinRules.isValid("1HGCM82633Q004352"))
    }

    @Test
    fun standardVinAlphabet_isAccepted() {
        assertTrue(VinRules.isValid("JH4TB2H26CC000000"))
    }
}
