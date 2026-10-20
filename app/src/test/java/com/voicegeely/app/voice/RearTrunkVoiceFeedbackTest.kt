package com.voicegeely.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearTrunkVoiceFeedbackTest {
    private val command = VoiceCommand.UnlockRearTrunk

    @Test
    fun acknowledgementDescribesVehicleUnlockForRearTrunkAccess() {
        val text = VoiceResponseFormatter.acknowledgement(command)

        assertTrue(text.contains("mở khóa"))
        assertTrue(text.contains("cốp sau"))
        assertFalse(text.contains("chưa được nối vào production"))
    }

    @Test
    fun confirmedOnlyClaimsVehicleUnlockNeverRearTrunkOpened() {
        val text = VoiceResponseFormatter.result(
            command,
            CommandExecutionResult.Confirmed(
                VerificationSource.VEHICLE_READ_BACK,
                "unlocked"
            )
        )

        assertTrue(text.contains("Đã mở khóa"))
        assertTrue(text.contains("cốp sau"))
        assertFalse(text.contains("Đã mở cốp sau"))
    }

    @Test
    fun acceptedUnverifiedReportsSentCommandWithoutClaimingSuccess() {
        val text = VoiceResponseFormatter.result(
            command,
            CommandExecutionResult.AcceptedUnverified("chưa đọc lại được một số cửa")
        )

        assertTrue(text.contains("Đã gửi lệnh"))
        assertTrue(text.contains("chưa xác nhận"))
        assertFalse(text.contains("Đã mở cốp sau"))
    }
}
