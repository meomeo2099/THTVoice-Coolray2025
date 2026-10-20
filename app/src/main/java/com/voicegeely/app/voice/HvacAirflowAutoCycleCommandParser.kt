package com.voicegeely.app.voice

/**
 * Parser for the automatic airflow cycle (tự động đảo gió).
 *
 * Only "đảo gió" wording is accepted; "tuần hoàn gió" / "gió trong" stay with
 * recirculation, and fan-direction phrases stay with FanDirection.
 */
object HvacAirflowAutoCycleCommandParser {
    private fun parsePeriodic(text: String, normalized: String): ParsedVoiceCommand? {
        val m = periodic.matchEntire(normalized) ?: return null
        val explicitPeriod = normalized.contains("hướng gió") || normalized.contains(" phút") ||
            normalized.endsWith("định kỳ") || normalized.contains("định kỳ ") || normalized.contains("liên tục")
        if (!explicitPeriod) return null // plain "bật đảo gió" stays the car's own auto cycle
        val verb = m.groupValues[1]
        val off = verb == "tắt" || verb == "ngắt" || verb == "dừng"
        val minutes = (m.groupValues[2].ifBlank { m.groupValues[3] }).toIntOrNull()
            ?: if (normalized.contains("mỗi phút") || normalized.contains("cứ phút")) 1 else 0
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = if (off) VoiceCommand.HvacAirflowAutoCycle(on = false, periodicMinutes = 0)
            else VoiceCommand.HvacAirflowAutoCycle(on = true, periodicMinutes = minutes)
        )
    }

    private val command = Regex(
        "^(?:(bật|mở|tắt|ngắt) )?(?:chế độ )?(?:đảo gió(?: tự động)?|tự động đảo gió)(?: giúp tôi)?(?: nhé| đi)?$"
    )

    /** Periodic swing (08/10/2026): "đảo gió mỗi 2 phút", "bật đảo hướng gió", "đảo gió 5 phút 1 lần". */
    private val periodic = Regex(
        "^(?:(bật|mở|tắt|ngắt|dừng) )?(?:chế độ )?(?:đảo hướng gió|đảo gió)" +
            "(?: (?:mỗi|sau mỗi|cứ) (?:(\\d+) )?phút(?: (?:một|1) lần)?| (\\d+) phút (?:một|1) lần| định kỳ| liên tục)?" +
            "(?: giúp tôi)?(?: nhé| đi)?$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        parsePeriodic(text, normalized)?.let { return it }
        val match = command.matchEntire(normalized) ?: return null
        val verb = match.groupValues[1]
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.HvacAirflowAutoCycle(on = verb != "tắt" && verb != "ngắt")
        )
    }
}
