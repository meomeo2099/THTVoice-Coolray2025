package com.voicegeely.app.voice

import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacAirflowAutoCycleCommandTest {
    @Test
    fun referencePhrasesParse() {
        listOf("bật đảo gió tự động", "bật đảo gió", "bật tự động đảo gió", "đảo gió tự động").forEach { text ->
            assertEquals(text, VoiceCommand.HvacAirflowAutoCycle(true), VoiceCommandParser.parse(text)?.command)
        }
        listOf("tắt đảo gió tự động", "tắt đảo gió", "tắt tự động đảo gió").forEach { text ->
            assertEquals(text, VoiceCommand.HvacAirflowAutoCycle(false), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun recirculationAndFanDirectionAreUnaffected() {
        assertEquals(VoiceCommand.Recirculation(true), VoiceCommandParser.parse("lấy gió trong")?.command)
        assertEquals(VoiceCommand.Recirculation(true), VoiceCommandParser.parse("bật tuần hoàn gió")?.command)
        assertEquals(
            VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE)),
            VoiceCommandParser.parse("hướng gió vào mặt")?.command
        )
    }

    @Test
    fun coolrayPlainPhrasesDriveThePeriodicSwing() = runBlocking {
        // Coolray has no car-side cycle: "bật đảo gió" starts the swing, "tắt đảo gió" stops it.
        val car = SwingCar()
        val on = VehicleCommandRouter(car, waitForReadback = {}).execute(VoiceCommand.HvacAirflowAutoCycle(true))
        assertTrue(on is CommandExecutionResult.Confirmed)
        assertEquals(2, car.direction)
        assertTrue(AirflowDirectionCycler.isRunning)
        val off = VehicleCommandRouter(car, waitForReadback = {}).execute(VoiceCommand.HvacAirflowAutoCycle(false))
        assertTrue(off is CommandExecutionResult.Confirmed)
        assertTrue(!AirflowDirectionCycler.isRunning)
        assertTrue(car.vendorWrites.isEmpty())
    }

    private class SwingCar(var direction: Int = 1) : VehiclePropertyAccess {
        val vendorWrites = mutableListOf<Int>()
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) =
            if (propertyId == com.voicegeely.app.car.CoolrayProperties.HVAC_FAN_DIRECTION) direction else -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = getBoolPropertyOrNull(propertyId, areaId) ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? =
            if (propertyId == com.voicegeely.app.car.CoolrayProperties.HVAC_POWER) true else null
        override fun getFloatProperty(propertyId: Int, areaId: Int) = Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean {
            if (propertyId == com.voicegeely.app.car.CoolrayProperties.HVAC_FAN_DIRECTION) direction = value else vendorWrites += propertyId
            return true
        }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean { vendorWrites += propertyId; return true }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = true
        override fun disconnect() = Unit
    }

    private class FakeVehicle(private var state: Boolean?, private val ignoreWrites: Boolean = false) : VehiclePropertyAccess {
        val writes = mutableListOf<Triple<Int, Int, Boolean>>()
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) = -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = getBoolPropertyOrNull(propertyId, areaId) ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? =
            if (propertyId == 557846566 /* EX2 HVAC_AIRFLOW_AUTO_CYCLE */ && areaId == 0 /* EX2 HVAC_AIRFLOW_AUTO_CYCLE_AREA */) state else null
        override fun getFloatProperty(propertyId: Int, areaId: Int) = Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = true
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean {
            writes += Triple(propertyId, areaId, value)
            if (!ignoreWrites && propertyId == 557846566 /* EX2 HVAC_AIRFLOW_AUTO_CYCLE */) state = value
            return true
        }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = true
        override fun disconnect() = Unit
    }
}

class AirflowPeriodicSwingTest {
    private fun cmd(text: String) = VoiceCommandParser.parse(text)?.command

