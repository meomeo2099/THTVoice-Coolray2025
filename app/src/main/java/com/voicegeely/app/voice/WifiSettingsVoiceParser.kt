package com.voicegeely.app.voice

/**
 * Explicit parser for opening Android Wi-Fi settings. It intentionally does not
 * interpret `bật/tắt Wi-Fi` as a settings-open or radio-toggle command.
 */
object WifiSettingsVoiceParser {
    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val explicitOpenSettings = Regex(
            "^(?:mở|hiện|vào)\\s+(?:phần\\s+|màn\\s+)?(?:cài đặt|settings)\\s+(?:wi ?fi|mạng wi ?fi)$|" +
                "^(?:mở|hiện)\\s+(?:bảng|trang|màn hình)\\s+(?:wi ?fi|cài đặt wi ?fi)$"
        )
        if (!explicitOpenSettings.matches(normalized)) return null
        return ParsedVoiceCommand(text, normalized, VoiceCommand.OpenWifiSettings)
    }
}
