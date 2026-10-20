package com.voicegeely.app.voice

/**
 * High-confidence parser family for parked-only door lock and trunk commands.
 *
 * This parser intentionally accepts only explicit whole-utterance aliases.
 * Generic phrases such as "mở cửa" or "mở cốp" remain fail-closed so they
 * cannot collide with window/door/trunk behavior.
 */
object DoorTrunkCommandParser {
    private val lock = Regex(
        "^(?:khóa|khoá) (?:(?:toàn bộ|tất cả) )?(?:cửa|cửa xe|xe)$"
    )
    private val unlock = Regex(
        "^mở (?:khóa|khoá)(?: (?:(?:toàn bộ|tất cả) )?(?:cửa|cửa xe|xe))?$"
    )
    private val frontTrunk = Regex(
        "^mở (?:(?:cốp|cốp xe) (?:trước|phía trước|đằng trước)|(?:nắp )?(?:capo|ca pô))$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        RearTrunkCommandParser.parse(text)?.let { return it }

        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val command = when {
            frontTrunk.matches(normalized) -> VoiceCommand.OpenFrontTrunk
            unlock.matches(normalized) -> VoiceCommand.SetDoorLock(locked = false)
            lock.matches(normalized) -> VoiceCommand.SetDoorLock(locked = true)
            else -> return null
        }
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = command
        )
    }
}
