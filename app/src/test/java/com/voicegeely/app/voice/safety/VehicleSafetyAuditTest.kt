package com.voicegeely.app.voice.safety

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleSafetyAuditTest {
    @Test
    fun backgroundCheckEnforcesParkedRuleWithoutFillingProblemLog() {
        val logger = SafetyEventLogger(clockMs = { 2L })
        val gate = ParkedActionGate(VehicleSafetyGate(FakeVehicle(speed = 20f, gear = 8), logger))
        assertEquals(SafetyDecisionType.BLOCK, gate.evaluate(ParkedAction.OTA_INSTALL, record = false).type)
        assertEquals(0, logger.snapshot().size)
        gate.evaluate(ParkedAction.OTA_INSTALL)
        assertEquals(1, logger.snapshot().size)
    }
    @Test
    fun gateAutomaticallyAuditsInitialDecisionAndRecheck() {
        val logger = SafetyEventLogger(clockMs = { 1L })
        val gate = VehicleSafetyGate(FakeVehicle(speed = 35f, gear = 8), logger)
        val command = VoiceCommand.SetDriveMode(DriveMode.SPORT)

        gate.evaluate(command)
        gate.recheckConfirmed(command)

        val events = logger.snapshot()
        assertEquals(2, events.size)
        assertEquals(SafetyDecisionType.REQUIRE_CONFIRMATION, events[0].decision)
        assertEquals("EVALUATE", events[0].phase)
        assertEquals(SafetyDecisionType.ALLOW, events[1].decision)
        assertEquals("RECHECK", events[1].phase)
    }

    @Test
    fun parkedOnlyActionIsAudited() {
        val logger = SafetyEventLogger(clockMs = { 2L })
        val gate = VehicleSafetyGate(FakeVehicle(speed = 20f, gear = 8), logger)

        gate.evaluateParkedOnly("OTA")

        val event = logger.latest()!!
        assertEquals("ACTION:OTA", event.command)
        assertEquals(SafetyRequirement.PARKED_ONLY, event.requirement)
        assertEquals(SafetyDecisionType.BLOCK, event.decision)
    }

    private class FakeVehicle(
        private val speed: Float,
        private val gear: Int
    ) : VehiclePropertyAccess {
        override fun getIntProperty(propertyId: Int, areaId: Int): Int =
            if (propertyId == CoolrayProperties.GEAR) gear else -1
        override fun getBoolProperty(propertyId: Int, areaId: Int): Boolean = false
        override fun getFloatProperty(propertyId: Int, areaId: Int): Float =
            if (propertyId == CoolrayProperties.VEHICLE_SPEED) speed else Float.NaN
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = false
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean) = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = false
        override fun disconnect() = Unit
    }
}
