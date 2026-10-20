package com.voicegeely.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublicTextTest {
    private val banned = Regex("(?i)sherpa|kokoro|zipformer|onnx|65M|groq|espeak|encoder|lexicon|/data/")

    @Test
    fun hidesEngineNamesAndPaths() {
        listOf(
            "Sherpa 65M offline đã sẵn sàng",
            "Không tìm thấy thư mục model Sherpa 65M: /data/user/0/com.tht.voice.coolray2025/files/models/asr",
            "Model Sherpa 65M thiếu encoder/decoder/joiner/tokens.",
            "Kokoro Vietnamese lexicon thiếu: xin",
            "Đang dùng giọng hệ thống (Kokoro: chưa sẵn sàng)",
            "java.lang.IllegalStateException: sherpa-onnx zipformer failed",
            "Cloud + Groq kết nối tốt",
        ).forEach { raw ->
            val out = PublicText.scrub(raw)
            assertFalse("leaked: $out (from $raw)", banned.containsMatchIn(out))
        }
    }

    @Test
    fun keepsPlainVietnameseText() {
        assertEquals("Thử giọng: đọc được", PublicText.scrub("Thử giọng: đọc được"))
        assertEquals("Nhận giọng nói offline sẵn sàng", PublicText.scrub("Nhận giọng nói offline sẵn sàng"))
        assertEquals("", PublicText.scrub(null))
    }

    @Test
    fun readableReplacement() {
        assertEquals("Bộ nhận giọng nói đã sẵn sàng", PublicText.scrub("Sherpa 65M offline đã sẵn sàng"))
        assertEquals("Đang dùng giọng hệ thống (Giọng đọc THT: chưa sẵn sàng)", PublicText.scrub("Đang dùng giọng hệ thống (Kokoro: chưa sẵn sàng)"))
    }
}
