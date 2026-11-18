package com.voicegeely.app.tts

/**
 * When the service checks the server for an MP3 voice pack by itself (release APKs carry none).
 * No pack yet: retry soon after a failed try. Pack installed: look for a newer revision once a day.
 */
internal object Mp3AutoUpdatePolicy {
    const val RETRY_WITHOUT_PACK_MS = 15 * 60 * 1000L
    const val CHECK_WITH_PACK_MS = 24 * 60 * 60 * 1000L

    fun shouldCheck(licensed: Boolean, online: Boolean, hasPack: Boolean, lastCheckAt: Long, now: Long): Boolean {
        if (!licensed || !online) return false
        if (lastCheckAt <= 0L || now < lastCheckAt) return true
        val wait = if (hasPack) CHECK_WITH_PACK_MS else RETRY_WITHOUT_PACK_MS
        return now - lastCheckAt >= wait
    }
}
