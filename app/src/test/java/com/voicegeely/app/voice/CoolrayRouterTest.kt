package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolrayRouterTest {

    @Test fun ev_and_foreign_vendor_families_are_rejected_without_any_write() = runBlocking {
        val car = FakeCoolray()
        val router = VehicleCommandRouter(car, waitForReadback = {})
        val blocked = listOf(
            VoiceCommand.OpenFrontTrunk,
            VoiceCommand.SetDoorLock(true),
            VoiceCommand.SetHeadlightHeight(2),
            VoiceCommand.SetIntelligentHighBeam(true),
            VoiceCommand.HvacEco(true),
            VoiceCommand.AmbientLightPower(true)
        )
        for (command in blocked) {
            val result = router.execute(command)
            assertTrue("$command must be rejected", result is CommandExecutionResult.Rejected)
        }
        assertTrue(car.intWrites.isEmpty())
        assertTrue(car.boolWrites.isEmpty())
    }

    @Test fun only_reference_status_queries_are_routed() = runBlocking {
        val router = VehicleCommandRouter(FakeCoolray(), waitForReadback = {})
        val allowed = CoolrayCapabilities.COOLRAY_STATUS_QUERIES
        assertEquals(
            setOf(
                VehicleStatusQuery.SPEED, VehicleStatusQuery.GEAR, VehicleStatusQuery.CLIMATE_TEMPERATURE, VehicleStatusQuery.FAN_SPEED,
                VehicleStatusQuery.FUEL_LEVEL, VehicleStatusQuery.ODOMETER, VehicleStatusQuery.OUTSIDE_TEMPERATURE,
                VehicleStatusQuery.BATTERY_VOLTAGE, VehicleStatusQuery.CAR_SUMMARY
            ),
            allowed
        )
        for (query in VehicleStatusQuery.values().filter { it !in allowed }) {
            val result = router.execute(VoiceCommand.QueryVehicleStatus(query))
            assertEquals("$query", "Xe này chưa hỗ trợ đọc thông tin đó.", (result as CommandExecutionResult.Rejected).reason)
        }
    }

    @Test fun unknown_head_unit_blocks_vehicle_writes_but_allows_status_reads() = runBlocking {
        val car = FakeCoolray(speed = 0f, gear = 4)
        val router = VehicleCommandRouter(car, waitForReadback = {}, headUnitSupported = false)
        for (command in listOf(
            VoiceCommand.Defrost(DefrostZone.REAR, true),
            VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN),
            VoiceCommand.SetFanSpeed(3)
        )) {
            val result = router.execute(command)
            assertEquals("$command", VehicleCommandRouter.UNSUPPORTED_HEAD_UNIT, (result as CommandExecutionResult.Rejected).reason)
        }
        assertTrue(car.intWrites.isEmpty())
        assertTrue(car.boolWrites.isEmpty())
        val gear = router.execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.GEAR))
        assertTrue(gear is CommandExecutionResult.Confirmed)
    }

    @Test fun front_trunk_rejection_explains_the_car_has_none() = runBlocking {
        val result = VehicleCommandRouter(FakeCoolray(), waitForReadback = {}).execute(VoiceCommand.OpenFrontTrunk)
        assertEquals("Xe này không có cốp trước.", (result as CommandExecutionResult.Rejected).reason)
    }

    @Test fun tailgate_is_not_routed_on_coolray() = runBlocking {
        // Owner decision 01/10/2026: no voice tailgate on Coolray.
        val car = FakeCoolray(speed = 0f, gear = 4)
        val result = VehicleCommandRouter(car, waitForReadback = {}).execute(VoiceCommand.UnlockRearTrunk)
        assertEquals("Chức năng này chưa hỗ trợ trên xe này.", (result as CommandExecutionResult.Rejected).reason)
        assertTrue(car.intWrites.isEmpty())
    }

    @Test fun body_families_go_through_adaptapi_port() = runBlocking {
        val port = FakeEcarxPort()
        val router = VehicleCommandRouter(FakeCoolray(), waitForReadback = {}, ecarx = port)
        router.execute(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN))
        router.execute(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, setOf(SeatTarget.DRIVER), level = 1))
        router.execute(VoiceCommand.FogLight(true))
        assertEquals(com.voicegeely.app.car.Coolray2025Functions.BCM_FUNC_WINDOW_POS, port.floatWrites.single().first)
        assertEquals(2, port.intWrites.size)
    }

    @Test fun body_families_without_adaptapi_are_rejected() = runBlocking {
        val car = FakeCoolray()
        val result = VehicleCommandRouter(car, waitForReadback = {}).execute(
            VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN)
        )
        assertEquals(EcarxCommandSupport.NO_BODY_API, (result as CommandExecutionResult.Rejected).reason)
        assertTrue(car.intWrites.isEmpty())
    }

    @Test fun coolray2025_hvac_uses_head_unit_areas() {
        // Decimal keys in the physical car's CarPropertyConfig, 2026-11-11.
        assertEquals(117, CoolrayProperties.HVAC_POWER_AREA)
        assertEquals(117, CoolrayProperties.HVAC_AC_AREA)
        assertEquals(117, CoolrayProperties.HVAC_FAN_AREA)
        assertEquals(356517121, CoolrayProperties.HVAC_FAN_DIRECTION)
        assertEquals(117, CoolrayProperties.HVAC_FAN_DIRECTION_AREA)
        assertEquals(7, CoolrayProperties.HVAC_MAX_FAN)
        assertEquals(49, CoolrayProperties.HVAC_TEMP_AREA)
        assertEquals(68, CoolrayProperties.HVAC_TEMP_AREA_PASSENGER)
        assertEquals(320865540, CoolrayProperties.HVAC_DEFROSTER)
        assertEquals(1, CoolrayProperties.HVAC_DEFROST_FRONT_AREA)
        assertEquals(2, CoolrayProperties.HVAC_DEFROST_REAR_AREA)
    }

    private class FakeCoolray(
        private val speed: Float? = 0f,
        private val gear: Int? = 4,
        private val reflectIntWrites: Boolean = true
    ) : VehiclePropertyAccess {
        val ints = mutableMapOf<Pair<Int, Int>, Int>()
        val ranges = mutableMapOf<Pair<Int, Int>, VehicleIntRange>()
        val intWrites = mutableMapOf<Pair<Int, Int>, Int>()
        val boolWrites = mutableMapOf<Pair<Int, Int>, Boolean>()

        override fun getIntProperty(propertyId: Int, areaId: Int): Int {
            if (propertyId == CoolrayProperties.GEAR) return gear ?: -1
            return ints[propertyId to areaId] ?: -1
        }
        override fun getBoolProperty(propertyId: Int, areaId: Int) = false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? = null
        override fun getFloatProperty(propertyId: Int, areaId: Int): Float =
            if (propertyId == CoolrayProperties.VEHICLE_SPEED) speed ?: Float.NaN else Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int) = ranges[propertyId to areaId]
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean {
            intWrites[propertyId to areaId] = value
            if (reflectIntWrites) ints[propertyId to areaId] = value
            return true
        }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean {
            boolWrites[propertyId to areaId] = value
            return true
        }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = true
        override fun disconnect() = Unit
    }
}
