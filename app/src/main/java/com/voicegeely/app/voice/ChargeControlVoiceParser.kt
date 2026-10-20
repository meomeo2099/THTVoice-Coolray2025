package com.voicegeely.app.voice

/**
 * High-confidence parser for wired vehicle charging control only.
 *
 * RESTART/resume phrases map to the reference-backed adapted value 605028610.
 * Wireless-phone charging remains a separate command family.
 */
object ChargeControlVoiceParser {
    private val restartLike = Regex(
        "khởi động lại sạc|bắt đầu lại sạc|tiếp tục sạc|resume charging|restart charging|restart charge"
    )

    private val stop = Regex(
        "^(?:dừng|ngừng)\\s+(?:quá trình\\s+)?sạc(?:\\s+(?:xe|pin xe))?$|" +
            "^(?:dừng|ngừng)\\s+sạc\\s+xe$|^stop\\s+charging$"
    )

    private val startNow = Regex(
        "^(?:bắt đầu|khởi động)\\s+sạc(?:\\s+(?:xe|pin xe))?\\s+(?:ngay|ngay bây giờ)$|" +
            "^sạc(?:\\s+(?:xe|pin xe))?\\s+ngay(?:\\s+bây giờ)?$|^start\\s+charging\\s+now$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val action = when {
            restartLike.matches(normalized) -> ChargeControlAction.RESTART
            stop.matches(normalized) -> ChargeControlAction.STOP
            startNow.matches(normalized) -> ChargeControlAction.START_NOW
            else -> return null
        }

        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.SetChargeCurrentLimit(amps = 0, control = action)
        )
    }
}
