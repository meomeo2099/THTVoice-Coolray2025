package com.voicegeely.app.voice

/**
 * How technical / English words are *spoken* (screen text stays unchanged). Found by reading all
 * 519 replies with the real voice (tools/tts-probe audit, 12/10/2026): 98 replies lost words such
 * as "VHAL", "HVAC", "Android", "THT" because the Vietnamese voice cannot say them.
 * The spoken forms are plain Vietnamese words, so they are in the voice lexicon (the packer reads
 * this file). Longest phrases first; word-bounded, case-insensitive.
 */
object SpokenTerms {
    private val TERMS: List<Pair<String, String>> = listOf(
        "Vietmap Live" to "việt mép lai",
        "Vietmap" to "việt mép",
        "THT Voice" to "tê hát tê voi",
        "THT Split" to "tê hát tê chia màn hình",
        "THT" to "tê hát tê",
        "Android context" to "hệ thống",
        "Android Auto" to "an đờ roi ô tô",
        "Android" to "hệ thống",
        "Vehicle backend" to "kết nối xe",
        "VHAL" to "hệ thống xe",
        "HVAC" to "điều hòa",
        "A/C" to "máy nén lạnh",
        "ADAS" to "hỗ trợ lái",
        "AVAS manager" to "âm thanh cảnh báo",
        "AVAS mode" to "chế độ âm thanh cảnh báo",
        "AVAS" to "âm thanh cảnh báo",
        "INTELLIGENT_HIGH_BEAM_ECHO" to "đèn pha thông minh",
        "INTELLIGENT_HIGH_BEAM" to "đèn pha thông minh",
        "Intelligent High Beam" to "đèn pha thông minh",
        "HEADLIGHT_HEIGHT" to "độ cao đèn",
        "Headlight Height" to "độ cao đèn",
        "Front trunk" to "cốp trước",
        "Rear trunk" to "cốp sau",
        "Door lock" to "khóa cửa",
        "MediaSession metadata" to "trình phát nhạc",
        "MediaSession" to "trình phát nhạc",
        "Media key" to "phím nhạc",
        "Scene provider" to "màn hình chế độ",
        "Settings" to "cài đặt",
        "IHU629G" to "màn hình xe",
        "IPv4" to "địa chỉ",
        "km/h" to "ki lô mét trên giờ",
        "km" to "ki lô mét",
        "số P" to "số pê",
        "executor" to "",
        "mapping" to "giá trị",
        "cursor null" to "dữ liệu trống",
        "scene_mode" to "chế độ",
        "Control/echo" to "lệnh và phản hồi",
        "echo" to "phản hồi",
        "control" to "lệnh",
        "raw" to "giá trị",
        "API" to "giao tiếp",
        "URI" to "đường dẫn",
        "navigation" to "dẫn đường",
        "process" to "tiến trình",
        "area" to "vùng",
        "latch" to "chốt"
    )

    private val PATTERNS: List<Pair<Regex, String>> = TERMS.map { (term, spoken) ->
        Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(term) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE) to spoken
    }

    fun forSpeech(text: String): String {
        var out = text
        PATTERNS.forEach { (r, spoken) -> out = r.replace(out, spoken) }
        return out.replace(Regex("[ \\t]+"), " ").replace(Regex(" ([,.!?;:])"), "$1").trim()
    }
}
