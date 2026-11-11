package com.voicegeely.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Mp3ReplyTextTest {
    private val clips = mapOf("đã chỉnh nhiệt độ về" to "hvac/set_temp", "độ" to "unit/degree",
        "hai mươi hai" to "number/22", "đã mở youtube" to "apps/youtube", "phẩy" to "number/decimal",
        "năm" to "number/5")

    @Test fun composesTemperatureThenApp() {
        assertEquals(listOf("hvac/set_temp", "number/22", "unit/degree", "apps/youtube"),
            Mp3ReplyText.select("Đã chỉnh nhiệt độ về 22 độ. Đã mở YouTube.", clips))
    }

    @Test fun doesNotMisreadDecimalOrUnknownSafetyReason() {
        assertEquals(listOf("hvac/set_temp", "number/22", "number/decimal", "number/5", "unit/degree"),
            Mp3ReplyText.select("Đã chỉnh nhiệt độ về 22.5 độ.", clips))
        assertNull(Mp3ReplyText.select("Đã mở YouTube. Lệnh tiếp theo bị chặn.", clips))
    }
    @Test fun existingCoolrayVideoReplyCanFallBackToTts() {
        assertNull(Mp3ReplyText.select("Đã mở video trên YouTube.", clips))
    }
}
