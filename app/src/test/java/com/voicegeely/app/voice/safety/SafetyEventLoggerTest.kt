package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.VerificationSource
import com.voicegeely.app.voice.VoiceCommand
import com.voicegeely.app.voice.WindowOperation
import com.voicegeely.app.voice.WindowTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SafetyEventLoggerTest {
    @Test
    fun keepsBoundedNewestEvents() {
        var now = 100L
        val logger = SafetyEventLogger(clockMs = { now++ }, capacity = 2)
        val command = VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN, 100)
        val allow = SafetyDecision(
            type = SafetyDecisionType.ALLOW,
            requirement = SafetyRequirement.CONFIRM_WHEN_MOVING,
            motion = VehicleMotionState(0f, VehicleMotionState.GEAR_PARK)
        )

        logger.record(command, allow, result = "one")
        logger.record(command, allow, result = "two")
        logger.record(command, allow, result = "three")

        val events = logger.snapshot()
        assertEquals(2, events.size)
        assertEquals("two", events[0].result)
        assertEquals("three", events[1].result)
        assertNotNull(logger.latest())
    }

    @Test
    fun recordsMotionDecisionAndConfirmation() {
        val logger = SafetyEventLogger(clockMs = { 1234L })
        val command = VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN, 100)
        val decision = SafetyDecision(
            type = SafetyDecisionType.REQUIRE_CONFIRMATION,
            requirement = SafetyRequirement.CONFIRM_WHEN_MOVING,
            motion = VehicleMotionState(42f, 8)
        )

        val event = logger.record(command, decision, commandHash = "abc", confirmation = "PENDING")

        assertEquals(42f, event.speedKph)
        assertEquals(8, event.gear)
        assertEquals("abc", event.commandHash)
        assertEquals("PENDING", event.confirmation)
    }

    @Test
    fun executionResultCarriesForwardLatestMotionSnapshot() {
        val logger = SafetyEventLogger(clockMs = { 5678L })
        val command = VoiceCommand.SetDriveMode(DriveMode.SPORT)
        logger.record(
            command,
            SafetyDecision(
                type = SafetyDecisionType.REQUIRE_CONFIRMATION,
                requirement = SafetyRequirement.CONFIRM_WHEN_MOVING,
                motion = VehicleMotionState(48f, 8)
            )
        )

        logger.recordExecution(
            command,
            CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "SPORT")
        )

        val event = logger.latest()!!
        assertEquals("RESULT", event.phase)
        assertEquals("CONFIRMED", event.result)
        assertEquals(48f, event.speedKph)
        assertEquals(8, event.gear)
        assertEquals(SafetyRequirement.CONFIRM_WHEN_MOVING, event.requirement)
    }
}
