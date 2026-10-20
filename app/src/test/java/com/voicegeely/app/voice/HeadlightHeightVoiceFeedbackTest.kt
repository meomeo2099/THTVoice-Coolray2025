package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlightHeightVoiceFeedbackTest {
    @Test
    fun acknowledgementNamesRequestedHeadlightLevel() {
        assertEquals(
            "Đang chỉnh độ cao đèn trước về mức 4.",
            VoiceResponseFormatter.acknowledgement(VoiceCommand.SetHeadlightHeight(level = 4))
        )
    }

    @Test
    fun confirmedHeadlightHeightUsesVerifiedReadBackValue() {
        val command = VoiceCommand.SetHeadlightHeight(level = 3)
        val confirmed = CommandExecutionResult.Confirmed(
            VerificationSource.VEHICLE_READ_BACK,
            "mức 3"
        )

        assertEquals(
            "Đã chỉnh độ cao đèn trước về mức 3.",
            VoiceResponseFormatter.result(command, confirmed)
        )
    }

    @Test
    fun unverifiedHeadlightHeightNeverClaimsCompletionOrOldDisabledState() {
        val command = VoiceCommand.SetHeadlightHeight(level = 2)
        val result = CommandExecutionResult.AcceptedUnverified(
            "VHAL đã nhận mức 2 nhưng chưa đọc lại được HEADLIGHT_HEIGHT."
        )
        val spoken = VoiceResponseFormatter.result(command, result)

        assertTrue(spoken.contains("Đã gửi lệnh điều chỉnh độ cao đèn trước"))
        assertTrue(spoken.contains("chưa xác nhận được trạng thái"))
        assertFalse(spoken.contains("chưa được nối vào production"))
        assertFalse(spoken.startsWith("Đã chỉnh độ cao đèn trước"))
    }
}
