package com.voicegeely.app.security

/**
 * Runtime signing check (pure logic, unit-tested). A release build knows the SHA-256 of the
 * official signing certificate (BuildConfig.RELEASE_CERT_SHA256, injected by the release
 * workflow). If the running APK is signed with another certificate it was repacked and re-signed
 * by someone else, so the license gate stops it and the server refuses it.
 *
 * Builds without an expected value (debug / hardware-test builds) skip the check.
 */
object SignaturePolicy {
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** "AB:CD:…" or "abcd…" → lowercase hex without separators, or "" when not a SHA-256. */
    fun normalize(value: String?): String {
        val hex = value.orEmpty().lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
        return if (HEX64.matches(hex)) hex else ""
    }

    fun enforced(expected: String?): Boolean = normalize(expected).isNotEmpty()

    /**
     * True when the check is off, or one of the certificates the APK is signed with matches.
     * An unreadable signature (empty list) counts as not genuine when the check is on.
     */
    fun isGenuine(expected: String?, actual: List<String>): Boolean {
        val want = normalize(expected)
        if (want.isEmpty()) return true
        return actual.any { normalize(it) == want }
    }
}
