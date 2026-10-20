package com.voicegeely.app.voice.safety

import java.text.Normalizer
import java.util.Locale

enum class ConfirmationIntent { CONFIRM, CANCEL }

/** Parses only short standalone confirmation/cancel utterances. */
object VoiceConfirmationParser {
    private val confirm = setOf(
        "đồng ý", "xác nhận", "đúng", "được", "ok", "okay", "ừ", "ừ được", "tiếp tục", "có"
    )
    private val cancel = setOf(
        "không", "không đồng ý", "hủy", "huỷ", "thôi", "bỏ", "bỏ đi", "dừng", "không cần"
    )

    fun parse(text: String): ConfirmationIntent? {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale("vi", "VN"))
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when (normalized) {
            in cancel -> ConfirmationIntent.CANCEL
            in confirm -> ConfirmationIntent.CONFIRM
            else -> null
        }
    }
}
