package com.voicegeely.app.voice

import java.text.Normalizer

/**
 * Owner 16/10/2026: when a sentence is not understood, offer the closest known command instead of
 * only "không hiểu": "Ý bạn là "mở kính lái" phải không?". Nothing runs until the driver says yes
 * ("đúng", "ừ", "có"…) within [WINDOW_MS]; the accepted phrase then goes through the normal
 * pipeline (parser, safety gate, read-back). Any other answer clears the question.
 *
 * Closeness is measured on the sentence without tone marks (ASR mostly gets tones/letters wrong),
 * and a suggestion is made only when one known phrase is clearly the closest.
 */
class CommandSuggestion(
    private val parse: (String) -> ParsedVoiceCommand? = VoiceParseChain::parse,
    private val windowMs: Long = WINDOW_MS
) {
    sealed class Answer {
        data class Accept(val phrase: String) : Answer()
        object Decline : Answer()
    }

    private var pending: Pair<String, Long>? = null

    /** The question to ask for [text], or null when nothing is close enough. Remembers the phrase. */
    @Synchronized
    fun suggest(text: String, extraPhrases: List<String>, nowMs: Long, cancelled: () -> Boolean = { false }): String? {
        pending = null
        val phrase = best(text, CommandCatalog.PHRASES + extraPhrases, cancelled) ?: return null
        if (Thread.currentThread().isInterrupted || cancelled()) return null
        pending = phrase to nowMs
        return question(phrase)
    }

    /** The answer to a pending question, or null (no question, too late, or another sentence). */
    @Synchronized
    fun resolve(text: String, nowMs: Long): Answer? {
        val (phrase, at) = pending ?: return null
        pending = null
        if (nowMs < at || nowMs - at > windowMs) return null
        val t = FollowUpContext.normalize(text)
        return when {
            YES.matches(t) -> Answer.Accept(phrase)
            NO.matches(t) -> Answer.Decline
            else -> null
        }
    }

    @Synchronized
    fun clear() { pending = null }

    /** Closest known phrase that parses, or null. */
    fun best(text: String, phrases: List<String>, cancelled: () -> Boolean = { false }): String? {
        val started = System.nanoTime()
        fun stopped() = Thread.currentThread().isInterrupted || cancelled() || System.nanoTime() - started > SEARCH_BUDGET_NS
        if (stopped()) return null
        val heard = plain(VoiceTextNormalizer.normalize(text))
        if (heard.length < MIN_LEN || heard.split(' ').size > MAX_WORDS) return null
        // Score cheaply before invoking the parse chain (which may load aliases/scenarios).
        val scored = phrases.distinct().map { p ->
            if (stopped()) return null
            p to similarity(heard, plain(VoiceTextNormalizer.normalize(p)))
        }.sortedByDescending { it.second }
        var top: Triple<String, VoiceCommand, Double>? = null
        for ((phrase, score) in scored) {
            if (stopped()) return null
            val selected = top
            if (selected == null && score < MIN_SIMILARITY) break
            if (selected != null && selected.third - score >= MIN_MARGIN) break
            val command = parse(phrase)?.command ?: continue
            if (stopped()) return null
            if (selected == null) top = Triple(phrase, command, score)
            else if (command != selected.second) return null
        }
        return if (stopped()) null else top?.first
    }

    companion object {
        const val WINDOW_MS = 15_000L
        const val MIN_SIMILARITY = 0.75
        const val MIN_MARGIN = 0.06
        const val MIN_LEN = 5
        const val MAX_WORDS = 8
        private const val SEARCH_BUDGET_NS = 750_000_000L

        fun question(phrase: String) = "Ý bạn là \"$phrase\" phải không?"
        const val DECLINED = "Vâng, bạn nói lại lệnh nhé."

        private val YES = Regex("^(?:đúng|đúng rồi|đúng vậy|đúng đó|phải|phải rồi|phải đó|ừ|ừ đúng|ờ|ờ đúng|ừm|có|có đúng|ok|oke|okay|được|được rồi|chuẩn|chuẩn rồi|làm đi|đồng ý|xác nhận)$")
        private val NO = Regex("^(?:không|không phải|sai|sai rồi|không đúng|chưa đúng|thôi|hủy|huỷ|bỏ đi|không cần)$")

        /** Lowercase, no tone marks, đ→d, single spaces. */
        fun plain(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace('đ', 'd')
                .replace(Regex("[^a-z0-9 ]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val d = levenshtein(a, b)
            return 1.0 - d.toDouble() / maxOf(a.length, b.length)
        }

        private fun levenshtein(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}

/** Everyday command phrases offered as suggestions (each must parse; see CommandSuggestionTest). */
object CommandCatalog {
    val PHRASES: List<String> = listOf(
        // Climate.
        "bật điều hòa", "tắt điều hòa", "tăng nhiệt độ", "giảm nhiệt độ", "nhiệt độ cao nhất", "nhiệt độ thấp nhất",
        "tăng quạt", "giảm quạt", "quạt mức 3", "gió vào mặt", "gió xuống chân", "lấy gió trong", "lấy gió ngoài",
        "sấy kính trước", "sấy kính sau", "sấy gương", "bật điều hòa tiết kiệm", "tắt điều hòa tiết kiệm",
        "bật điều hòa tự động", "tắt điều hòa tự động", "chỉnh quạt tự động sang êm",
        // Windows / doors.
        "mở tất cả kính", "đóng tất cả kính", "mở kính lái", "đóng kính lái", "mở kính phụ", "đóng kính phụ",
        "mở kính sau", "đóng kính sau", "hé kính", "nghiêng cửa sổ trời",
        // Driving.
        "chế độ lái eco", "chế độ lái sport", "chế độ lái comfort",
        "bật auto hold", "tắt auto hold", "bật start stop", "tắt start stop", "bật đèn ban ngày", "tắt đèn ban ngày",
        // Media.
        "mở nhạc", "tạm dừng nhạc", "tiếp tục phát", "bài tiếp theo", "bài trước", "tăng âm lượng", "giảm âm lượng",
        "tắt tiếng", "bật tiếng", "mở youtube", "mở spotify", "mở zing mp3",
        // Navigation / apps / screen.
        "mở bản đồ", "mở google maps", "về màn hình chính", "màn hình đen",
        // Questions.
        "bình xăng còn bao nhiêu", "quãng đường còn lại", "mấy giờ rồi", "hôm nay thứ mấy", "nhiệt độ ngoài xe",
        "nhiệt độ trong xe", "tốc độ bao nhiêu", "tình trạng xe"
    )
}
