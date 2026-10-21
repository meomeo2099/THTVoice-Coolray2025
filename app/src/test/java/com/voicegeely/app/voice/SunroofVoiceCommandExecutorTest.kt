package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coolray 2025 sunroof glass through the ECARX AdaptAPI, zone WINDOW_ROOF_TOP (4). */
class SunroofVoiceCommandExecutorTest {
    private fun exec(port: FakeEcarxPort) = SunroofVoiceCommandExecutor(port, waitForReadback = {})

    @Test fun openGlassWritesRoofZoneAndConfirms() = runBlocking {
        val port = FakeEcarxPort().apply { floats[F.BCM_FUNC_WINDOW_POS to 4] = 0f }
        val result = exec(port).execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(Triple(F.BCM_FUNC_WINDOW_POS, 4, 100f), port.floatWrites.single())
    }

    @Test fun percentIsWrittenAsIs() = runBlocking {
        val port = FakeEcarxPort()
        exec(port).execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 40))
        assertEquals(40f, port.floatWrites.single().third)
    }

    @Test fun shadeUsesMcuGroupIndexSix() = runBlocking {
        val port = FakeEcarxPort().apply { mcuAccepts = true; props[F.INFO_ID_WINDOW_SUNSHADE] = 0 }
        val result = exec(port).execute(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf<Byte>(-1, -1, -1, -1, -1, -1, 100, -1), port.mcuWrites.single().second.toList())
    }

    @Test fun shadeWithoutMcuIsRejectedWithoutAdaptApiWrite() = runBlocking {
        val port = FakeEcarxPort()
        val result = exec(port).execute(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(port.floatWrites.isEmpty() && port.intWrites.isEmpty())
    }

    @Test fun glassUsesMcuGroupIndexFourFirst() = runBlocking {
        val port = FakeEcarxPort().apply { mcuAccepts = true; props[F.INFO_ID_WINDOW_SUNROOF] = 0 }
        val result = exec(port).execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 60))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(60.toByte(), port.mcuWrites.single().second[4])
        assertTrue(port.floatWrites.isEmpty())
    }

    @Test fun alreadyAtTargetSkipsWrite() = runBlocking {
        val port = FakeEcarxPort().apply { floats[F.BCM_FUNC_WINDOW_CURRENT_POS to 4] = 0f }
        val result = exec(port).execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.CLOSE))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertTrue(port.floatWrites.isEmpty())
    }

    @Test fun noReadBackIsNotSuccess() = runBlocking {
        val port = FakeEcarxPort().apply { unreadable += F.BCM_FUNC_WINDOW_POS; unreadable += F.BCM_FUNC_WINDOW_CURRENT_POS }
        val result = exec(port).execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    @Test fun rejectedWriteIsRejected() = runBlocking {
        val result = exec(FakeEcarxPort(acceptWrites = false)).execute(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 30))
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test fun capabilityIsEnabled() {
        assertTrue(CoolrayCapabilities.isEnabled(CoolrayCapabilities.Family.SUNROOF))
    }
}
