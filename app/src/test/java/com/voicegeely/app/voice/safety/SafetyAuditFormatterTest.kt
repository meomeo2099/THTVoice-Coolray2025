package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyAuditFormatterTest {
    @Test
    fun formatsVehicleSafetyEventForDiagnostics() {
        val event = SafetyEvent(
            timestampMs = 1L,
            command = VoiceCommand.SetDriveMode(DriveMode.SPORT).toString(),
            commandHash = "abc",
            requirement = SafetyRequirement.CONFIRM_WHEN_MOVING,
            decision = SafetyDecisionType.REQUIRE_CONFIRMATION,
            speedKph = 48f,
            gear = 8,
            phase = "EVALUATE",
            confirmation = "PENDING"
        )

        val text = SafetyAuditFormatter.format(event)
        assertTrue(text.contains("speed=48 km/h"))
        assertTrue(text.contains("gear=D"))
        assertTrue(text.contains("confirmation=PENDING"))
    }

    @Test
    fun formatsOnlyRequestedLatestEvents() {
        var now = 0L
        val logger = SafetyEventLogger(clockMs = { ++now })
        repeat(3) { index ->
            logger.recordParkedAction(
                actionName = "action-$index",
                decision = SafetyDecision(
                    type = SafetyDecisionType.BLOCK,
                    requirement = SafetyRequirement.PARKED_ONLY,
                    motion = VehicleMotionState(10f + index, 8),
                    reason = "blocked-$index"
                )
            )
        }

        val lines = SafetyAuditFormatter.formatLatest(logger, limit = 2)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("action-1"))
        assertTrue(lines[1].contains("action-2"))
    }
}
