package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SunroofOemExtensionsTest {
    @Test fun explicitTiltUsesGlobalSwitchAndVerifiedState() = runBlocking {
        val port = FakeEcarxPort()
        val executor = SunroofVoiceCommandExecutor(port, waitForReadback = {})
        assertTrue(executor.execute(VoiceCommand.SetSunroofTilt(true)) is CommandExecutionResult.Confirmed)
        assertEquals(Triple(0x21030400, 0, 1), port.intWrites.single())
        assertTrue(port.mcuWrites.isEmpty())
        assertEquals(VoiceCommand.SetSunroofTilt(true), CoolrayOemCommandParser.parse("nghiêng cửa sổ trời")?.command)
    }
    @Test fun closeGlassMustCloseKnownTiltBeforeAlreadyClosedPositionCanConfirm() = runBlocking {
        val port = FakeEcarxPort().apply {
            floats[F.BCM_FUNC_WINDOW_CURRENT_POS to 4] = 0f
            ints[F.BCM_FUNC_SUNROOF_TILT to 0] = 1
        }
        val result = SunroofVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.CLOSE))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(Triple(F.BCM_FUNC_SUNROOF_TILT, 0, 0)), port.intWrites)
    }
    @Test fun integerRoofPositionFallbackWorksWithoutFloatApi() = runBlocking {
        val state = FakeEcarxPort()
        val port = object : EcarxFunctionPort by state {
            override fun setFloat(functionId: Int, zone: Int, value: Float) = false
            override fun getFloat(functionId: Int, zone: Int): Float? = null
            override fun getInt(functionId: Int, zone: Int): Int? =
                if (functionId == F.BCM_FUNC_WINDOW_CURRENT_POS) state.ints[F.BCM_FUNC_WINDOW_POS to zone] else state.getInt(functionId, zone)
        }
        val result = SunroofVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 40))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(Triple(F.BCM_FUNC_WINDOW_POS, 4, 40), state.intWrites.single())
    }
    @Test fun zeroPercentMustAlsoCloseKnownTilt() = runBlocking {
        val port = FakeEcarxPort().apply { floats[F.BCM_FUNC_WINDOW_CURRENT_POS to 4] = 0f; ints[F.BCM_FUNC_SUNROOF_TILT to 0] = 1 }
        val result = SunroofVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 0))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(Triple(F.BCM_FUNC_SUNROOF_TILT, 0, 0)), port.intWrites)
    }
}
