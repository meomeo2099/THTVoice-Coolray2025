package com.voicegeely.app.voice

/**
 * Parser for the Coolray sunroof glass and sunshade.
 *
 * Runs before the door-window parser because "cửa sổ trời" contains "cửa sổ".
 * - Glass: "cửa sổ trời", "cửa trời", "kính trần", "kính nóc", "sunroof".
 * - Shade: "rèm" / "tấm che nắng" (Coolray has only the sunroof shade).
 * Percent is the open position: "mở cửa sổ trời 50 phần trăm" = 50, "hé" = a crack (10 %,
 * snapped to the reference minimum by the executor). "Dừng" and out-of-range numbers fail closed.
 */
object SunroofCommandParser {
    private const val CRACK_PERCENT = 10

    private val glassContext = Regex("cửa sổ trời|cửa trời|kính trần|kính nóc|cửa nóc|sunroof|sun roof")
    private val shadeContext = Regex("(?:^| )rèm(?: |$)|tấm che nắng|che nắng trần|che nắng nóc")
    private val openVerb = Regex("(?:^| )(?:mở|hé|kéo ra|mở ra)(?: |$)")
    private val crackVerb = Regex("(?:^| )hé(?: |$)")
    private val closeVerb = Regex("(?:^| )(?:đóng|kéo lại|khép)(?: |$)")
    private val stopVerb = Regex("(?:^| )(?:dừng|ngừng|stop)(?: |$)")
    private val halfOpen = Regex("(?:^| )(?:một nửa|nửa chừng|phân nửa)(?: |$)")
    private val percentPattern = Regex("(?<!\\d)(\\d{1,3})(?!\\d)\\s*(?:%|phần trăm)")
    private val anyNumber = Regex("(?<!\\d)\\d+(?!\\d)")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val command = parseNormalized(normalized, text) ?: return null
        return ParsedVoiceCommand(originalText = text, normalizedText = normalized, command = command)
    }

    private fun parseNormalized(text: String, originalText: String): VoiceCommand.Sunroof? {
        val part = when {
            shadeContext.containsMatchIn(text) -> SunroofPart.SHADE
            glassContext.containsMatchIn(text) -> SunroofPart.GLASS
            else -> return null
        }
        if (stopVerb.containsMatchIn(text)) return null
        if (Regex("(?<!\\d)[-−]\\s*\\d+|\\d+[.,]\\d+").containsMatchIn(originalText)) return null

        val numbers = anyNumber.findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.any { it > 100 }) return null
        val percent = percentPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (halfOpen.containsMatchIn(text)) 50 else null
        if (percent == null && numbers.isNotEmpty()) return null

        val open = openVerb.containsMatchIn(text)
        val close = closeVerb.containsMatchIn(text)
        if (open == close) return null

        return when {
            percent != null -> VoiceCommand.Sunroof(part, WindowOperation.SET_PERCENT, percent)
            crackVerb.containsMatchIn(text) -> VoiceCommand.Sunroof(part, WindowOperation.SET_PERCENT, CRACK_PERCENT)
            open -> VoiceCommand.Sunroof(part, WindowOperation.OPEN, 100)
            else -> VoiceCommand.Sunroof(part, WindowOperation.CLOSE, 0)
        }
    }
}
