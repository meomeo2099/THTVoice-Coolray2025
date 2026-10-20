package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coolray 2025 steering wheel heat: HVAC_STEERING_WHEEL_HEAT 289408269 area 0, -2..2. */
class SteeringWheelHeatTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test fun steeringWheelPhrases() {
        assertEquals(VoiceCommand.SteeringWheelHeat(true), cmd("bật sưởi vô lăng"))
        assertEquals(VoiceCommand.SteeringWheelHeat(true), cmd("sưởi vô lăng"))
        assertEquals(VoiceCommand.SteeringWheelHeat(true), cmd("vô lăng lạnh quá"))
        assertEquals(VoiceCommand.SteeringWheelHeat(true), cmd("làm ấm tay lái"))
        assertEquals(VoiceCommand.SteeringWheelHeat(false), cmd("tắt sưởi vô lăng"))
        assertNull(SteeringWheelHeatCommandParser.parse("bật sưởi ghế lái"))
        assertTrue(cmd("bật sưởi ghế") is VoiceCommand.SeatClimate)
    }

    @Test fun onWritesTwoOffWritesZeroAndVerifies() = runBlocking {
        val car = IntCar()
        val exec = SteeringWheelHeatVoiceCommandExecutor(car, waitForReadback = {})
        assertTrue(exec.execute(VoiceCommand.SteeringWheelHeat(true)) is CommandExecutionResult.Confirmed)
        assertEquals(2, car.writes.last())
        assertTrue(exec.execute(VoiceCommand.SteeringWheelHeat(false)) is CommandExecutionResult.Confirmed)
        assertEquals(0, car.writes.last())
        assertEquals(289408269, CoolrayProperties.STEERING_WHEEL_HEAT)
    }

    @Test fun refusedWriteIsRejectedAndNoReadBackIsUnverified() = runBlocking {
        val refused = SteeringWheelHeatVoiceCommandExecutor(IntCar(accept = false), waitForReadback = {})
            .execute(VoiceCommand.SteeringWheelHeat(true))
        assertTrue(refused is CommandExecutionResult.Rejected)
        val silent = SteeringWheelHeatVoiceCommandExecutor(IntCar(reflect = false), waitForReadback = {})
            .execute(VoiceCommand.SteeringWheelHeat(true))
        assertTrue(silent is CommandExecutionResult.AcceptedUnverified)
    }

    private class IntCar(private val accept: Boolean = true, private val reflect: Boolean = true) : VehiclePropertyAccess {
        val writes = mutableListOf<Int>()
        private var value: Int? = null
        override val isMock = false
        override fun getIntProperty(propertyId: Int, areaId: Int) = value ?: -1
        override fun getIntPropertyOrNull(propertyId: Int, areaId: Int): Int? = value
        override fun getBoolProperty(propertyId: Int, areaId: Int) = false
        override fun getFloatProperty(propertyId: Int, areaId: Int) = Float.NaN
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean {
            writes += value
            if (accept && reflect) this.value = value
            return accept
        }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean) = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float) = false
        override fun disconnect() = Unit
    }
}
