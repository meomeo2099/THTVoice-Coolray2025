package com.voicegeely.app.voice

/**
 * Parser for HVAC Eco (điều hòa tiết kiệm).
 *
 * The utterance must name the air conditioning ("điều hòa"; "máy lạnh" is normalized
 * to it) together with "tiết kiệm"/"eco", so drive-mode phrases such as
 * "chế độ tiết kiệm" or "chế độ lái eco" never reach this parser's result.
 */
object HvacEcoCommandParser {
    private const val ECO = "(?:tiết kiệm|eco)"
    private val command = Regex(
        "^(?:(bật|mở|tắt|ngắt) )?(?:chế độ )?(?:điều hòa $ECO|$ECO điều hòa)(?: giúp tôi)?(?: nhé| đi)?$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val match = command.matchEntire(normalized) ?: return null
        val verb = match.groupValues[1]
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.HvacEco(on = verb != "tắt" && verb != "ngắt")
        )
    }
}
