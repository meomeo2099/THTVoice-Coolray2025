package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.CoolrayProperties as P
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoolrayOemStatusTest {
    private class Vehicle : VehiclePropertyAccess {
        val floats = mutableMapOf<Int, Float>()
        override fun getIntProperty(propertyId: Int, areaId: Int) = -1
        override fun getFloatProperty(propertyId: Int, areaId: Int) = floats[propertyId] ?: Float.NaN
        override fun getBoolProperty(propertyId: Int, areaId: Int) = false
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean = error("read-only query")
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean = error("read-only query")
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean = error("read-only query")
        override fun disconnect() {}
    }
    @Test fun fuelMcuByteIsPercentAndUsesKnownTankCapacityForLitres() = runBlocking {
        val vehicle = Vehicle().apply { floats[P.FUEL_CAPACITY] = 50_000f }
        val port = FakeEcarxPort().apply { byteProps[F.INFO_ID_REMAINING_FUEL] = byteArrayOf(50) }
        val result = VehicleStatusQueryExecutor(vehicle, port).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FUEL_LEVEL)) as CommandExecutionResult.Confirmed
        assertTrue(result.actualValue, result.actualValue.orEmpty().contains("25 lít"))
        assertFalse(result.actualValue.orEmpty().contains("50 lít"))
    }
    @Test fun unknownTankCapacityCanReportMcuPercentWithoutInventingLitres() = runBlocking {
        val port = FakeEcarxPort().apply { byteProps[F.INFO_ID_REMAINING_FUEL] = byteArrayOf(60) }
        val result = VehicleStatusQueryExecutor(Vehicle(), port).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FUEL_LEVEL)) as CommandExecutionResult.Confirmed
        assertTrue(result.actualValue.orEmpty().contains("60 phần trăm"))
        assertFalse(result.actualValue.orEmpty().contains("lít"))
    }
    @Test fun partialWindowReadCannotSayEveryWindowIsClosed() = runBlocking {
        val port = FakeEcarxPort().apply { floats[F.BCM_FUNC_WINDOW_CURRENT_POS to 16] = 0f }
        val result = VehicleStatusQueryExecutor(Vehicle(), port).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CAR_SUMMARY))
        assertFalse((result as? CommandExecutionResult.Confirmed)?.actualValue.orEmpty().contains("Kính đóng hết"))
    }
    @Test fun allFourMcuWindowPositionsCanConfirmClosedSummary() = runBlocking {
        val port = FakeEcarxPort().apply {
            for (prop in listOf(F.INFO_ID_WINDOW_FL, F.INFO_ID_WINDOW_FR, F.INFO_ID_WINDOW_RL, F.INFO_ID_WINDOW_RR)) props[prop] = 0
        }
        val result = VehicleStatusQueryExecutor(Vehicle(), port).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CAR_SUMMARY)) as CommandExecutionResult.Confirmed
        assertTrue(result.actualValue.orEmpty().contains("Kính đóng hết"))
    }
    @Test fun rangeUsesSensorApiAndDoesNotWriteFunctionIds() = runBlocking {
        val port = FakeEcarxPort().apply { sensors[0x00100800] = 321f }
        val result = VehicleStatusQueryExecutor(Vehicle(), port).execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.RANGE)) as CommandExecutionResult.Confirmed
        assertTrue(result.actualValue.orEmpty().contains("321"))
        assertEquals(listOf(0x00100800), port.sensorReads)
        assertTrue(port.intWrites.isEmpty())
        assertTrue(port.floatWrites.isEmpty())
    }
    @Test fun unavailableOdometerCanUseOemSensorButUnknownRangeIsRejected() = runBlocking {
        val port = FakeEcarxPort().apply { sensors[0x00100700] = 12_345f; sensors[0x00100800] = Float.NaN }
        val executor = VehicleStatusQueryExecutor(Vehicle(), port)
        val odo = executor.execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.ODOMETER)) as CommandExecutionResult.Confirmed
        assertTrue(odo.actualValue.orEmpty().contains("12345"))
        assertTrue(executor.execute(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.RANGE)) is CommandExecutionResult.Rejected)
    }
}
