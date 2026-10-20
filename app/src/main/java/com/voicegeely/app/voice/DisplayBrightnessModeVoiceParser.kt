package com.voicegeely.app.voice

/**
 * High-confidence parser for Android system brightness mode only.
 *
 * This deliberately does not match vehicle display theme phrases such as
 * "giao diện tự động" or generic "màn hình tối/sáng".
 */
object DisplayBrightnessModeVoiceParser {
    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val mode = when {
            Regex("^(?:bật|mở) độ sáng tự động$").matches(normalized) -> DisplayBrightnessMode.AUTOMATIC
            Regex("^(?:chuyển|đổi) (?:độ sáng|màn hình) (?:sang|qua) (?:chế độ )?tự động$").matches(normalized) -> DisplayBrightnessMode.AUTOMATIC
            Regex("^đặt (?:độ sáng|màn hình) (?:ở )?(?:chế độ )?tự động$").matches(normalized) -> DisplayBrightnessMode.AUTOMATIC
            Regex("^(?:tắt|đóng) độ sáng tự động$").matches(normalized) -> DisplayBrightnessMode.MANUAL
            Regex("^(?:chuyển|đổi) (?:độ sáng|màn hình) (?:sang|qua) (?:chế độ )?thủ công$").matches(normalized) -> DisplayBrightnessMode.MANUAL
            Regex("^đặt (?:độ sáng|màn hình) (?:ở )?(?:chế độ )?thủ công$").matches(normalized) -> DisplayBrightnessMode.MANUAL
            else -> return null
        }

        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.SetManualDisplayBrightness(percent = 0, mode = mode)
        )
    }
}
