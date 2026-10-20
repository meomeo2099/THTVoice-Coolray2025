package com.voicegeely.app.voice

/**
 * High-confidence parser helper for parked-only Intelligent High Beam control.
 *
 * Generic manual high-beam phrases such as "bật đèn pha" are deliberately not
 * accepted. The utterance must explicitly say automatic/intelligent high beam.
 */
object IntelligentHighBeamCommandParser {
    private val command = Regex(
        "^(bật|tắt) (?:(?:đèn )?pha tự động|đèn pha thông minh|đèn chiếu xa tự động|đèn chiếu xa thông minh)$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val match = command.matchEntire(normalized) ?: return null
        val enabled = match.groupValues[1] == "bật"
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.SetIntelligentHighBeam(enabled)
        )
    }
}
