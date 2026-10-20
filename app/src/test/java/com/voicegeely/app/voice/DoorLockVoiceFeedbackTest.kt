package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorLockVoiceFeedbackTest {
    @Test
    fun acknowledgementMatchesRequestedDoorLockAction() {
        assertEquals(
            "Đang khóa cửa xe.",
            VoiceResponseFormatter.acknowledgement(VoiceCommand.SetDoorLock(locked = true))
        )
        assertEquals(
            "Đang mở khóa cửa xe.",
            VoiceResponseFormatter.acknowledgement(VoiceCommand.SetDoorLock(locked = false))
        )
    }

    @Test
    fun confirmedDoorLockCanClaimSuccessOnlyAfterReadBack() {
        val confirmed = CommandExecutionResult.Confirmed(
            VerificationSource.VEHICLE_READ_BACK,
            "locked"
        )
        assertEquals(
            "Đã khóa cửa xe.",
            VoiceResponseFormatter.result(VoiceCommand.SetDoorLock(locked = true), confirmed)
        )
        assertEquals(
            "Đã mở khóa cửa xe.",
            VoiceResponseFormatter.result(VoiceCommand.SetDoorLock(locked = false), confirmed)
        )
    }

    @Test
    fun unverifiedDoorLockNeverClaimsCompletionOrOldRuntimeDisabledState() {
        val result = CommandExecutionResult.AcceptedUnverified("Chưa đọc lại được area 64.")
        val spoken = VoiceResponseFormatter.result(VoiceCommand.SetDoorLock(locked = true), result)

        assertTrue(spoken.contains("Đã gửi lệnh khóa cửa"))
        assertTrue(spoken.contains("chưa xác nhận được trạng thái"))
        assertFalse(spoken.contains("runtime chưa bật"))
        assertFalse(spoken.startsWith("Đã khóa cửa xe"))
    }
}
