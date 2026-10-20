package com.voicegeely.app.voice.safety

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.VoiceCommand
import com.voicegeely.app.voice.WindowOperation
import com.voicegeely.app.voice.WindowTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSafetyGateTest {
    @Test fun sensitiveCommandIsAllowedWhenParked() {
        val gate = VehicleSafetyGate(FakeVehicle(speed = 0f, gear = 4))
        val decision = gate.evaluate(VoiceCommand.SetDriveMode(DriveMode.SPORT))
        assertEquals(SafetyDecisionType.ALLOW, decision.type)
        assertTrue(decision.motion?.isParked == true)
    }

    @Test fun sensitiveCommandRequiresConfirmationWhenNotParked() {
        val gate = VehicleSafetyGate(FakeVehicle(speed = 48f, gear = 8))
        val decision = gate.evaluate(
            VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN)
        )
        assertEquals(SafetyDecisionType.REQUIRE_CONFIRMATION, decision.type)
    }

    @Test fun sensitiveCommandFailsClosedWhenTelemetryUnavailable() {
        val gate = VehicleSafetyGate(FakeVehicle(speed = Float.NaN, gear = -1))
        val decision = gate.evaluate(VoiceCommand.SetDriveMode(DriveMode.ECO))
        assertEquals(SafetyDecisionType.BLOCK, decision.type)
    }

    @Test fun confirmedCommandRechecksTelemetryButDoesNotAskTwice() {
        val gate = VehicleSafetyGate(FakeVehicle(speed = 30f, gear = 8))
        val decision = gate.recheckConfirmed(VoiceCommand.SetDriveMode(DriveMode.COMFORT))
        assertEquals(SafetyDecisionType.ALLOW, decision.type)
    }

    @Test fun brightnessWriteFailsClosedWithoutParkedTelemetry() {
        val command = VoiceCommand.SetManualDisplayBrightness(50)
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(command))
        assertEquals(SafetyDecisionType.BLOCK, VehicleSafetyGate(null).evaluate(command).type)
        assertEquals(SafetyDecisionType.BLOCK,
            VehicleSafetyGate(FakeVehicle(speed = 10f, gear = 8)).evaluate(command).type)
        assertEquals(SafetyDecisionType.ALLOW,
            VehicleSafetyGate(FakeVehicle(speed = 0f, gear = 4)).evaluate(command).type)
    }

    @Test fun parkedOnlyCannotBeOverriddenWhileMoving() {
        val gate = VehicleSafetyGate(FakeVehicle(speed = 10f, gear = 8))
        val decision = gate.evaluateParkedOnly("Cài đặt bản cập nhật")
        assertEquals(SafetyDecisionType.BLOCK, decision.type)
    }

    private class FakeVehicle(
        private val speed: Float,
        private val gear: Int
    ) : VehiclePropertyAccess {
        override fun getIntProperty(propertyId: Int, areaId: Int): Int =
            if (propertyId == CoolrayProperties.GEAR) gear else -1

        override fun getBoolProperty(propertyId: Int, areaId: Int): Boolean = false

        override fun getFloatProperty(propertyId: Int, areaId: Int): Float =
            if (propertyId == CoolrayProperties.VEHICLE_SPEED) speed else -1f

        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean = false
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean = false
        override fun disconnect() = Unit
    }
}
