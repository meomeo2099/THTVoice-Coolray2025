package com.voicegeely.app.parsers

object VietnameseNumberParser {
    fun hasClimateSetpoint(text: String): Boolean =
        Regex("nhiệt độ|điều hòa|điều hoà|máy lạnh", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            Regex("(?<!\\d)(?:1[6-9]|2[0-9]|3[0-2])(?!\\d)").containsMatchIn(text)

    fun isAmbiguousClimateHi(text: String): Boolean =
        Regex("nhiệt độ|điều hòa|điều hoà|máy lạnh", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            Regex("(?i)(?<![\\p{L}])hi(?![\\p{L}])").containsMatchIn(text) &&
            !Regex("\\d|(?i)hi\\s+mươi|hai\\s+mươi").containsMatchIn(text)

    private val numberMap = mapOf(
        "không" to 0, "một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "tư" to 4,
        "năm" to 5, "lăm" to 5, "sáu" to 6, "bảy" to 7, "tám" to 8, "chín" to 9,
        "mười" to 10
    )

    /**
     * "không" is both the Vietnamese digit zero and a normal lexical word.
     * Preserve high-confidence non-numeric phrases used by vehicle commands so
     * e.g. "sạc không dây" never becomes "sạc 0 dây" before intent parsing.
     */
    private val lexicalKhongFollowers = setOf("dây", "khí")

    fun parse(text: String): Int? {
        val words = text.lowercase().trim().split("\\s+".toRegex())
        var total = 0
        var current = 0
        for (word in words) {
            when {
                numberMap.containsKey(word) -> {
                    val value = numberMap[word]!!
                    if (value == 10) {
                        if (current == 0) current = 1
                        total += current * 10
                        current = 0
                    } else current += value
                }
                word == "mươi" -> { total += current * 10; current = 0 }
                word == "trăm" -> { total += current * 100; current = 0 }
            }
        }
        total += current
        return if (total > 0 || text.contains("không")) total else null
    }

    fun parseVietnameseNumber(text: String): String {
        val climateText = Regex("nhiệt độ|điều hòa|điều hoà|máy lạnh", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
        val normalized = if (climateText) {
            text
                // In HVAC context, users/ASR may emit the shorthand "hai hai"
                // for 22. Handle it before the generic additive number parser,
                // which would otherwise interpret "hai hai" as 2 + 2 = 4.
                .replace(Regex("(?i)(?<![\\p{L}])hai\\s+hai(?![\\p{L}])"), "hai mươi hai")
                .replace(Regex("(?i)(?<![\\p{L}])hi\\s+mười\\s+hi(?![\\p{L}])"), "hai mươi hai")
                .replace(Regex("(?i)(?<![\\p{L}])hi\\s+mười(?![\\p{L}])"), "hai mươi")
                .replace(Regex("(?i)(?<![\\p{L}])hi(?=\\s+mươi(?:\\s|$|[,.!?]))"), "hai")
                .replace(Regex("(?i)\\b(mười|mươi|trăm)\\s+hi(?![\\p{L}])"), "$1 hai")
        } else text
        val words = normalized.split("\\s+".toRegex())
        val result = mutableListOf<String>()
        val currentNumberSequence = mutableListOf<String>()
        val numberWords = numberMap.keys + setOf("mươi", "trăm")

        fun flushNumberSequence() {
            if (currentNumberSequence.isEmpty()) return
            val parsedInt = parse(currentNumberSequence.joinToString(" "))
            if (parsedInt != null) result.add(parsedInt.toString()) else result.addAll(currentNumberSequence)
            currentNumberSequence.clear()
        }

        words.forEachIndexed { index, word ->
            val normalizedWord = word.lowercase().replace("[,.?]".toRegex(), "")
            val nextWord = words.getOrNull(index + 1)
                ?.lowercase()
                ?.replace("[,.?]".toRegex(), "")
            val lexicalKhong = normalizedWord == "không" && nextWord in lexicalKhongFollowers

            if (normalizedWord in numberWords && !lexicalKhong) {
                currentNumberSequence.add(word)
            } else {
                flushNumberSequence()
                result.add(word)
            }
        }
        flushNumberSequence()
        return result.joinToString(" ")
    }
}
