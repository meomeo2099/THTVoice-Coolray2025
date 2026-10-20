package com.voicegeely.app.ui

/**
 * Text shown to the user (status line, overlay errors, notification, "Thử giọng") must not name
 * the speech engines/models inside the app. Details stay in logcat; the UI gets product terms.
 */
object PublicText {
    private val rules: List<Pair<Regex, String>> = listOf(
        // Absolute paths (model folders, app data) carry engine names and are noise for drivers.
        Regex("""(/(data|storage|sdcard|mnt)/\S*)""") to "",
        // Owner decision 15/10/2026: no car-maker name on screen.
        Regex("""\bGeely\b""") to "Ge-ly",
        Regex("""(?i)kokoro(\s+vietnamese)?(\s+offline)?""") to "Giọng đọc THT",
        Regex("""(?i)sherpa[\s-]*onnx""") to "Bộ nhận giọng nói",
        Regex("""(?i)sherpa\s+(online|offline)[\s-]*transducer""") to "Bộ nhận giọng nói",
        Regex("""(?i)sherpa\s+kws""") to "Bộ từ đánh thức",
        Regex("""(?i)(model\s+)?sherpa(\s+65M)?(\s+offline)?(\s*-?\s*transducer)?""") to "Bộ nhận giọng nói",
        Regex("""(?i)(model\s+)?\b(65M|30M)\b(\s+offline)?(\s*-?\s*transducer)?""") to "Bộ nhận giọng nói",
        Regex("""(?i)\b(zipformer|onnx(runtime)?|transducer|espeak(-ng)?(-data)?|vits|piper|groq)\b""") to "",
        Regex("""(?i)\b(encoder|decoder|joiner)(/(encoder|decoder|joiner|tokens))*\b""") to "tệp mô hình",
        Regex("""(?i)\b(voices\.bin|tokens(\.txt)?|lexicon(-vi)?(\.txt)?|speaker)\b""") to "dữ liệu giọng",
    )

    fun scrub(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()
        var out: String = text
        for ((re, replacement) in rules) out = re.replace(out, replacement)
        return out
            .replace(Regex("""\(\s*\)"""), "")
            .replace(Regex("""(Bộ nhận giọng nói)(\s+\1)+"""), "$1")
            .replace(Regex("""(Giọng đọc THT)(\s+\1)+"""), "$1")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\s+([.,:;])"""), "$1")
            .replace(Regex(""":\s*$"""), "")
            .trim()
    }
}
