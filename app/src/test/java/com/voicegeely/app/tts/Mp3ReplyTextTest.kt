package com.voicegeely.app.tts

import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.VerificationSource
import com.voicegeely.app.voice.VoiceParseChain
import com.voicegeely.app.voice.VoiceResponseFormatter
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
    @Test fun directYoutubeUsesNamedMp3ThroughRuntimeParseChain() {
        val parsed = requireNotNull(VoiceParseChain.parse("mở youtube"))
        val execution = CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE)
        val reply = VoiceResponseFormatter.result(parsed.command, execution)
        assertEquals("Đã mở YouTube.", reply)
        assertEquals(listOf("apps/youtube"), Mp3ReplyText.select(reply, clips))
    }

    @Test fun explicitVideoKeepsMediaReply() {
        val parsed = requireNotNull(VoiceParseChain.parse("mở video trên youtube"))
        val execution = CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE)
        assertEquals("Đã mở video trên YouTube.", VoiceResponseFormatter.result(parsed.command, execution))
    }
}
