package com.voicegeely.app.voice

import com.voicegeely.app.parsers.VietnameseNumberParser

/**
 * High-confidence parser helper for parked-only headlight-height commands.
 *
 * This helper intentionally requires an explicit height phrase, an explicit
 * front/headlight target, and a level in the supported 0..5 range. Generic
 * lighting phrases are left untouched so they cannot collide with ambient
 * lighting or Intelligent High Beam commands.
 */
object HeadlightHeightCommandParser {
    private val heightCommand = Regex(
        "^(?:đặt|chỉnh) (?:(?:độ cao|chiều cao) (?:đèn trước|đèn pha)|(?:đèn trước|đèn pha) (?:độ cao|chiều cao)) (?:về )?(?:mức|cấp) ([0-5])$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val numeric = VietnameseNumberParser.parseVietnameseNumber(normalized)
        val match = heightCommand.matchEntire(numeric) ?: return null
        val level = match.groupValues[1].toIntOrNull() ?: return null
        if (level !in 0..5) return null

        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.SetHeadlightHeight(level)
        )
    }
}
