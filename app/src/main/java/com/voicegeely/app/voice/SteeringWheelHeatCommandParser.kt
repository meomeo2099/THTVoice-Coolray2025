package com.voicegeely.app.voice

/**
 * "bật sưởi vô lăng", "tắt sưởi vô lăng", "làm ấm tay lái", "vô lăng lạnh quá".
 * A steering-wheel word and a heat word are both required; seat phrases stay with
 * SeatClimateCommandParser. Steering wheel heat is on/off only (REF-WIDGET maxLevel = 1).
 */
object SteeringWheelHeatCommandParser {
    private val wheel = Regex("vô lăng|tay lái")
    private val heat = Regex("sưởi|làm ấm|ấm|lạnh")
    private val seat = Regex("(?:^| )ghế(?: |$)")
    private val off = Regex("(?:^| )(?:tắt|ngắt|thôi)(?: |$)")
    private val on = Regex("(?:^| )(?:bật|mở)(?: |$)|lạnh quá|lạnh tay|làm ấm")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        if (!wheel.containsMatchIn(normalized) || !heat.containsMatchIn(normalized) || seat.containsMatchIn(normalized)) return null
        val command = when {
            off.containsMatchIn(normalized) -> VoiceCommand.SteeringWheelHeat(false)
            on.containsMatchIn(normalized) -> VoiceCommand.SteeringWheelHeat(true)
            Regex("^sưởi (?:vô lăng|tay lái)$").matches(normalized) -> VoiceCommand.SteeringWheelHeat(true)
            else -> return null
        }
        return ParsedVoiceCommand(originalText = text, normalizedText = normalized, command = command)
    }
}
