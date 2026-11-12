package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.CoolrayProperties as P
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BinhDinhHvacTest {
    @Test fun ownerReportedRegionalWordsKeepTheIntendedTypedCommand() {
        for ((heard, correct) in listOf(
            "lấy gió treo" to "lấy gió trong", "chuyển sang lấy gió treo" to "lấy gió trong",
            "hạ kính bên tay" to "hạ kính bên lái", "đóng kính seo bên tay" to "đóng kính sau bên lái",
            "hạ kính seo bên phụ" to "hạ kính sau bên phụ", "hạ kính seo tay 50 phần trăm" to "hạ kính sau bên lái 50 phần trăm",
            "sái kính trước" to "sấy kính trước", "sáy kính seo" to "sấy kính sau", "tắt sáy kính seo" to "tắt sấy kính sau"
        )) {
            val expected = VoiceParseChain.parse(correct)?.command
            assertNotNull(correct, expected)
            assertEquals(heard, expected, VoiceParseChain.parse(heard)?.command)
        }
        for (text in listOf("đi đến đường tay", "mở ứng dụng seo", "tìm bài hát lấy gió treo", "đi đến tiệm sáy", "sái nhà"))
            assertEquals(text, text, VoiceTextNormalizer.normalize(text))
    }

    @Test fun autoCommandsAreHvacAndNeverAutoHold() {
        for (text in listOf("bật auto", "bật chế độ auto", "mở điều hòa tự động"))
            assertEquals(text, VoiceCommand.HvacAuto(true), VoiceParseChain.parse(text)?.command)
        assertEquals(VoiceCommand.HvacAuto(false), VoiceParseChain.parse("tắt chế độ auto")?.command)
        assertEquals(VoiceCommand.OemSwitch(OemFeature.AUTO_HOLD, true), VoiceParseChain.parse("bật auto hold")?.command)
    }

    @Test fun autoPronunciationVariantsStayInsideClimateContext() {
        for (variant in listOf("aoto", "aotu", "ao to", "ao tô", "ao tu", "au to", "au tô", "au tu", "a tô", "a to", "a tu", "a o tô", "a u tô")) {
            assertEquals(variant, VoiceCommand.HvacAuto(true), VoiceParseChain.parse("bật chế độ $variant")?.command)
            assertEquals(variant, VoiceCommand.HvacAuto(false), VoiceParseChain.parse("tắt điều hòa $variant")?.command)
        }
        assertEquals(VoiceCommand.HvacAuto(true), VoiceParseChain.parse("bật điều hòa ô tô")?.command)
        assertEquals(VoiceCommand.HvacAuto(true), VoiceParseChain.parse("bật chế độ ô tô")?.command)
        assertTrue(VoiceParseChain.parse("bật ô tô")?.command !is VoiceCommand.HvacAuto)
        assertTrue(VoiceParseChain.parse("bật aoto hold")?.command !is VoiceCommand.HvacAuto)
        assertEquals("mở ứng dụng aotu", VoiceTextNormalizer.normalize("mở ứng dụng Aotu"))
        assertEquals("đi đến ao tô", VoiceTextNormalizer.normalize("đi đến Ao Tô"))
    }

    private class Climate : VehiclePropertyAccess {
        val writes = mutableListOf<String>()
        val bools = mutableMapOf<Int, Boolean>()
        var temperature = 24f
        var rejectAc = false
        var unreadableCooling = false
        override fun getIntProperty(propertyId: Int, areaId: Int) = -1
        override fun getFloatProperty(propertyId: Int, areaId: Int) = temperature
        override fun getBoolProperty(propertyId: Int, areaId: Int) = bools[propertyId] ?: false
        override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? = if (unreadableCooling) null else getBoolProperty(propertyId, areaId)
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int) = false
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean { writes += "TEMP"; temperature = value; return true }
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean {
            writes += if (propertyId == P.HVAC_POWER) "POWER" else "AC"
            if (propertyId == P.HVAC_AC && rejectAc) return false
            bools[propertyId] = value
            return true
        }
        override fun disconnect() {}
    }

    @Test fun relativeTemperatureRequestsSystemAndAcBeforeHalfDegreeSetpoint() = runBlocking {
        for (delta in listOf(-0.5f, 0.5f)) {
            val car = Climate()
            assertTrue(HvacVoiceCommandExecutor(car, {}).execute(VoiceCommand.AdjustTemperature(delta)) is CommandExecutionResult.Confirmed)
            assertEquals(listOf("POWER", "AC", "TEMP"), car.writes)
            assertEquals(24f + delta, car.temperature, 0f)
        }
    }
    @Test fun rejectedAcPreventsTemperatureWrite() = runBlocking {
        val car = Climate().apply { rejectAc = true }
        assertTrue(HvacVoiceCommandExecutor(car, {}).execute(VoiceCommand.SetTemperature(22f)) is CommandExecutionResult.Rejected)
        assertEquals(listOf("POWER", "AC"), car.writes)
        assertEquals(24f, car.temperature, 0f)
    }
    @Test fun temperatureReadbackCannotConfirmUnknownAcState() = runBlocking {
        val car = Climate().apply { unreadableCooling = true }
        assertTrue(HvacVoiceCommandExecutor(car, {}).execute(VoiceCommand.SetTemperature(22f)) is CommandExecutionResult.AcceptedUnverified)
    }
    @Test fun invalidTemperatureDoesNotPowerOnAnything() = runBlocking {
        for (value in listOf(15f, 15.9f, 32.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val car = Climate()
            assertTrue(HvacVoiceCommandExecutor(car, {}).execute(VoiceCommand.SetTemperature(value)) is CommandExecutionResult.Rejected)
            assertTrue(car.writes.isEmpty())
        }
    }
    @Test fun alreadyOnCoolingAvoidsRedundantPowerWrites() = runBlocking {
        val car = Climate().apply { bools[P.HVAC_POWER] = true; bools[P.HVAC_AC] = true }
        assertTrue(HvacVoiceCommandExecutor(car, {}).execute(VoiceCommand.SetTemperature(22f)) is CommandExecutionResult.Confirmed)
        assertEquals(listOf("TEMP"), car.writes)
    }
    @Test fun enablingAutoStartsClimatePowerFirst() = runBlocking {
        val port = FakeEcarxPort()
        assertTrue(CoolrayOemCommandExecutor(port) {}.execute(VoiceCommand.HvacAuto(true)) is CommandExecutionResult.Confirmed)
        assertEquals(listOf(Triple(F.HVAC_FUNC_POWER, 0, 1), Triple(F.HVAC_FUNC_AUTO, 0, 1)), port.intWrites)
    }
    @Test fun unsupportedAutoDoesNotSwitchClimatePowerOn() = runBlocking {
        val port = FakeEcarxPort().apply { unsupported += F.HVAC_FUNC_AUTO }
        assertTrue(CoolrayOemCommandExecutor(port) {}.execute(VoiceCommand.HvacAuto(true)) is CommandExecutionResult.Rejected)
        assertTrue(port.intWrites.isEmpty())
    }
    @Test fun autoOffDoesNotEnablePowerOrCompressor() = runBlocking {
        val port = FakeEcarxPort().apply { ints[F.HVAC_FUNC_AUTO to 0] = 1 }
        assertTrue(CoolrayOemCommandExecutor(port) {}.execute(VoiceCommand.HvacAuto(false)) is CommandExecutionResult.Confirmed)
        assertEquals(listOf(Triple(F.HVAC_FUNC_AUTO, 0, 0)), port.intWrites)
    }
    @Test fun autoStateCannotHideUnknownPowerReadback() = runBlocking {
        val port = FakeEcarxPort(reflectWrites = false).apply { ints[F.HVAC_FUNC_AUTO to 0] = 1 }
        assertTrue(CoolrayOemCommandExecutor(port) {}.execute(VoiceCommand.HvacAuto(true)) is CommandExecutionResult.AcceptedUnverified)
    }
}
