package com.voicegeely.app.voice

/**
 * High-confidence parser helper for the parked-only rear-trunk access intent.
 *
 * The resulting command means unlock vehicle access so the physical rear-tailgate
 * button can be used. It does not mean the rear-tailgate latch is already open.
 * This helper stays isolated from [VoiceCommandParser] until a later slice.
 */
object RearTrunkCommandParser {
    private val rearTrunk = Regex(
        "^mở (?:(?:khóa|khoá) )?(?:cốp|cốp xe) (?:sau|phía sau|đằng sau)$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank() || !rearTrunk.matches(normalized)) return null

        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.UnlockRearTrunk
        )
    }
}
