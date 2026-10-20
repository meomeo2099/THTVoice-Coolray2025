package com.voicegeely.app.voice.safety

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkedActionGateTest {
    @Test
    fun allowsOnlyParkedVehicle() {
        val parked = ParkedActionGate(VehicleSafetyGate(FakeVehicle(speed = 0.2f, gear = VehicleMotionState.GEAR_PARK)))
        val moving = ParkedActionGate(VehicleSafetyGate(FakeVehicle(speed = 18f, gear = 8)))

        assertEquals(SafetyDecisionType.ALLOW, parked.evaluate(ParkedAction.OTA_INSTALL).type)
        assertEquals(SafetyDecisionType.BLOCK, moving.evaluate(ParkedAction.OTA_INSTALL).type)
    }

    @Test
    fun runIfParkedNeverExecutesBlockedAction() {
        var executed = false
        val gate = ParkedActionGate(VehicleSafetyGate(FakeVehicle(speed = 5f, gear = 8)))
        val result = gate.runIfParked(
            action = ParkedAction.APK_INSTALL,
            onBlocked = { decision -> "blocked:${decision.type}" },
            onAllowed = {
                executed = true
                "allowed"
            }
        )

        assertFalse(executed)
        assertTrue(result.startsWith("blocked:"))
    }

    @Test
    fun typedActionKeepsStableDisplayNameInAudit() {
        SafetyAuditRegistry.logger.clear()
        val gate = ParkedActionGate(VehicleSafetyGate(FakeVehicle(speed = 15f, gear = 8)))
        gate.evaluate(ParkedAction.DEV_TOOLS)

        assertEquals("ACTION:${ParkedAction.DEV_TOOLS.displayName}", SafetyAuditRegistry.logger.latest()?.command)
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
