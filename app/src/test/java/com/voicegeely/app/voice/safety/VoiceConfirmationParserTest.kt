package com.voicegeely.app.voice.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceConfirmationParserTest {
    @Test fun parsesStandaloneConfirmations() {
        assertEquals(ConfirmationIntent.CONFIRM, VoiceConfirmationParser.parse("đồng ý"))
        assertEquals(ConfirmationIntent.CONFIRM, VoiceConfirmationParser.parse("XÁC NHẬN"))
        assertEquals(ConfirmationIntent.CONFIRM, VoiceConfirmationParser.parse("ok"))
    }

    @Test fun parsesStandaloneCancellation() {
        assertEquals(ConfirmationIntent.CANCEL, VoiceConfirmationParser.parse("không"))
        assertEquals(ConfirmationIntent.CANCEL, VoiceConfirmationParser.parse("bỏ đi"))
        assertEquals(ConfirmationIntent.CANCEL, VoiceConfirmationParser.parse("hủy"))
    }

    @Test fun doesNotStealNormalCommands() {
        assertNull(VoiceConfirmationParser.parse("mở kính bên lái"))
        assertNull(VoiceConfirmationParser.parse("chuyển sang sport"))
        assertNull(VoiceConfirmationParser.parse("đặt nhiệt độ 22 độ"))
    }
}
