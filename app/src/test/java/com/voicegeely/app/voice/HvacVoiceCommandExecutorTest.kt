package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacVoiceCommandExecutorTest {
    @Test fun confirmsOnlyAfterReadbackMatches() = runBlocking {
        val fake = FakeVehicle(floatReads = mutableListOf(20f, 22f))
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.SetTemperature(22f))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(22f, fake.lastFloatWrite)
    }

    @Test fun acceptedWriteWithoutMatchingReadbackIsNotSuccess() = runBlocking {
        val fake = FakeVehicle(floatReads = MutableList(12) { 20f })
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.SetTemperature(22f))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    @Test fun acceptsConfirmedIhu629gLowEndpointAndRejectsBelowRange() = runBlocking {
        val fake = FakeVehicle(floatReads = mutableListOf(16f))
        val accepted = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.SetTemperature(16f))
        assertTrue(accepted is CommandExecutionResult.Confirmed)
        assertEquals(16f, fake.lastFloatWrite)

        val rejected = HvacVoiceCommandExecutor(FakeVehicle(), waitForReadback = {}).execute(VoiceCommand.SetTemperature(15f))
        assertTrue(rejected is CommandExecutionResult.Rejected)
    }

    @Test fun relativeTemperatureUsesCurrentValue() = runBlocking {
        val fake = FakeVehicle(floatReads = mutableListOf(24f, 22f))
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.AdjustTemperature(-2f))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(22f, fake.lastFloatWrite)
    }

    @Test fun acOnRequiresPowerAndCompressorReadback() = runBlocking {
        val fake = FakeVehicle().apply {
            boolReadSequences[CoolrayProperties.HVAC_POWER] = mutableListOf(false, true)
            boolReadSequences[CoolrayProperties.HVAC_AC] = mutableListOf(false, true)
        }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.AcPower(true))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_POWER])
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_AC])
    }

    @Test fun fanSpeedIsVerifiedAndDoesNotTouchAcPower() = runBlocking {
        val fake = FakeVehicle().apply { intReadSequences[CoolrayProperties.HVAC_FAN_SPEED] = mutableListOf(2, 5) }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.SetFanSpeed(5))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(5, fake.intWrites[CoolrayProperties.HVAC_FAN_SPEED])
        assertTrue(CoolrayProperties.HVAC_POWER !in fake.boolWrites)
        assertTrue(CoolrayProperties.HVAC_AC !in fake.boolWrites)
    }

    @Test fun recirculationDefrostAndDirectionAreVerified() = runBlocking {
        val fake = FakeVehicle().apply {
            // First read = state before the command, then the read-back after the write.
            boolReadSequences[CoolrayProperties.HVAC_DEFROSTER] = mutableListOf(false, true, false, true)
            intReadSequences[CoolrayProperties.HVAC_FAN_DIRECTION] = mutableListOf(1, 6)
        }
        val executor = HvacVoiceCommandExecutor(fake, waitForReadback = {})
        // Coolray 2025 recirculation: HVAC_RECIRC_ON 354419976 area 75.
        assertTrue(executor.execute(VoiceCommand.Recirculation(true)) is CommandExecutionResult.Confirmed)
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_RECIRC])
        // Coolray 2025 windshield / rear window heat: HVAC_DEFROSTER areas 1 / 2, read back on the same property.
        assertTrue(executor.execute(VoiceCommand.Defrost(DefrostZone.FRONT, true)) is CommandExecutionResult.Confirmed)
        assertEquals(true, fake.boolWrites[CoolrayProperties.HVAC_DEFROSTER])
        assertTrue(executor.execute(VoiceCommand.Defrost(DefrostZone.REAR, true)) is CommandExecutionResult.Confirmed)
        assertTrue(executor.execute(VoiceCommand.FanDirection(setOf(FanDirectionTarget.FEET, FanDirectionTarget.WINDSHIELD))) is CommandExecutionResult.Confirmed)
        assertEquals(6, fake.intWrites[CoolrayProperties.HVAC_FAN_DIRECTION])
    }

    @Test fun stateAlreadyAtTargetIsNotWrittenAgain() = runBlocking {
        val fake = FakeVehicle().apply {
            boolReadSequences[CoolrayProperties.HVAC_AC] = mutableListOf(true)
            intReadSequences[CoolrayProperties.HVAC_FAN_DIRECTION] = mutableListOf(6)
        }
        val executor = HvacVoiceCommandExecutor(fake, waitForReadback = {})
        assertTrue(executor.execute(VoiceCommand.AcPower(true, HvacPowerTarget.COMPRESSOR)) is CommandExecutionResult.Confirmed)
        assertTrue(executor.execute(VoiceCommand.FanDirection(setOf(FanDirectionTarget.FEET, FanDirectionTarget.WINDSHIELD))) is CommandExecutionResult.Confirmed)
        assertEquals(null, fake.boolWrites[CoolrayProperties.HVAC_AC])
        assertEquals(null, fake.intWrites[CoolrayProperties.HVAC_FAN_DIRECTION])
    }

    @Test fun fanEightIsClampedToSevenAndZeroTurnsSystemOff() = runBlocking {
        val fake = FakeVehicle().apply { intReadSequences[CoolrayProperties.HVAC_FAN_SPEED] = mutableListOf(2, 7) }
        val result = HvacVoiceCommandExecutor(fake, waitForReadback = {}).execute(VoiceCommand.SetFanSpeed(8))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(7, fake.intWrites[CoolrayProperties.HVAC_FAN_SPEED])

        val off = FakeVehicle().apply { boolReadSequences[CoolrayProperties.HVAC_POWER] = mutableListOf(true, false) }
        assertTrue(HvacVoiceCommandExecutor(off, waitForReadback = {}).execute(VoiceCommand.SetFanSpeed(0)) is CommandExecutionResult.Confirmed)
        assertEquals(false, off.boolWrites[CoolrayProperties.HVAC_POWER])
        assertTrue(CoolrayProperties.HVAC_FAN_SPEED !in off.intWrites)
    }

    @Test fun coolrayAcceptsAllFanDirectionMasks() = runBlocking {
        val fake = FakeVehicle()
        val executor = HvacVoiceCommandExecutor(fake, waitForReadback = {})

        val faceAndWindshield = executor.execute(
            VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE, FanDirectionTarget.WINDSHIELD))
        )
        val allDirections = executor.execute(
            VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE, FanDirectionTarget.FEET, FanDirectionTarget.WINDSHIELD))
        )

        // Coolray reference accepts face+windshield (5) and all directions (7).
        assertTrue(faceAndWindshield !is CommandExecutionResult.Rejected)
        assertTrue(allDirections !is CommandExecutionResult.Rejected)
        assertEquals(7, fake.intWrites[CoolrayProperties.HVAC_FAN_DIRECTION])
    }

    private class FakeVehicle(private val floatReads: MutableList<Float> = mutableListOf()) : VehiclePropertyAccess {
        var lastFloatWrite: Float? = null
        val intWrites = mutableMapOf<Int, Int>()
        val boolWrites = mutableMapOf<Int, Boolean>()
        val intReadSequences = mutableMapOf<Int, MutableList<Int>>()
        val boolReadSequences = mutableMapOf<Int, MutableList<Boolean?>>()
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) = intReadSequences[propertyId]?.removeFirstOrNull() ?: intWrites[propertyId] ?: -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = getBoolPropertyOrNull(propertyId, areaId) ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int) = boolReadSequences[propertyId]?.removeFirstOrNull() ?: boolWrites[propertyId]
        override fun getFloatProperty(propertyId: Int, areaId: Int) = if (floatReads.isNotEmpty()) floatReads.removeAt(0) else lastFloatWrite ?: -1f
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean { intWrites[propertyId] = value; return true }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean { boolWrites[propertyId] = value; return true }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean { lastFloatWrite = value; return true }
        override fun disconnect() = Unit
    }
}
