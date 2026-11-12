package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleIntRange
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coolray 2025 setpoint range 16..32 °C (HVAC_TEMPERATURE_SET config, areas 0x31 (49) / 0x44 (68)), 0.5 steps. */
class CoolrayTemperatureRangeTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test fun referenceRange() {
        assertEquals(16f, CoolrayProperties.HVAC_MIN_TEMP)
        assertEquals(32f, CoolrayProperties.HVAC_MAX_TEMP)
    }

    @Test fun halfDegreePhrases() {
        listOf("hai mươi hai rưỡi", "22 rưỡi", "nhiệt độ hai mươi hai rưỡi", "nhiệt độ 22,5", "nhiệt độ 22.5 độ", "nhiệt độ hai hai phẩy năm")
            .forEach { assertEquals(it, VoiceCommand.SetTemperature(22.5f), cmd(it)) }
        assertEquals(VoiceCommand.SetTemperature(15.5f), cmd("mười lăm rưỡi"))
        assertEquals(VoiceCommand.AdjustTemperature(0.5f), cmd("tăng nhiệt độ nửa độ"))
        assertEquals(VoiceCommand.AdjustTemperature(-0.5f), cmd("giảm nhiệt độ nửa độ"))
        assertEquals(VoiceCommand.AdjustTemperature(1.5f), cmd("tăng nhiệt độ 1 độ rưỡi"))
    }

    @Test fun defaultAdjustmentUsesOneOemHalfDegreeStep() {
        listOf("tăng nhiệt độ", "ấm lên", "chỉnh nhiệt độ cao hơn", "tăng nhiệt")
            .forEach { assertEquals(it, VoiceCommand.AdjustTemperature(0.5f), cmd(it)) }
        listOf("giảm nhiệt độ", "mát xuống", "hạ giúp tôi nhiệt độ", "giảm nhiệt")
            .forEach { assertEquals(it, VoiceCommand.AdjustTemperature(-0.5f), cmd(it)) }
        assertEquals(VoiceCommand.AdjustTemperature(1f), cmd("tăng nhiệt độ một độ"))
        assertEquals(VoiceCommand.AdjustTemperature(-2f), cmd("giảm nhiệt độ hai độ"))
    }

    @Test fun defaultUpAndDownWriteHalfDegreeOnCar() = runBlocking {
        val car = Fake(24f)
        val executor = HvacVoiceCommandExecutor(car, waitForReadback = {})
        executor.execute(requireNotNull(cmd("tăng nhiệt độ")))
        executor.execute(requireNotNull(cmd("giảm nhiệt độ")))
        assertEquals(listOf(24.5f, 24f), car.writes)
    }

    @Test fun executorWritesReferenceEndpointsAndRejectsOutOfRange() = runBlocking {
        val high = Fake(20f)
        assertTrue(HvacVoiceCommandExecutor(high, waitForReadback = {}).execute(VoiceCommand.TemperatureHigh) is CommandExecutionResult.Confirmed)
        assertEquals(listOf(32f), high.writes)
        val low = Fake(20f)
        HvacVoiceCommandExecutor(low, waitForReadback = {}).execute(VoiceCommand.TemperatureLow)
        assertEquals(listOf(16f), low.writes)
        val tooHot = Fake(20f)
        val rejected = HvacVoiceCommandExecutor(tooHot, waitForReadback = {}).execute(VoiceCommand.SetTemperature(33f))
        assertTrue(rejected is CommandExecutionResult.Rejected)
        assertTrue(tooHot.writes.isEmpty())
    }

    @Test fun halfDegreeIsWrittenAndSpokenAsRuoi() = runBlocking {
        val car = Fake(20f)
        val result = HvacVoiceCommandExecutor(car, waitForReadback = {}).execute(VoiceCommand.SetTemperature(22.5f))
        assertEquals(listOf(22.5f), car.writes)
        assertEquals("Đã chỉnh nhiệt độ về 22 rưỡi độ.", VoiceResponseFormatter.result(VoiceCommand.SetTemperature(22.5f), result))
        val step = Fake(31.5f)
        HvacVoiceCommandExecutor(step, waitForReadback = {}).execute(VoiceCommand.AdjustTemperature(2f))
        assertEquals(listOf(32f), step.writes)
    }

    private class Fake(private var temp: Float) : VehiclePropertyAccess {
        val writes = mutableListOf<Float>()
        private val bools = mutableMapOf<Pair<Int, Int>, Boolean>()
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) = -1
        override fun getBoolProperty(propertyId: Int, areaId: Int) = bools[propertyId to areaId] ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? = bools[propertyId to areaId]
        override fun getFloatProperty(propertyId: Int, areaId: Int) = temp
        override fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = true
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean { bools[propertyId to areaId] = value; return true }
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean { writes += value; temp = value; return true }
        override fun disconnect() = Unit
    }
}
