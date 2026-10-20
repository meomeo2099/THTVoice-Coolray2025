package com.voicegeely.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontTrunkVoiceFeedbackTest {
    @Test
    fun acknowledgementReflectsActiveRuntimeWithoutClaimingCompletion() {
        val spoken = VoiceResponseFormatter.acknowledgement(VoiceCommand.OpenFrontTrunk)

        assertTrue(spoken.contains("Đang gửi lệnh mở cốp trước"))
        assertFalse(spoken.contains("chưa được bật"))
        assertFalse(spoken.startsWith("Đã mở cốp trước"))
    }

    @Test
    fun acceptedUnverifiedNeverClaimsFrontTrunkOpened() {
        val spoken = VoiceResponseFormatter.result(
            VoiceCommand.OpenFrontTrunk,
            CommandExecutionResult.AcceptedUnverified("Chưa có latch read-back độc lập.")
        )

        assertTrue(spoken.contains("Đã gửi lệnh mở cốp trước"))
        assertTrue(spoken.contains("chưa xác nhận được trạng thái"))
        assertFalse(spoken.contains("runtime chưa bật"))
        assertFalse(spoken.startsWith("Đã mở cốp trước"))
    }

    @Test
    fun unexpectedConfirmedStillFailsClosedWithoutOpenedClaim() {
        val spoken = VoiceResponseFormatter.result(
            VoiceCommand.OpenFrontTrunk,
            CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "open")
        )

        assertTrue(spoken.contains("không công bố thành công"))
        assertFalse(spoken.contains("Đã mở cốp trước"))
    }
}
