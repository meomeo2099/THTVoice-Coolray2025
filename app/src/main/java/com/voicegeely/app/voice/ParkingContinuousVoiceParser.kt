package com.voicegeely.app.voice

/**
 * High-confidence compatibility parser for the documented continuous parking mode.
 *
 * Kept separate from the broader generic parser so the Part 5 reference-backed
 * slice stays narrow and cannot steal unrelated parking phrases.
 */
object ParkingContinuousVoiceParser {
    private val parkingContext = Regex("chế độ đỗ xe|parking mode")
    private val continuous = Regex("liên tục|không giới hạn|vô hạn|continuous")
    private val off = Regex("(?:^| )(?:tắt|off)(?: |$)")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        if (!parkingContext.containsMatchIn(normalized)) return null
        if (!continuous.containsMatchIn(normalized)) return null

        val duration = if (off.containsMatchIn(normalized)) {
            ParkingModeDuration.OFF
        } else {
            ParkingModeDuration.CONTINUOUS
        }
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.SetParkingModeDuration(duration)
        )
    }
}
