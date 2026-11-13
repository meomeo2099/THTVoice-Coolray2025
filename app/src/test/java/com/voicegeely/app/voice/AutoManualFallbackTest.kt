package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutoManualFallbackTest {
    private fun fake(auto: Int? = 1, fan: Int? = 3) = FakeEcarxPort(reflectWrites = false).apply {
        auto?.let { ints[F.HVAC_FUNC_AUTO to F.GLOBAL] = it }
        fan?.let { ints[F.HVAC_FUNC_FAN_SPEED to F.ZONE_ROW_1_LEFT] = F.level(F.HVAC_FUNC_FAN_SPEED, it) }
    }
    @Test fun acceptedOffCanEnterManualAtSameFanLevel() = runBlocking {
        val base = fake()
        val port = object : EcarxFunctionPort by base {
            override fun setInt(functionId: Int, zone: Int, value: Int): Boolean {
                val ok = base.setInt(functionId, zone, value)
                if (functionId == F.HVAC_FUNC_FAN_SPEED && ok) base.ints[F.HVAC_FUNC_AUTO to 0] = 0
                return ok
            }
        }
        val result = CoolrayOemCommandExecutor(port) {}.execute(VoiceCommand.HvacAuto(false))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(Triple(F.HVAC_FUNC_AUTO, 0, 0), Triple(F.HVAC_FUNC_FAN_SPEED, 0, F.level(F.HVAC_FUNC_FAN_SPEED, 3))), base.intWrites)
    }
    @Test fun absentOrInvalidFanAndUnknownAutoNeverGuess() = runBlocking {
        for (base in listOf(fake(fan=null), fake(fan=8), fake(auto=null), fake().apply { unsupported += F.HVAC_FUNC_FAN_SPEED })) {
            assertTrue(CoolrayOemCommandExecutor(base) {}.execute(VoiceCommand.HvacAuto(false)) is CommandExecutionResult.AcceptedUnverified)
            assertEquals(listOf(Triple(F.HVAC_FUNC_AUTO, 0, 0)), base.intWrites)
        }
    }
    @Test fun manualWriteWithoutAutoReadbackNeverClaimsSuccess() = runBlocking {
        val base = fake()
        assertTrue(CoolrayOemCommandExecutor(base) {}.execute(VoiceCommand.HvacAuto(false)) is CommandExecutionResult.AcceptedUnverified)
        assertEquals(2, base.intWrites.size)
        assertEquals(1, base.intWrites.count { it.first == F.HVAC_FUNC_FAN_SPEED })
    }
    @Test fun alreadyOffAndDeniedOffNeverChangeFan() = runBlocking {
        val off = fake(auto=0)
        assertTrue(CoolrayOemCommandExecutor(off) {}.execute(VoiceCommand.HvacAuto(false)) is CommandExecutionResult.Confirmed)
        assertTrue(off.intWrites.isEmpty())
        val denied = FakeEcarxPort(acceptWrites=false).apply { ints[F.HVAC_FUNC_AUTO to 0] = 1 }
        assertTrue(CoolrayOemCommandExecutor(denied) {}.execute(VoiceCommand.HvacAuto(false)) is CommandExecutionResult.Rejected)
        assertEquals(listOf(Triple(F.HVAC_FUNC_AUTO, 0, 0)), denied.intWrites)
    }
}
