package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacPowerSeparationTest {
    @Test fun parserSeparatesExplicitCompressorSystemAndGenericCommands() {
        assertEquals(
            VoiceCommand.AcPower(true, HvacPowerTarget.COMPRESSOR),
            VoiceCommandParser.parse("bật ac")?.command
        )
        assertEquals(
            VoiceCommand.AcPower(false, HvacPowerTarget.COMPRESSOR),
            VoiceCommandParser.parse("tắt A/C")?.command
        )
        assertEquals(
            VoiceCommand.AcPower(true, HvacPowerTarget.SYSTEM),
            VoiceCommandParser.parse("bật nguồn điều hòa")?.command
        )
        assertEquals(
            VoiceCommand.AcPower(false, HvacPowerTarget.SYSTEM),
            VoiceCommandParser.parse("tắt hệ thống điều hòa")?.command
        )
        assertEquals(
            VoiceCommand.AcPower(true, HvacPowerTarget.COMBINED),
            VoiceCommandParser.parse("bật điều hòa")?.command
        )
        assertEquals(
            VoiceCommand.AcPower(false, HvacPowerTarget.COMBINED),
            VoiceCommandParser.parse("tắt máy lạnh")?.command
        )
    }

    @Test fun compressorTargetWritesAndVerifiesOnlyAcProperty() = runBlocking {
        val fake = FakeVehicle().apply {
            boolReadSequences[CoolrayProperties.HVAC_AC] = mutableListOf(false, true)
        }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(
            VoiceCommand.AcPower(true, HvacPowerTarget.COMPRESSOR)
        )

        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_AC])
        assertFalse(CoolrayProperties.HVAC_POWER in fake.boolWrites)
        assertFalse(CoolrayProperties.HVAC_POWER in fake.boolReads)
    }

    @Test fun systemTargetWritesAndVerifiesOnlyHvacPowerProperty() = runBlocking {
        val fake = FakeVehicle().apply {
            boolReadSequences[CoolrayProperties.HVAC_POWER] = mutableListOf(false, true)
        }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(
            VoiceCommand.AcPower(true, HvacPowerTarget.SYSTEM)
        )

        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_POWER])
        assertFalse(CoolrayProperties.HVAC_AC in fake.boolWrites)
        assertFalse(CoolrayProperties.HVAC_AC in fake.boolReads)
    }

    @Test fun genericCombinedTargetKeepsBackwardCompatibleDualWriteAndReadback() = runBlocking {
        val fake = FakeVehicle().apply {
            boolReadSequences[CoolrayProperties.HVAC_POWER] = mutableListOf(true)
            boolReadSequences[CoolrayProperties.HVAC_AC] = mutableListOf(true)
        }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(
            VoiceCommand.AcPower(true)
        )

        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_POWER])
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_AC])
        assertTrue(CoolrayProperties.HVAC_POWER in fake.boolReads)
        assertTrue(CoolrayProperties.HVAC_AC in fake.boolReads)
    }

    @Test fun targetedAcceptedWriteWithoutMatchingReadbackIsNotReportedConfirmed() = runBlocking {
        val fake = FakeVehicle().apply {
            boolReadSequences[CoolrayProperties.HVAC_AC] = MutableList(12) { false }
        }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(
            VoiceCommand.AcPower(true, HvacPowerTarget.COMPRESSOR)
        )
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    private class FakeVehicle : VehiclePropertyAccess {
        val boolWrites = mutableMapOf<Int, Boolean>()
        val boolReads = mutableSetOf<Int>()
        val boolReadSequences = mutableMapOf<Int, MutableList<Boolean?>>()
        override val isMock = false

        override fun getIntProperty(propertyId: Int, areaId: Int) = -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = getBoolPropertyOrNull(propertyId, areaId) ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? {
            boolReads += propertyId
            return boolReadSequences[propertyId]?.removeFirstOrNull() ?: boolWrites[propertyId]
        }
        override fun getFloatProperty(propertyId: Int, areaId: Int) = Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = false
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean {
            boolWrites[propertyId] = value
            return true
        }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = false
        override fun disconnect() = Unit
    }
}
