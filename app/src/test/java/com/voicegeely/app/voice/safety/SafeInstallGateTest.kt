package com.voicegeely.app.voice.safety

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeInstallGateTest {
    @Test
    fun defersInstallWhileMovingAndDoesNotRunCallback() {
        var installed = false
        val vehicle = MutableVehicle(speed = 35f, gear = 8)
        val gate = SafeInstallGate(ParkedActionGate(VehicleSafetyGate(vehicle)))

        val result = gate.requestInstall(ParkedAction.OTA_INSTALL) { installed = true }

        assertTrue(result is SafeInstallResult.Deferred)
        assertFalse(installed)
        assertEquals(ParkedAction.OTA_INSTALL, gate.pendingAction()?.action)
    }

    @Test
    fun retriesDeferredInstallOnlyAfterFreshParkedCheck() {
        val vehicle = MutableVehicle(speed = 20f, gear = 8)
        val gate = SafeInstallGate(ParkedActionGate(VehicleSafetyGate(vehicle)))
        var installedAction: ParkedAction? = null

        gate.requestInstall(ParkedAction.APK_INSTALL) { installedAction = ParkedAction.APK_INSTALL }
        assertEquals(null, installedAction)

        vehicle.speed = 0.2f
        vehicle.gear = VehicleMotionState.GEAR_PARK
        val retry = gate.retryPending { installedAction = it }

        assertTrue(retry is SafeInstallResult.Installed)
        assertEquals(ParkedAction.APK_INSTALL, installedAction)
        assertEquals(null, gate.pendingAction())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonInstallParkedActions() {
        val vehicle = MutableVehicle(speed = 0f, gear = VehicleMotionState.GEAR_PARK)
        val gate = SafeInstallGate(ParkedActionGate(VehicleSafetyGate(vehicle)))
        gate.requestInstall(ParkedAction.DEV_TOOLS) {}
    }

    private class MutableVehicle(
        var speed: Float,
        var gear: Int
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
