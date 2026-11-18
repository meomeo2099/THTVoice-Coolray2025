package com.voicegeely.app.kws

/**
 * Sherpa KWS keyword lines ("tok tok tok :boost #threshold @LABEL"). Extra lines are passed per
 * stream (`KeywordSpotter.createStream(keywords)`, lines joined by "/"); the built-in keyword file
 * rides along in every stream, so learned lines only add to it (R03 does the same).
 */
object KeywordLines {
    data class Line(val tokens: List<String>, val boost: Float?, val threshold: Float?, val label: String) {
        fun format(): String = buildString {
            append(tokens.joinToString(" "))
            boost?.let { append(" :").append(fmt(it)) }
            threshold?.let { append(" #").append(fmt(it)) }
            append(" @").append(label)
        }
    }

    private fun fmt(f: Float): String = String.format(java.util.Locale.US, "%.2f", f)

    fun parse(line: String): Line? {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var boost: Float? = null
        var threshold: Float? = null
        var label: String? = null
        val tokens = mutableListOf<String>()
        for (p in parts) when {
            p.startsWith("@") -> label = p.substring(1)
            p.startsWith(":") -> boost = p.substring(1).toFloatOrNull()
            p.startsWith("#") -> threshold = p.substring(1).toFloatOrNull()
            else -> tokens += p
        }
        if (tokens.isEmpty() || label.isNullOrEmpty()) return null
        return Line(tokens, boost, threshold, label)
    }

    fun forProfile(lines: List<Line>, profile: WakePhrase): List<Line> =
        lines.filter { it.label == profile.keywordLabel + "_USER" && it.tokens.size in WakeEnrollment.MIN_TOKENS..WakeEnrollment.MAX_TOKENS }
            .take(WakeEnrollment.MAX_LINES * WakeVoicesLimit)

    private const val WakeVoicesLimit = 4

    fun parseAll(text: String): List<Line> = text.lineSequence().mapNotNull { parse(it) }.toList()

    /** Stream keyword string: lines joined with "/", never containing "/" or newlines themselves. */
    fun streamKeywords(lines: List<Line>): String =
        lines.joinToString("/") { it.format().replace("/", " ").replace('\n', ' ') }

    /**
     * "Cơ hội thứ hai": the same keyword lines, easier to trigger for a few seconds after a near miss
     * (lower threshold, a little more boost). The Vietnamese ASR confirmation still decides.
     */
    fun lenient(lines: List<Line>, threshold: Float, extraBoost: Float, defaultBoost: Float): List<Line> =
        lines.map { it.copy(threshold = minOf(it.threshold ?: threshold, threshold), boost = (it.boost ?: defaultBoost) + extraBoost) }
}
