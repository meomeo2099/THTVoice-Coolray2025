package com.voicegeely.app.voice

/** A redacted, empty, or malformed SSID must never be spoken as a network name. */
internal object WifiSsidReading {
    fun visibleName(raw: String?): String? {
        val value = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        return value.takeIf {
            it.isNotEmpty() && it.length <= 48 && !it.equals("<unknown ssid>", ignoreCase = true) &&
                it.none { char -> char < ' ' || char == 127.toChar() }
        }
    }
}
