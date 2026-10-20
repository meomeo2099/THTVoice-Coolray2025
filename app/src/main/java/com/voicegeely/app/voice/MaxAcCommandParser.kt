package com.voicegeely.app.voice

/**
 * Coolray Max A/C (làm lạnh nhanh / làm lạnh tối đa / max A/C). REF-SYSUI: 354419974 area 117.
 * "nhiệt độ thấp nhất", "lạnh nhất" stay the LO setpoint; only the cooling-mode wording is taken.
 */
object MaxAcCommandParser {
    private val phrase = Regex(
        "^(?:(bật|mở|tắt|ngắt|thôi) )?(?:chế độ )?" +
            "(?:làm lạnh (?:nhanh|cực nhanh|tối đa|tốc độ)|lạnh cực nhanh|max (?:a c|ac|a\\/c|điều hòa))" +
            "(?: (?:lên|đi|giúp tôi|nhé|nha|giùm|dùm))*$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val m = phrase.matchEntire(normalized) ?: return null
        val verb = m.groupValues[1]
        val on = verb != "tắt" && verb != "ngắt" && verb != "thôi"
        return ParsedVoiceCommand(originalText = text, normalizedText = normalized, command = VoiceCommand.MaxAc(on))
    }
}
