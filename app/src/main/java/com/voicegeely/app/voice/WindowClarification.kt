package com.voicegeely.app.voice

/**
 * Owner decision 14/10/2026: "mở kính" / "đóng kính" without saying which window → ask
 * "Kính nào? …" and take the next short answer ("kính lái", "bên phụ", "sau", "tất cả"…) within
 * [WINDOW_MS]. A percentage said first is kept ("mở kính 50 phần trăm" → "mở kính lái 50 phần trăm").
 * Never guesses a window; "mở/đóng tất cả kính", "mở hết kính" still work directly.
 */
class WindowClarification(
    private val parse: (String) -> ParsedVoiceCommand? = VoiceParseChain::parse,
    private val windowMs: Long = WINDOW_MS
) {
    private data class Pending(val verb: String, val percentTail: String, val atMs: Long)
    private var pending: Pending? = null

    /** The question when [text] is an open/close-window sentence without a window; null otherwise. */
    @Synchronized
    fun check(text: String, nowMs: Long): String? {
        val t = VoiceTextNormalizer.normalize(text)
        val m = AMBIGUOUS.matchEntire(t) ?: return null
        val word = m.groupValues[1]
        val direction = m.groupValues[2]
        val verb = when {
            word == "mở" || word == "hạ" -> "mở"
            word == "đóng" || word == "nâng" -> "đóng"
            direction == "lên" -> "đóng"
            direction == "xuống" -> "mở"
            else -> return null
        }
        val percent = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { " $it phần trăm" }.orEmpty()
        pending = Pending(verb, percent, nowMs)
        return question(verb)
    }

    /** The full command for an answer to the question, or null. The question is cleared either way. */
    @Synchronized
    fun resolve(text: String, nowMs: Long): ParsedVoiceCommand? {
        val p = pending ?: return null
        pending = null
        if (nowMs < p.atMs || nowMs - p.atMs > windowMs) return null
        var answer = VoiceTextNormalizer.normalize(text)
            .replace(Regex("^(?:mở|đóng|hạ|nâng) "), "")
            .replace(Regex("^(?:cửa )?(?:kính|kiếng|kinh|kín) "), "")
            .replace(Regex("^(?:kính|kiếng|kinh|kín)$"), "")
            .trim()
        ANSWER_SLIPS.forEach { (re, to) -> answer = re.replace(answer, to) }
        if (answer.isEmpty()) return null
        val sentence = if (ALL.matches(answer)) "${p.verb} tất cả kính${p.percentTail}" else "${p.verb} kính $answer${p.percentTail}"
        val parsed = parse(sentence) ?: return null
        return (parsed.command as? VoiceCommand.Window)?.let { ParsedVoiceCommand(text, parsed.normalizedText, it) }
    }

    @Synchronized
    fun clear() { pending = null }

    companion object {
        const val WINDOW_MS = 20_000L
        /** Owner 15/10/2026: short question, no list of answers. */
        fun question(verb: String) = "Bạn cần $verb kính nào?"

        private const val WB = "(?<![\\p{L}\\p{N}])"
        private const val WE = "(?![\\p{L}\\p{N}])"
        /** Answer words the ASR mishears (Northern l/n, tr/ch; Southern s/x, ph/f; tones). */
        private val ANSWER_SLIPS = listOf(
            Regex("${WB}(?:nái|lài|lãi|lải|lại|lấy|nãi)$WE") to "lái",
            Regex("${WB}(?:phủ|phú|phù|phũ|fụ|fu|phu|bụ|vụ)$WE") to "phụ",
            Regex("${WB}(?:xau|sao|xao)$WE") to "sau",
            Regex("${WB}(?:chước|trướt|trức|chướt|trưới)$WE") to "trước",
            Regex("${WB}(?:chái|chải|trãi|trải|chại)$WE") to "trái",
            Regex("${WB}(?:phãi|phái|fải|phai|phại)$WE") to "phải",
            Regex("^(?:tấc|tắc|tát|tật|tấp|tắt) (?:cả|cã|ca|cà|kả)$") to "tất cả",
            Regex("^(?:hét|hếch|hếc|hệt|hến)$") to "hết",
            Regex("^cả (?:bố|bổ|bộ|bón|bốn)$") to "cả 4"
        )
        private val AMBIGUOUS = Regex("^(mở|đóng|hạ|nâng|kéo|quay)(?: cửa)? kính(?: (lên|xuống|ra|lại))?(?: (\\d{1,3}) phần trăm)?$")
        private val ALL = Regex("^(?:tất cả|tắt cả|hết|cả|cả 4|cả bốn|toàn bộ|4 kính|bốn kính|tất cả kính|tắt cả kính|hết kính|cả 4 kính|hết luôn)(?: kính)?$")
    }
}
