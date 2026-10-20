package com.voicegeely.app.voice

import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacEcoCommandTest {
    @Test
    fun ecoOnPhrasesFromReferenceDataset() {
        listOf(
            "chế độ tiết kiệm điều hòa",
            "điều hòa tiết kiệm",
            "bật điều hòa tiết kiệm",
            "điều hòa eco",
            "bật eco điều hòa",
            "chế độ điều hòa tiết kiệm",
            "bật máy lạnh tiết kiệm",
            "máy lạnh tiết kiệm"
        ).forEach { text ->
            assertEquals(text, VoiceCommand.HvacEco(true), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun ecoOffPhrasesFromReferenceDataset() {
        listOf(
            "tắt chế độ tiết kiệm điều hòa",
            "tắt điều hòa tiết kiệm",
            "tắt điều hòa eco",
            "tắt eco điều hòa",
            "tắt máy lạnh tiết kiệm",
            "tắt chế độ điều hòa tiết kiệm"
        ).forEach { text ->
            assertEquals(text, VoiceCommand.HvacEco(false), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun driveModeEcoAndPlainAcAreNotHvacEco() {
        assertEquals(VoiceCommand.SetDriveMode(DriveMode.ECO), VoiceCommandParser.parse("chế độ lái tiết kiệm")?.command)
        assertEquals(VoiceCommand.SetDriveMode(DriveMode.ECO), VoiceCommandParser.parse("chế độ eco")?.command)
        listOf("bật điều hòa", "tắt điều hòa", "chế độ tiết kiệm").forEach { text ->
            assertTrue(text, VoiceCommandParser.parse(text)?.command !is VoiceCommand.HvacEco)
        }
    }

    @Test
    fun coolrayExecutorRejectsHvacEcoWithoutWrite() = runBlocking {
        // No HVAC Eco mapping on the Coolray path of the reference.
        val fake = FakeVehicle(state = false)
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.HvacEco(true))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(fake.writes.isEmpty())
    }

    @Test
    fun coolrayRouterDoesNotRouteVendorHvacExtras() = runBlocking {
        val fake = FakeVehicle(state = true)
        val result = VehicleCommandRouter(fake, waitForReadback = {}).execute(VoiceCommand.HvacEco(false))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(fake.writes.isEmpty())
    }

    private class FakeVehicle(private var state: Boolean?, private val ignoreWrites: Boolean = false) : VehiclePropertyAccess {
        val writes = mutableListOf<Triple<Int, Int, Boolean>>()
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) = -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = getBoolPropertyOrNull(propertyId, areaId) ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? =
            if (propertyId == 268960000 /* EX2 HVAC_ECO */ && areaId == 117 /* EX2 HVAC_ECO_AREA */) state else null
        override fun getFloatProperty(propertyId: Int, areaId: Int) = Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = true
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean {
            writes += Triple(propertyId, areaId, value)
            if (!ignoreWrites && propertyId == 268960000 /* EX2 HVAC_ECO */) state = value
            return true
        }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = true
        override fun disconnect() = Unit
    }
}
