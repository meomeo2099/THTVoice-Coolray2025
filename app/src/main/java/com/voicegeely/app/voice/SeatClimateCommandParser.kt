package com.voicegeely.app.voice

/**
 * Parser for Coolray front-seat heating and ventilation (REFERENCE: GalaxyVoiceAssistant
 * SeatClimateHandler). Behaviour follows the reference:
 * - "bật" without a level = level 2; "tắt" = 0; "mức 1..3", "thấp nhất" = 1, "cao nhất" = 3;
 * - "tăng" / "giảm" change the current level by one (clamped 1..3 by the executor);
 * - no seat named = driver seat; "hai ghế" / "cả hai" / "ghế trước" = both front seats.
 * A seat word is required ("sưởi gương/kính" stays a defrost command, "thông gió" alone stays
 * recirculation). Levels above 3 fail closed.
 */
object SeatClimateCommandParser {
    // "sưởi 2 ghế", "thông gió cả 2 ghế": the heat/vent word and "ghế" may be separated by seat words.
    private val seatWord = Regex("(?:^| )ghế(?: |$)")
    private val heat = Regex("sưởi|làm ấm|ghế ấm")
    private val vent = Regex("thông gió|làm mát|quạt ghế|ghế mát|thổi mát")
    private val notSeat = Regex("gương|kính|điều hòa|nhiệt độ")
    private val both = Regex("hai ghế|2 ghế|cả hai|cả 2|tất cả ghế|các ghế|ghế trước|hàng ghế trước")
    private val driver = Regex("ghế lái|ghế tài|bên lái|tài xế|người lái")
    private val passenger = Regex("ghế phụ|bên phụ|hành khách|ghế bên cạnh")
    private val off = Regex("(?:^| )(?:tắt|ngắt)(?: |$)")
    private val on = Regex("(?:^| )(?:bật|mở)(?: |$)")
    private val increase = Regex("(?:^| )(?:tăng|nóng hơn|mát hơn|mạnh hơn)(?: |$)")
    private val decrease = Regex("(?:^| )(?:giảm|bớt|nhẹ hơn|yếu hơn)(?: |$)")
    private val max = Regex("cao nhất|tối đa|mạnh nhất|lớn nhất|hết cỡ")
    private val min = Regex("thấp nhất|tối thiểu|nhẹ nhất|yếu nhất|nhỏ nhất")
    private val mid = Regex("trung bình|vừa phải|mức vừa")
    private val number = Regex("(?<!\\d)\\d+(?!\\d)")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        if (!seatWord.containsMatchIn(normalized) || notSeat.containsMatchIn(normalized)) return null
        val kind = when {
            heat.containsMatchIn(normalized) && !vent.containsMatchIn(normalized) -> SeatClimateKind.HEAT
            vent.containsMatchIn(normalized) && !heat.containsMatchIn(normalized) -> SeatClimateKind.VENT
            else -> return null
        }
        val seats = when {
            both.containsMatchIn(normalized) -> setOf(SeatTarget.DRIVER, SeatTarget.PASSENGER)
            driver.containsMatchIn(normalized) && passenger.containsMatchIn(normalized) -> setOf(SeatTarget.DRIVER, SeatTarget.PASSENGER)
            passenger.containsMatchIn(normalized) -> setOf(SeatTarget.PASSENGER)
            else -> setOf(SeatTarget.DRIVER)
        }
        val numbers = number.findAll(normalized).mapNotNull { it.value.toIntOrNull() }
            .filter { it != 2 || !Regex("(?:^| )2 ghế").containsMatchIn(normalized) }.toList()
        if (numbers.size > 1 || numbers.any { it !in 1..3 }) return null

        val command = when {
            off.containsMatchIn(normalized) -> VoiceCommand.SeatClimate(kind, seats, level = 0)
            increase.containsMatchIn(normalized) && !decrease.containsMatchIn(normalized) ->
                numbers.singleOrNull()?.let { VoiceCommand.SeatClimate(kind, seats, level = it) }
                    ?: if (max.containsMatchIn(normalized)) VoiceCommand.SeatClimate(kind, seats, level = 3)
                    else VoiceCommand.SeatClimate(kind, seats, delta = 1)
            decrease.containsMatchIn(normalized) && !increase.containsMatchIn(normalized) ->
                numbers.singleOrNull()?.let { VoiceCommand.SeatClimate(kind, seats, level = it) }
                    ?: if (min.containsMatchIn(normalized)) VoiceCommand.SeatClimate(kind, seats, level = 1)
                    else VoiceCommand.SeatClimate(kind, seats, delta = -1)
            numbers.isNotEmpty() -> VoiceCommand.SeatClimate(kind, seats, level = numbers.single())
            max.containsMatchIn(normalized) -> VoiceCommand.SeatClimate(kind, seats, level = 3)
            min.containsMatchIn(normalized) -> VoiceCommand.SeatClimate(kind, seats, level = 1)
            mid.containsMatchIn(normalized) -> VoiceCommand.SeatClimate(kind, seats, level = 2)
            on.containsMatchIn(normalized) -> VoiceCommand.SeatClimate(kind, seats, level = 2)
            else -> return null
        }
        return ParsedVoiceCommand(originalText = text, normalizedText = normalized, command = command)
    }
}
