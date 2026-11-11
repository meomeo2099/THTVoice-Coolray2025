package com.voicegeely.app.car

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.CoolrayProperties as P
import com.voicegeely.app.voice.FakeEcarxPort
import com.voicegeely.app.voice.HvacVoiceCommandExecutor
import com.voicegeely.app.voice.VoiceCommand
import com.voicegeely.app.voice.CommandExecutionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EcarxHvacPropertyAccessTest {
    @Test fun relativeCommandUsesOemTwentyTwoAndConfirmsTwentyTwoPointFive() = runBlocking {
        val vhal = StaleVhal()
        val port = FakeEcarxPort().apply { floats[F.HVAC_FUNC_TEMP to 1] = 22f }
        val result = HvacVoiceCommandExecutor(EcarxHvacPropertyAccess(vhal, port), waitForReadback = {})
            .execute(VoiceCommand.AdjustTemperature(0.5f))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(Triple(F.HVAC_FUNC_TEMP, 1, 22.5f), port.floatWrites.single())
        assertEquals(0, vhal.writes)
    }
    @Test fun acceptedWriteWithoutOemStateChangeRemainsUnverified() = runBlocking {
        val port = FakeEcarxPort(reflectWrites = false).apply { floats[F.HVAC_FUNC_TEMP to 1] = 22f }
        val result = HvacVoiceCommandExecutor(EcarxHvacPropertyAccess(StaleVhal(), port), waitForReadback = {})
            .execute(VoiceCommand.AdjustTemperature(0.5f))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }
    private class StaleVhal : VehiclePropertyAccess {
        var writes = 0
        override fun getFloatProperty(propertyId: Int, areaId: Int) = 16f
        override fun getIntProperty(propertyId: Int, areaId: Int) = 4
        override fun getIntPropertyOrNull(propertyId: Int, areaId: Int): Int? = null
        override fun getBoolProperty(propertyId: Int, areaId: Int) = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean { writes++; return true }
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean { writes++; return true }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean { writes++; return true }
        override fun disconnect() {}
    }
    @Test fun temperatureReadsOemInsteadOfStaleVhalAndWritesHalfDegreeToCorrectZones() {
        val vhal = StaleVhal()
        val port = FakeEcarxPort().apply { floats[F.HVAC_FUNC_TEMP to 1] = 22f }
        val access = EcarxHvacPropertyAccess(vhal, port)
        assertEquals(22f, access.getFloatProperty(P.HVAC_TEMP_SET, P.HVAC_TEMP_AREA), 0f)
        assertTrue(access.setFloatProperty(P.HVAC_TEMP_SET, P.HVAC_TEMP_AREA, 22.5f))
        assertTrue(access.setFloatProperty(P.HVAC_TEMP_SET, P.HVAC_TEMP_AREA_PASSENGER, 23f))
        assertEquals(listOf(Triple(F.HVAC_FUNC_TEMP, 1, 22.5f), Triple(F.HVAC_FUNC_TEMP, 4, 23f)), port.floatWrites)
        assertEquals(0, vhal.writes)
    }
    @Test fun unreadableOemTemperatureDoesNotExposeStaleVhal() {
        assertTrue(EcarxHvacPropertyAccess(StaleVhal(), FakeEcarxPort())
            .getFloatProperty(P.HVAC_TEMP_SET, P.HVAC_TEMP_AREA).isNaN())
    }
    @Test fun unsupportedTemperatureIsNotWritten() {
        val port = FakeEcarxPort().apply { unsupported += F.HVAC_FUNC_TEMP }
        assertFalse(EcarxHvacPropertyAccess(StaleVhal(), port).setFloatProperty(P.HVAC_TEMP_SET, P.HVAC_TEMP_AREA, 22f))
        assertTrue(port.floatWrites.isEmpty())
    }
    @Test fun fanRecirculationAndUnknownBooleanUseOemEncodings() {
        val port = FakeEcarxPort()
        val access = EcarxHvacPropertyAccess(StaleVhal(), port)
        assertNull(access.getBoolPropertyOrNull(P.HVAC_AC, P.HVAC_AREA))
        assertTrue(access.setIntProperty(P.HVAC_FAN_SPEED, P.HVAC_AREA, 3))
        assertEquals(3, access.getIntPropertyOrNull(P.HVAC_FAN_SPEED, P.HVAC_AREA))
        assertEquals(F.HVAC_FUNC_FAN_SPEED + 3, port.intWrites.last().third)
        assertTrue(access.setBoolProperty(P.HVAC_RECIRC, P.HVAC_AREA, false))
        assertEquals(F.HVAC_FUNC_CIRCULATION + 2, port.intWrites.last().third)
        assertEquals(false, access.getBoolPropertyOrNull(P.HVAC_RECIRC, P.HVAC_AREA))
    }
    @Test fun unrelatedNullableReadPreservesBackendUnknown() {
        assertNull(EcarxHvacPropertyAccess(StaleVhal(), FakeEcarxPort()).getIntPropertyOrNull(P.GEAR, 0))
    }
    @Test fun fanStateUsesFrontRowZoneBeforeGlobalFallback() {
        val port = FakeEcarxPort().apply {
            ints[F.HVAC_FUNC_FAN_SPEED to 0] = F.HVAC_FUNC_FAN_SPEED + 1
            ints[F.HVAC_FUNC_FAN_SPEED to 1] = F.HVAC_FUNC_FAN_SPEED + 4
        }
        assertEquals(4, EcarxHvacPropertyAccess(StaleVhal(), port).getIntPropertyOrNull(P.HVAC_FAN_SPEED, P.HVAC_AREA))
    }
}
