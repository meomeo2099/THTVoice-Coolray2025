package com.voicegeely.app.update

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import com.voicegeely.app.voice.safety.ParkedActionGate
import com.voicegeely.app.voice.safety.VehicleMotionState
import com.voicegeely.app.voice.safety.VehicleSafetyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaParkedPolicyTest {
    private fun policy(speed: Float?, gear: Int?) =
        OtaParkedPolicy(ParkedActionGate(VehicleSafetyGate(FakeVehicle(speed, gear))))

    @Test
    fun parkedAndStillAllowsOta() {
        val p = policy(0f, VehicleMotionState.GEAR_PARK)
        assertNull(p.blockedResult())
        assertTrue(p.mayContinueDownload())
    }

    @Test
    fun movingVehicleBlocksCheckDownloadAndInstall() {
        val p = policy(30f, CoolrayProperties.GEAR_D)
        val blocked = p.blockedResult()
        assertEquals(AppUpdateFlowStatus.WAITING_FOR_PARK, blocked?.status)
        assertTrue(blocked!!.detail.startsWith(OtaParkedPolicy.BLOCKED_MESSAGE))
        assertFalse(p.mayContinueDownload())
    }

    @Test
    fun rollingInParkOrDriveGearAtStandstillIsBlocked() {
        assertEquals(AppUpdateFlowStatus.WAITING_FOR_PARK, policy(5f, VehicleMotionState.GEAR_PARK).blockedResult()?.status)
        assertEquals(AppUpdateFlowStatus.WAITING_FOR_PARK, policy(0f, CoolrayProperties.GEAR_D).blockedResult()?.status)
    }

    @Test
    fun missingTelemetryFailsClosed() {
        assertEquals(AppUpdateFlowStatus.WAITING_FOR_PARK, policy(null, null).blockedResult()?.status)
        assertFalse(policy(null, VehicleMotionState.GEAR_PARK).mayContinueDownload())
    }

    private class FakeVehicle(private val speed: Float?, private val gear: Int?) : VehiclePropertyAccess {
        override fun getIntProperty(propertyId: Int, areaId: Int): Int =
            if (propertyId == CoolrayProperties.GEAR) gear ?: -1 else -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = false
        override fun getFloatProperty(propertyId: Int, areaId: Int): Float =
            if (propertyId == CoolrayProperties.VEHICLE_SPEED) speed ?: Float.NaN else Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = false
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean) = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = false
        override fun disconnect() = Unit
    }
}
