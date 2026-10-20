package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.VerificationSource
import com.voicegeely.app.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyDiagnosticsTest {
    @Test
    fun summarizesBufferedSafetyEvents() {
        var now = 1L
        val logger = SafetyEventLogger(clockMs = { now++ })
        val command = VoiceCommand.SetDriveMode(DriveMode.SPORT)
        logger.record(
            command,
            SafetyDecision(
                type = SafetyDecisionType.REQUIRE_CONFIRMATION,
                requirement = SafetyRequirement.CONFIRM_WHEN_MOVING,
                motion = VehicleMotionState(40f, 8)
            )
        )
        logger.record(
            command,
            SafetyDecision(
                type = SafetyDecisionType.ALLOW,
                requirement = SafetyRequirement.CONFIRM_WHEN_MOVING,
                motion = VehicleMotionState(39f, 8)
            ),
            phase = "RECHECK"
        )
        logger.recordExecution(
            command,
            CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "SPORT")
        )
        logger.recordParkedAction(
            "Cài đặt bản cập nhật",
            SafetyDecision(
                type = SafetyDecisionType.BLOCK,
                requirement = SafetyRequirement.PARKED_ONLY,
                motion = VehicleMotionState(20f, 8),
                reason = "Chỉ cài khi đỗ"
            )
        )

        val snapshot = SafetyDiagnostics.snapshot(limit = 2, logger = logger)
        assertEquals(4, snapshot.totalBuffered)
        assertEquals(1, snapshot.blocked)
        assertEquals(1, snapshot.confirmationsRequired)
        assertEquals(1, snapshot.confirmedExecutions)
        assertEquals(0, snapshot.failedExecutions)
        assertEquals(2, snapshot.lines.size)
        assertTrue(snapshot.lines.last().contains("Cài đặt bản cập nhật"))
    }
}
