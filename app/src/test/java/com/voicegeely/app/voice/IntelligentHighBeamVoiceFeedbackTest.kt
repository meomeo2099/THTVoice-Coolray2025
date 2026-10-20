package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentHighBeamVoiceFeedbackTest {
    @Test
    fun acknowledgementMatchesRequestedIntelligentHighBeamState() {
        assertEquals(
            "Đang bật đèn pha thông minh.",
            VoiceResponseFormatter.acknowledgement(VoiceCommand.SetIntelligentHighBeam(true))
        )
        assertEquals(
            "Đang tắt đèn pha thông minh.",
            VoiceResponseFormatter.acknowledgement(VoiceCommand.SetIntelligentHighBeam(false))
        )
    }

    @Test
    fun confirmedIntelligentHighBeamCanClaimSuccessOnlyAfterEchoReadBack() {
        val confirmedOn = CommandExecutionResult.Confirmed(
            VerificationSource.VEHICLE_READ_BACK,
            "bật"
        )
        val confirmedOff = CommandExecutionResult.Confirmed(
            VerificationSource.VEHICLE_READ_BACK,
            "tắt"
        )

        assertEquals(
            "Đã bật đèn pha thông minh.",
            VoiceResponseFormatter.result(VoiceCommand.SetIntelligentHighBeam(true), confirmedOn)
        )
        assertEquals(
            "Đã tắt đèn pha thông minh.",
            VoiceResponseFormatter.result(VoiceCommand.SetIntelligentHighBeam(false), confirmedOff)
        )
    }

    @Test
    fun unverifiedIntelligentHighBeamNeverClaimsCompletionOrOldDisabledState() {
        val command = VoiceCommand.SetIntelligentHighBeam(true)
        val result = CommandExecutionResult.AcceptedUnverified(
            "VHAL đã nhận lệnh bật Intelligent High Beam nhưng chưa đọc được echo độc lập."
        )
        val spoken = VoiceResponseFormatter.result(command, result)

        assertTrue(spoken.contains("Đã gửi lệnh bật đèn pha thông minh"))
        assertTrue(spoken.contains("chưa xác nhận được trạng thái"))
        assertFalse(spoken.contains("chưa được nối vào production"))
        assertFalse(spoken.startsWith("Đã bật đèn pha thông minh."))
    }
}
