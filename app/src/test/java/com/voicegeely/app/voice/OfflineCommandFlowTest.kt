package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the production parser, vehicle router and response coordinator together. */
class OfflineCommandFlowTest {
    @Test fun spokenTwentyTwoIsOnlyConfirmedAfterReadBack() = runBlocking {
        val vehicle = TemperatureVehicle(reflectWrite = true)
        val spoken = mutableListOf<String>()
        val parsed = requireNotNull(VoiceCommandParser.parse("đặt nhiệt độ hai hai độ"))
        val result = VoiceCommandCoordinator(
            VehicleCommandRouter(vehicle, waitForReadback = {}),
            VoiceSpeechSink { spoken += it }
        ).handle(parsed, VoiceResponseMode.SHORT)

        assertEquals(VoiceCommand.SetTemperature(22f), parsed.command)
        assertEquals(22f, vehicle.lastWrite)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(VerificationSource.VEHICLE_READ_BACK, (result as CommandExecutionResult.Confirmed).verificationSource)
        assertTrue(spoken.single().startsWith("Đã "))
    }

    @Test fun acceptedWriteWithoutMatchingReadBackNeverSpeaksSuccess() = runBlocking {
        val vehicle = TemperatureVehicle(reflectWrite = false)
        val spoken = mutableListOf<String>()
        val parsed = requireNotNull(VoiceCommandParser.parse("đặt nhiệt độ 22 độ"))
        val result = VoiceCommandCoordinator(
            VehicleCommandRouter(vehicle, waitForReadback = {}),
            VoiceSpeechSink { spoken += it }
        ).handle(parsed, VoiceResponseMode.SHORT)

        assertEquals(22f, vehicle.lastWrite)
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
        assertTrue(spoken.single().startsWith("Đã gửi lệnh"))
        assertTrue(spoken.single().contains("chưa xác nhận được trạng thái"))
        assertFalse(spoken.single().startsWith("Đã chỉnh nhiệt độ"))
    }

    @Test fun rejectedWriteAndInvalidWindowValueCannotClaimSuccess() = runBlocking {
        val vehicle = TemperatureVehicle(acceptWrite = false)
        val spoken = mutableListOf<String>()
        val parsed = requireNotNull(VoiceCommandParser.parse("đặt nhiệt độ 20 độ"))
        val result = VoiceCommandCoordinator(
            VehicleCommandRouter(vehicle, waitForReadback = {}),
            VoiceSpeechSink { spoken += it }
        ).handle(parsed, VoiceResponseMode.SHORT)

        assertTrue(result is CommandExecutionResult.Rejected)
        assertFalse(spoken.single().startsWith("Đã "))
        assertNull(VoiceCommandParser.parse("mở kính bên lái 150%"))
        assertEquals(1, vehicle.writeCount)
    }

    private class TemperatureVehicle(
        private val acceptWrite: Boolean = true,
        private val reflectWrite: Boolean = true
    ) : VehiclePropertyAccess {
        var temperature = 20f
        var lastWrite: Float? = null
        var writeCount = 0

        override fun getIntProperty(propertyId: Int, areaId: Int): Int = -1
        // These tests isolate setpoint acceptance/read-back with cooling already running.
        // Prerequisite writes and unknown A/C are covered by BinhDinhHvacTest.
        override fun getBoolProperty(propertyId: Int, areaId: Int): Boolean =
            propertyId == CoolrayProperties.HVAC_POWER || propertyId == CoolrayProperties.HVAC_AC
        override fun getFloatProperty(propertyId: Int, areaId: Int): Float =
            if (propertyId == CoolrayProperties.HVAC_TEMP_SET) temperature else Float.NaN

        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean = false
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean {
            writeCount++
            lastWrite = value
            if (acceptWrite && reflectWrite) temperature = value
            return acceptWrite
        }
        override fun disconnect() = Unit
    }
}
