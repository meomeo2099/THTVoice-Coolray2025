package com.voicegeely.app.voice

/**
 * Owner report 12/10/2026: "mở youtube rồi tạm dừng nhạc" opened a YouTube *search*. The driver
 * paused after "rồi", the recogniser ended the utterance there and "mở youtube rồi" became a
 * search for "rồi". Two fixes:
 * - while listening, a sentence that ends in a joining word ("rồi", "và", "xong", "sau đó"…)
 *   gets [GRACE_MS] more to continue instead of ending at the first pause;
 * - a joining word still dangling at the end is dropped before parsing.
 */
object UtteranceContinuation {
    const val GRACE_MS = 2_500L

    private val DANGLING = Regex(
        "(?:\\s+|^)(?:rồi thì|rồi|dồi|rùi|xong rồi|xong|sau đó|và|với lại|đồng thời|tiện thể|kèm theo)[\\s.,!?…]*$",
        RegexOption.IGNORE_CASE
    )

    /**
     * True when the sentence ends in a joining word that is not part of a complete command
     * ("mấy giờ rồi", "xe chạy bao nhiêu km rồi" are complete; "mở youtube rồi" is only a search
     * for "rồi", so it is not).
     */
    private val ENDS_WITH_ROI = Regex("(?:^|\\s)(?:rồi|dồi|rùi)[\\s.,!?…]*$", RegexOption.IGNORE_CASE)

    fun endsWithConnector(text: String, parse: (String) -> ParsedVoiceCommand? = VoiceParseChain::parse): Boolean {
        val t = text.trim()
        if (t.isEmpty() || !DANGLING.containsMatchIn(t) || stripRaw(t) == t) return false
        // "và / xong / sau đó…" at the end never finish a command. "rồi" can ("mấy giờ rồi").
        if (!ENDS_WITH_ROI.containsMatchIn(t)) return true
        val whole = parse(t)?.command
        return whole == null || isFreeText(whole)
    }

    /** "mở youtube rồi" → "mở youtube"; unchanged when nothing dangles or the whole is a command. */
    fun stripDanglingConnector(text: String, parse: (String) -> ParsedVoiceCommand? = VoiceParseChain::parse): String =
        if (endsWithConnector(text, parse)) stripRaw(text.trim()) else text

    private fun isFreeText(c: VoiceCommand) =
        c is VoiceCommand.SearchMedia || c is VoiceCommand.Navigate || c is VoiceCommand.OpenInstalledApp

    private fun stripRaw(text: String): String {
        var t = text.trim()
        while (true) {
            val m = DANGLING.find(t) ?: return t
            val rest = t.substring(0, m.range.first).trim()
            if (rest.isEmpty()) return t
            t = rest
        }
    }

    /**
     * Listening-loop decision at an ASR endpoint. [graceText]/[graceStartMs] remember the partial
     * that ended with a joining word. Returns true when listening should stop.
     */
    fun stopAtEndpoint(
        partial: String, graceText: String?, graceStartMs: Long, nowMs: Long,
        parse: (String) -> ParsedVoiceCommand? = VoiceParseChain::parse
    ): Boolean {
        if (!endsWithConnector(partial, parse)) return true
        if (graceText != partial) return false            // new words arrived, or grace not started
        return nowMs - graceStartMs >= GRACE_MS
    }
}
