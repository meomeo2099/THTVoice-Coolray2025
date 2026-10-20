package com.voicegeely.app.security

import java.util.Locale

/**
 * Codes typed on the activation screen (same rules as worker/src/codes.js):
 * - 20 digits = new activation code, last digit is a Damm check digit (catches one wrong digit
 *   and any swap of two neighbouring digits before anything is sent);
 * - 16 characters 0-9/A-F = code from the previous server (still accepted);
 * - 8 digits = one-time unlock code after too many wrong tries.
 */
object LicenseCodes {
    sealed class Parsed {
        data class Numeric(val code: String) : Parsed()
        data class Hex(val code: String) : Parsed()
        data class Unlock(val code: String) : Parsed()
        object Typo : Parsed()
        object Invalid : Parsed()
    }

    private val DAMM = arrayOf(
        intArrayOf(0, 3, 1, 7, 5, 9, 8, 6, 4, 2),
        intArrayOf(7, 0, 9, 2, 1, 5, 4, 8, 6, 3),
        intArrayOf(4, 2, 0, 6, 8, 7, 1, 3, 5, 9),
        intArrayOf(1, 7, 5, 0, 9, 8, 3, 4, 2, 6),
        intArrayOf(6, 1, 2, 3, 0, 4, 5, 9, 7, 8),
        intArrayOf(3, 6, 7, 4, 2, 0, 9, 5, 8, 1),
        intArrayOf(5, 8, 6, 9, 7, 2, 0, 1, 3, 4),
        intArrayOf(8, 9, 4, 5, 3, 6, 2, 0, 1, 7),
        intArrayOf(9, 4, 3, 8, 6, 1, 7, 2, 0, 5),
        intArrayOf(2, 5, 8, 1, 4, 3, 6, 7, 9, 0)
    )

    fun dammDigit(digits: String): Int {
        var interim = 0
        for (ch in digits) interim = DAMM[interim][ch - '0']
        return interim
    }

    fun dammValid(digits: String): Boolean =
        digits.isNotEmpty() && digits.all { it in '0'..'9' } && dammDigit(digits) == 0

    /** Upper-case letters/digits only (separators removed). */
    fun compact(raw: String): String = raw.uppercase(Locale.US).filter { it in '0'..'9' || it in 'A'..'Z' }

    fun parse(raw: String): Parsed {
        val c = compact(raw)
        return when {
            c.length == 20 && c.all { it in '0'..'9' } -> if (dammValid(c)) Parsed.Numeric(c) else Parsed.Typo
            c.length == 16 && c.all { it in '0'..'9' || it in 'A'..'F' } -> Parsed.Hex(c)
            c.length == 8 && c.all { it in '0'..'9' } -> Parsed.Unlock(c)
            else -> Parsed.Invalid
        }
    }

    /** Live formatting in the input: groups of 4, up to 20 digits or 16 hex characters. */
    fun formatForInput(raw: String): String {
        val c = compact(raw).filter { it in '0'..'9' || it in 'A'..'F' }
        val limited = if (c.any { it in 'A'..'F' }) c.take(16) else c.take(20)
        return limited.chunked(4).joinToString("-")
    }
}
