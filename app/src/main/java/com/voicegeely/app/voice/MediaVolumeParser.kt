package com.voicegeely.app.voice

/**
 * Owner request 15/10/2026: "âm lượng 15", "chỉnh loa lên 20", "âm lượng 50 phần trăm",
 * "âm lượng tối đa / nhỏ nhất", "tăng âm lượng 3 nấc", "giảm loa 2 mức".
 * Works on normalized text (Vietnamese numbers already digits). A volume word is required, so fan,
 * temperature and brightness sentences are never touched. Out-of-range numbers → null (no guess).
 * "tăng/giảm âm lượng N" without a unit: N ≤ [MAX_BARE_STEPS] = steps, larger N = that level.
 */
object MediaVolumeParser {
    const val MAX_STEPS = 10
    const val MAX_BARE_STEPS = 5

    private const val VOL = "(?:âm lượng|volume|loa|âm thanh|tiếng loa)"
    private const val VERB = "(?:chỉnh|để|cho|đặt|vặn|bật|mở|đưa|tăng|giảm|hạ|set)"
    private const val UNIT = "(?:nấc|mức|bậc|vạch|cấp)"

    private val absolute = Regex(
        "^(?:($VERB) )?$VOL((?: (?:lên|xuống|về|ở|đến|tới|thành|mức|số|nấc|bằng|cấp))*) (\\d{1,3})(?: ?(phần trăm|%))?$"
    )
    private val steps = Regex("^(tăng|giảm|hạ) $VOL(?: (?:lên|xuống|thêm|bớt|đi))? (\\d{1,2}) $UNIT(?: nữa)?$")
    private val extreme = Regex(
        "^(?:$VERB )?$VOL(?: (?:lên|xuống|về|ở|mức))* (tối đa|lớn nhất|to nhất|cao nhất|max|hết cỡ|nhỏ nhất|bé nhất|thấp nhất|tối thiểu)$"
    )

    /** A volume-by-number sentence whose number is out of range: rejected, never "tăng âm lượng" by 1. */
    fun outOfRange(normalized: String): Boolean {
        val t = normalized.trim().replace(Regex("\\s+"), " ")
        return (steps.matches(t) || absolute.matches(t)) && parse(t) == null
    }

    fun parse(normalized: String): VoiceCommand? {
        val t = normalized.trim().replace(Regex("\\s+"), " ")
        steps.matchEntire(t)?.let { m ->
            val n = m.groupValues[2].toInt()
            if (n !in 1..MAX_STEPS) return null
            return VoiceCommand.AdjustMediaVolume(if (m.groupValues[1] == "tăng") n else -n)
        }
        extreme.matchEntire(t)?.let { m ->
            return when (m.groupValues[1]) {
                "nhỏ nhất", "bé nhất", "thấp nhất", "tối thiểu" -> VoiceCommand.SetMediaVolume(1)
                else -> VoiceCommand.SetMediaVolume(100, VolumeUnit.PERCENT)
            }
        }
        absolute.matchEntire(t)?.let { m ->
            val verb = m.groupValues[1]
            val filler = m.groupValues[2].trim()
            val n = m.groupValues[3].toInt()
            val percent = m.groupValues[4].isNotEmpty()
            if (percent) return if (n in 0..100) VoiceCommand.SetMediaVolume(n, VolumeUnit.PERCENT) else null
            if (n > 100) return null
            val relative = verb in setOf("tăng", "giảm", "hạ") && filler.isEmpty()
            if (relative && n in 1..MAX_BARE_STEPS) return VoiceCommand.AdjustMediaVolume(if (verb == "tăng") n else -n)
            return VoiceCommand.SetMediaVolume(n)
        }
        return null
    }
}
