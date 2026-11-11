package com.voicegeely.app.tts

import java.io.InputStream
import java.security.MessageDigest

internal object Mp3PackIntegrity {
    /** Re-read bounded disk bytes, never trust a preference stamp as proof of integrity. */
    fun matches(input: InputStream, expectedSha: String, limit: Int): Boolean = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) return false
            digest.update(buffer, 0, count)
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        actual == expectedSha
    }
}
