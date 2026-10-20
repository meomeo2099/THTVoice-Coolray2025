package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCodesTest {
    // Same Damm table as worker/src/codes.js: "572" → check digit 4.
    @Test
    fun dammMatchesServer() {
        assertEquals(4, LicenseCodes.dammDigit("572"))
        assertTrue(LicenseCodes.dammValid("5724"))
        assertFalse(LicenseCodes.dammValid("5734"))
    }

    @Test
    fun twentyDigitCodesCatchOneWrongDigitAndSwaps() {
        val body = "1234567890123456789"
        val code = body + LicenseCodes.dammDigit(body)
        assertEquals(LicenseCodes.Parsed.Numeric(code), LicenseCodes.parse(code.chunked(4).joinToString("-")))
        for (i in code.indices) {
            val wrong = code.toCharArray().also { it[i] = if (it[i] == '9') '0' else it[i] + 1 }.concatToString()
            assertEquals("digit $i", LicenseCodes.Parsed.Typo, LicenseCodes.parse(wrong))
            if (i < code.length - 1 && code[i] != code[i + 1]) {
                val swapped = code.toCharArray().also { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t }.concatToString()
                assertEquals("swap $i", LicenseCodes.Parsed.Typo, LicenseCodes.parse(swapped))
            }
        }
    }

    @Test
    fun oldHexAndUnlockCodes() {
        assertEquals(LicenseCodes.Parsed.Hex("ABCDEF0123456789"), LicenseCodes.parse("abcd-ef01-2345-6789"))
        assertEquals(LicenseCodes.Parsed.Hex("0123456789012345"), LicenseCodes.parse("0123 4567 8901 2345"))
        assertEquals(LicenseCodes.Parsed.Unlock("12345678"), LicenseCodes.parse("1234-5678"))
        assertEquals(LicenseCodes.Parsed.Invalid, LicenseCodes.parse("12345"))
        assertEquals(LicenseCodes.Parsed.Invalid, LicenseCodes.parse("ABCDEFGH01234567"))
    }

    @Test
    fun inputFormatting() {
        assertEquals("1234-5678-9012-3456-7890", LicenseCodes.formatForInput("123456789012345678901234"))
        assertEquals("ABCD-EF01-2345-6789", LicenseCodes.formatForInput("abcdef0123456789ff"))
        assertEquals("1234-5", LicenseCodes.formatForInput("12345"))
    }
}
