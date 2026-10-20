package com.voicegeely.app.car

import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.SwappableCommandExecutor
import com.voicegeely.app.voice.VoiceCommand
import com.voicegeely.app.voice.VoiceCommandExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleReconnectPolicyTest {
    @Test fun missingBackendReconnects() {
        assertTrue(VehicleReconnectPolicy.needsReconnect(false, VehicleTelemetryState.CONNECTED, "IO_OK"))
    }

    @Test fun healthyBackendLeftAlone() {
        assertFalse(VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.CONNECTED, "IO_OK"))
        assertFalse(VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.UNAVAILABLE, "NOT_INITIALIZED"))
    }

    @Test fun deadBinderReconnectsButOrdinaryIoErrorDoesNot() {
        assertTrue(VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.DEGRADED, "DeadObjectException"))
        assertTrue(VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.DEGRADED, "CarNotConnectedException"))
        assertFalse("one property missing is not a dead car service",
            VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.DEGRADED, "IllegalArgumentException"))
        assertFalse(VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.DEGRADED, "SecurityException"))
        assertTrue(VehicleReconnectPolicy.needsReconnect(true, VehicleTelemetryState.DISCONNECTED, "DISCONNECTED"))
    }

    @Test fun backoffIsBoundedAndGrows() {
        assertEquals(0L, VehicleReconnectPolicy.delayMs(0))
        assertEquals(5_000L, VehicleReconnectPolicy.delayMs(1))
        assertEquals(30_000L, VehicleReconnectPolicy.delayMs(2))
        assertEquals(120_000L, VehicleReconnectPolicy.delayMs(3))
        assertEquals(120_000L, VehicleReconnectPolicy.delayMs(VehicleReconnectPolicy.MAX_ATTEMPTS - 1))
        assertNull(VehicleReconnectPolicy.delayMs(VehicleReconnectPolicy.MAX_ATTEMPTS))
        var last = -1L
        for (i in 0 until VehicleReconnectPolicy.MAX_ATTEMPTS) {
            val d = VehicleReconnectPolicy.delayMs(i)!!
            assertTrue(d >= last); last = d
        }
    }

    @Test fun swappableExecutorUsesCurrentDelegate() = runBlocking {
        val a = object : VoiceCommandExecutor { override suspend fun execute(command: VoiceCommand) = CommandExecutionResult.Failed("a") }
        val b = object : VoiceCommandExecutor { override suspend fun execute(command: VoiceCommand) = CommandExecutionResult.Failed("b") }
        val swap = SwappableCommandExecutor(a)
        val cmd = VoiceCommand.SetFanSpeed(3)
        assertEquals("a", (swap.execute(cmd) as CommandExecutionResult.Failed).reason)
        swap.delegate = b
        assertEquals("b", (swap.execute(cmd) as CommandExecutionResult.Failed).reason)
    }
}
