package com.voicegeely.app.kws

/**
 * "Dạy từ gọi" (R03 WakeEnrollment): from a few takes of the driver saying the wake phrase, the
 * keyword lines that match how the keyword spotter actually hears this voice. A decoded line is
 * kept only if it is long enough and the spotter, given that line alone, also hears it in at least
 * two OTHER takes — one odd take cannot teach a generic sound. Pure Kotlin over two callbacks.
 */
object WakeEnrollment {
    const val TAKES = 5
    const val MIN_TAKES = 3
    const val MIN_TOKENS = 3
    const val MAX_TOKENS = 14
    const val MIN_CROSS_HITS = 2
    const val MAX_LINES = 6
    const val BOOST = 2.5f
    const val THRESHOLD = 0.20f

    sealed class Result {
        data class Learned(val lines: List<KeywordLines.Line>, val takesHeard: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * @param decoded tokens the spotter's transducer heard in each take (free greedy decoding)
     * @param hears   does the spotter, with only [line] as extra keyword, fire on take [take]?
     */
    fun learn(decoded: List<List<String>>, label: String, hears: (KeywordLines.Line, Int) -> Boolean): Result {
        val takes = decoded.withIndex().filter { it.value.isNotEmpty() }
        if (takes.size < MIN_TAKES) return Result.Failed("Không nghe rõ đủ $MIN_TAKES lần gọi. Hãy nói to, rõ sau mỗi tiếng bíp, trong xe yên tĩnh.")
        val candidates = LinkedHashMap<List<String>, Int>() // tokens -> source take
        for ((i, toks) in takes) {
            val trimmed = toks.filter { it.isNotBlank() && it != "<blk>" && it != "<unk>" }
            if (trimmed.size in MIN_TOKENS..MAX_TOKENS) candidates.putIfAbsent(trimmed, i)
        }
        val kept = mutableListOf<Pair<KeywordLines.Line, Int>>()
        for ((tokens, source) in candidates) {
            val line = KeywordLines.Line(tokens, BOOST, THRESHOLD, label)
            val hits = takes.count { (i, _) -> i != source && runCatching { hears(line, i) }.getOrDefault(false) }
            if (hits >= MIN_CROSS_HITS) kept += line to hits
        }
        if (kept.isEmpty()) return Result.Failed("Chưa học được giọng. Hãy thử lại ở nơi yên tĩnh và nói giống cách bạn hay gọi.")
        val best = kept.sortedByDescending { it.second }.take(MAX_LINES)
        val heard = takes.count { (i, _) -> best.any { (line, _) -> runCatching { hears(line, i) }.getOrDefault(false) } }
        return Result.Learned(best.map { it.first }, heard)
    }
}
