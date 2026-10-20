package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStatusQueryExecutorTest {
    @Test
    fun speedUsesKmhValueDirectly() = runBlocking {
        val fake = FakeVehicle(
            floatValues = mutableMapOf(Pair(CoolrayProperties.VEHICLE_SPEED, 0) to 36f)
        )
        val result = VehicleStatusQueryExecutor(fake)
            .execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.SPEED))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals("Tốc độ hiện tại là 36 ki lô mét một giờ", (result as CommandExecutionResult.Confirmed).actualValue)
    }

    @Test
    fun gearUsesStandardAndroidVehicleGearValues() = runBlocking {
        val fake = FakeVehicle(
            intValues = mutableMapOf(Pair(CoolrayProperties.GEAR, 0) to 8)
        )
        val result = VehicleStatusQueryExecutor(fake)
            .execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.GEAR))
        assertEquals("Xe đang ở số D", (result as CommandExecutionResult.Confirmed).actualValue)
    }

    @Test
    fun climateTemperatureAndFanSpeedUseCoolrayAreas() = runBlocking {
        val fake = FakeVehicle(
            intValues = mutableMapOf(Pair(CoolrayProperties.HVAC_FAN_SPEED, CoolrayProperties.HVAC_FAN_AREA) to 3),
            floatValues = mutableMapOf(Pair(CoolrayProperties.HVAC_TEMP_SET, CoolrayProperties.HVAC_TEMP_AREA) to 22f)
        )
        val temp = VehicleStatusQueryExecutor(fake).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CLIMATE_TEMPERATURE))
        assertEquals("Nhiệt độ điều hòa hiện tại là 22 độ", (temp as CommandExecutionResult.Confirmed).actualValue)
        val fan = VehicleStatusQueryExecutor(fake).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FAN_SPEED))
        assertEquals("Quạt đang ở mức 3", (fan as CommandExecutionResult.Confirmed).actualValue)
        assertEquals(0, fake.writeCount)
    }

    @Test
    fun queriesWithoutCoolrayMappingAreRejectedWithoutReadOrWrite() = runBlocking {
        val fake = FakeVehicle()
        for (query in VehicleStatusQuery.values().filter { it !in CoolrayCapabilities.COOLRAY_STATUS_QUERIES }) {
            val result = VehicleStatusQueryExecutor(fake).execute(VoiceCommand.QueryVehicleStatus(query))
            assertTrue("$query", result is CommandExecutionResult.Rejected)
        }
        assertEquals(0, fake.writeCount)
    }

    @Test
    fun fuelOdometerAndOutsideTemperatureUseStandardProperties() = runBlocking {
        val fake = FakeVehicle(
            floatValues = mutableMapOf(
                Pair(CoolrayProperties.FUEL_LEVEL, 0) to 30000f,
                Pair(CoolrayProperties.FUEL_CAPACITY, 0) to 50000f,
                Pair(CoolrayProperties.ODOMETER, 0) to 12345.6f,
                Pair(CoolrayProperties.OUTSIDE_TEMPERATURE, 0) to 31.4f
            )
        )
        fun say(q: VehicleStatusQuery) = runBlocking {
            (VehicleStatusQueryExecutor(fake).execute(VoiceCommand.QueryVehicleStatus(q)) as CommandExecutionResult.Confirmed).actualValue
        }
        assertEquals("Bình xăng còn 60 phần trăm", say(VehicleStatusQuery.FUEL_LEVEL))
        assertEquals("Tổng quãng đường xe đã đi là 12346 ki lô mét", say(VehicleStatusQuery.ODOMETER))
        assertEquals("Nhiệt độ ngoài trời là 31 độ", say(VehicleStatusQuery.OUTSIDE_TEMPERATURE))
        assertEquals(0, fake.writeCount)
    }

    @Test
    fun fuelLowFlagIsSpokenAndMissingCapacityIsNotGuessed() = runBlocking {
        val low = FakeVehicle(
            floatValues = mutableMapOf(Pair(CoolrayProperties.FUEL_LEVEL, 0) to 4000f, Pair(CoolrayProperties.FUEL_CAPACITY, 0) to 50000f),
            boolValues = mutableMapOf(Pair(CoolrayProperties.FUEL_LEVEL_LOW, 0) to true)
        )
        val r = VehicleStatusQueryExecutor(low).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FUEL_LEVEL))
        assertEquals("Bình xăng còn 8 phần trăm, xăng sắp hết", (r as CommandExecutionResult.Confirmed).actualValue)
        val noCapacity = FakeVehicle(floatValues = mutableMapOf(Pair(CoolrayProperties.FUEL_LEVEL, 0) to 4000f))
        assertTrue(
            VehicleStatusQueryExecutor(noCapacity).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FUEL_LEVEL))
                is CommandExecutionResult.Rejected
        )
    }

    @Test
    fun unknownGearIsRejectedInsteadOfGuessed() = runBlocking {
        val fake = FakeVehicle(
            intValues = mutableMapOf(Pair(CoolrayProperties.GEAR, 0) to 999)
        )
        val result = VehicleStatusQueryExecutor(fake)
            .execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.GEAR))
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test
    fun carSummaryUsesFuelAndAdaptApiWindowPositions() = runBlocking {
        val fake = FakeVehicle(
            floatValues = mutableMapOf(Pair(CoolrayProperties.FUEL_LEVEL, 0) to 30000f, Pair(CoolrayProperties.FUEL_CAPACITY, 0) to 50000f)
        )
        val port = FakeEcarxPort().apply {
            WindowVoiceCommandExecutor.ALL_ZONES.forEach { floats[com.voicegeely.app.car.Coolray2025Functions.BCM_FUNC_WINDOW_CURRENT_POS to it] = 0f }
            floats[com.voicegeely.app.car.Coolray2025Functions.BCM_FUNC_WINDOW_CURRENT_POS to 32] = 50f
        }
        val r = VehicleStatusQueryExecutor(fake, port).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CAR_SUMMARY))
        assertEquals("Xăng còn 60 phần trăm. Đang mở kính trước bên phụ", (r as CommandExecutionResult.Confirmed).actualValue)
        assertEquals(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CAR_SUMMARY), VoiceParseChain.parse("tình trạng xe")?.command)
    }

    private class FakeVehicle(
        val intValues: MutableMap<Pair<Int, Int>, Int> = mutableMapOf(),
        val floatValues: MutableMap<Pair<Int, Int>, Float> = mutableMapOf(),
        val boolValues: MutableMap<Pair<Int, Int>, Boolean> = mutableMapOf()
    ) : VehiclePropertyAccess {
        var writeCount = 0
        override fun getIntProperty(propertyId: Int, areaId: Int): Int = intValues[Pair(propertyId, areaId)] ?: -1
        override fun getBoolProperty(propertyId: Int, areaId: Int): Boolean =
            boolValues[Pair(propertyId, areaId)] ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? =
            boolValues[Pair(propertyId, areaId)]
        override fun getFloatProperty(propertyId: Int, areaId: Int): Float = floatValues[Pair(propertyId, areaId)] ?: -1f
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean { writeCount++; return false }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean { writeCount++; return false }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean { writeCount++; return false }
        override fun disconnect() = Unit
    }
}
