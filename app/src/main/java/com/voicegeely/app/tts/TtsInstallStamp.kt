package com.voicegeely.app.tts

import java.security.MessageDigest

/**
 * Identifies which APK produced the Kokoro copy in app storage.
 *
 * Without it a copy from an older test build (or a copy cut off half-way through the 326 MB
 * model) was reused forever after installing a new APK, and the assistant stayed silent.
 */
object TtsInstallStamp {
    const val FILE = ".install-stamp"

    fun expected(assetSourceText: String, apkUpdatedAtMs: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(assetSourceText.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return "apk=$apkUpdatedAtMs;source=$digest"
    }

    fun isCurrent(installed: String?, expected: String): Boolean = installed?.trim() == expected
}
