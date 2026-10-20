package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coolray 2025 windows through the ECARX AdaptAPI (BCM_FUNC_WINDOW_POS, zones 16/32/256/512). */
class WindowVoiceCommandExecutorTest {
    private fun exec(port: FakeEcarxPort) = WindowVoiceCommandExecutor(port, waitForReadback = {})

    @Test fun driverWindowThirtyPercentConfirms() = runBlocking {
        val port = FakeEcarxPort().apply { floats[F.BCM_FUNC_WINDOW_POS to F.WINDOW_ROW_1_LEFT] = 0f }
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.SET_PERCENT, 30))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(Triple(F.BCM_FUNC_WINDOW_POS, F.WINDOW_ROW_1_LEFT, 30f), port.floatWrites.single())
    }

    @Test fun everyTargetMapsToItsAdaptApiZone() = runBlocking {
        val expected = mapOf(
            WindowTarget.FRONT_LEFT to 16, WindowTarget.FRONT_RIGHT to 32,
            WindowTarget.REAR_LEFT to 256, WindowTarget.REAR_RIGHT to 512
        )
        for ((target, zone) in expected) {
            val port = FakeEcarxPort()
            exec(port).execute(VoiceCommand.Window(setOf(target), WindowOperation.OPEN))
            assertEquals("$target", zone, port.floatWrites.single().second)
            assertEquals(100f, port.floatWrites.single().third)
        }
    }

    @Test fun allWindowsWriteFourZonesAndConfirm() = runBlocking {
        val port = FakeEcarxPort()
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.CLOSE))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(16, 32, 256, 512), port.floatWrites.map { it.second })
        assertTrue(port.floatWrites.all { it.third == 0f })
    }

    @Test fun refusedPositionWriteFallsBackToOpenCloseSwitchOnce() = runBlocking {
        val port = object : com.voicegeely.app.car.EcarxFunctionPort {
            val ints = mutableListOf<Triple<Int, Int, Int>>()
            override fun isSupported(functionId: Int, zone: Int) = true
            override fun getInt(functionId: Int, zone: Int): Int? = null
            override fun setInt(functionId: Int, zone: Int, value: Int): Boolean { ints += Triple(functionId, zone, value); return true }
            override fun getFloat(functionId: Int, zone: Int): Float? = null
            override fun setFloat(functionId: Int, zone: Int, value: Float) = false
        }
        val result = WindowVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN))
        assertEquals(listOf(Triple(F.BCM_FUNC_WINDOW, 16, F.ON)), port.ints)
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    @Test fun noReadBackIsNeverSuccess() = runBlocking {
        val port = FakeEcarxPort().apply {
            unreadable += F.BCM_FUNC_WINDOW_POS
            unreadable += F.BCM_FUNC_WINDOW_CURRENT_POS
        }
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    @Test fun movementTowardTargetIsNotClaimedAsComplete() = runBlocking {
        val port = FakeEcarxPort(reflectWrites = false).apply {
            floats[F.BCM_FUNC_WINDOW_CURRENT_POS to 16] = 0f
            floatSequences[F.BCM_FUNC_WINDOW_CURRENT_POS to 16] = mutableListOf(0f, 20f, 40f, 60f, 70f, 80f)
        }
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
        assertTrue((result as CommandExecutionResult.AcceptedUnverified).detail!!.contains("đang di chuyển"))
    }

    @Test fun rejectedWriteRejectsWholeCommand() = runBlocking {
        val port = FakeEcarxPort(acceptWrites = false)
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.SET_PERCENT, 50))
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test fun inactiveFunctionIsRejectedWithoutWrite() = runBlocking {
        val port = FakeEcarxPort().apply { unsupported += F.BCM_FUNC_WINDOW_POS; unsupported += F.BCM_FUNC_WINDOW }
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(port.floatWrites.isEmpty() && port.intWrites.isEmpty())
    }

    @Test fun stopSendsPauseAndNeverClaimsConfirmed() = runBlocking {
        val port = FakeEcarxPort()
        val result = exec(port).execute(VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.STOP))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
        assertEquals(4, port.intWrites.size)
        assertTrue(port.intWrites.all { it.first == F.BCM_FUNC_WINDOW && it.third == F.WINDOW_PAUSE })
        assertFalse(result is CommandExecutionResult.Confirmed)
    }
}