    @org.junit.Test fun periodicPhrases() {
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true, 1), cmd("đảo gió mỗi phút"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true, 1), cmd("bật đảo gió mỗi phút một lần"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true, 2), cmd("đảo gió mỗi hai phút"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true, 5), cmd("đảo gió 5 phút một lần"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true, 0), cmd("bật đảo hướng gió"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true, 0), cmd("đảo gió định kỳ"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(false, 0), cmd("tắt đảo hướng gió"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(false, 0), cmd("dừng đảo gió mỗi phút"))
        // The car's own automatic cycle keeps its plain phrases.
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(true), cmd("bật đảo gió"))
        org.junit.Assert.assertEquals(VoiceCommand.HvacAirflowAutoCycle(false), cmd("tắt đảo gió"))
    }

    @org.junit.Test fun swingAlternatesFaceAndFeet() {
        org.junit.Assert.assertEquals(2, AirflowDirectionCycler.nextDirection(1))
        org.junit.Assert.assertEquals(1, AirflowDirectionCycler.nextDirection(2))
        org.junit.Assert.assertEquals(1, AirflowDirectionCycler.nextDirection(6))
        org.junit.Assert.assertEquals(1, AirflowDirectionCycler.nextDirection(null))
    }

    @org.junit.Test fun manualDirectionStopsTheSwing() {
        val vehicle = SwingFake()
        AirflowDirectionCycler.start(vehicle, 1)
        org.junit.Assert.assertTrue(AirflowDirectionCycler.isRunning)
        kotlinx.coroutines.runBlocking {
            HvacVoiceCommandExecutor(vehicle, waitForReadback = {}).execute(VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE)))
        }
        org.junit.Assert.assertFalse(AirflowDirectionCycler.isRunning)
    }

    @org.junit.Test fun periodicStartRequiresHvacOnAndSwingsNow() {
        val off = SwingFake(power = false)
        val rejected = kotlinx.coroutines.runBlocking {
            HvacVoiceCommandExecutor(off, waitForReadback = {}).execute(VoiceCommand.HvacAirflowAutoCycle(true, 2))
        }
        org.junit.Assert.assertTrue(rejected is CommandExecutionResult.Rejected)
        org.junit.Assert.assertFalse(AirflowDirectionCycler.isRunning)

        val on = SwingFake(power = true, direction = 1)
        val ok = kotlinx.coroutines.runBlocking {
            HvacVoiceCommandExecutor(on, waitForReadback = {}).execute(VoiceCommand.HvacAirflowAutoCycle(true, 2))
        }
        org.junit.Assert.assertTrue(ok is CommandExecutionResult.Confirmed)
        org.junit.Assert.assertEquals(2, on.direction)
        org.junit.Assert.assertEquals(2, AirflowDirectionCycler.intervalMinutes)
        val off2 = kotlinx.coroutines.runBlocking {
            HvacVoiceCommandExecutor(on, waitForReadback = {}).execute(VoiceCommand.HvacAirflowAutoCycle(false, 0))
        }
        org.junit.Assert.assertTrue(off2 is CommandExecutionResult.Confirmed)
        org.junit.Assert.assertFalse(AirflowDirectionCycler.isRunning)
    }

    private class SwingFake(var power: Boolean = true, var direction: Int = 1) : com.voicegeely.app.car.VehiclePropertyAccess {
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) =
            if (propertyId == com.voicegeely.app.car.CoolrayProperties.HVAC_FAN_DIRECTION) direction else -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = getBoolPropertyOrNull(propertyId, areaId) ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? =
            if (propertyId == com.voicegeely.app.car.CoolrayProperties.HVAC_POWER) power else null
        override fun getFloatProperty(propertyId: Int, areaId: Int) = Float.NaN
        override fun getIntRange(propertyId: Int, areaId: Int): com.voicegeely.app.car.VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean {
            if (propertyId == com.voicegeely.app.car.CoolrayProperties.HVAC_FAN_DIRECTION) direction = value
            return true
        }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean) = true
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = true
        override fun disconnect() = Unit
    }
}
