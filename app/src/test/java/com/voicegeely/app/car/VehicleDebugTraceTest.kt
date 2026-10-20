package com.voicegeely.app.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDebugTraceTest {
    @Test
    fun commandScopeIsBoundAndClearedOnCurrentThread() {
        val traceId = VehicleDebugTrace.beginCommand("HVAC:SetTemperature")

        assertEquals(traceId, VehicleDebugTrace.currentTraceId())
        assertEquals("HVAC:SetTemperature", VehicleDebugTrace.currentScope())

        VehicleDebugTrace.endCommand(traceId)
        assertNull(VehicleDebugTrace.currentTraceId())
        assertNull(VehicleDebugTrace.currentScope())
    }

    @Test
    fun wrongTraceIdDoesNotClearActiveContext() {
        val traceId = VehicleDebugTrace.beginCommand("WINDOW:OPEN")

        VehicleDebugTrace.endCommand(traceId + 1)
        assertEquals(traceId, VehicleDebugTrace.currentTraceId())
        assertEquals("WINDOW:OPEN", VehicleDebugTrace.currentScope())

        VehicleDebugTrace.endCommand(traceId)
    }

    @Test
    fun writeSequenceIsMonotonic() {
        val before = VehicleDebugTrace.writeSequence()
        val first = VehicleDebugTrace.markWrite()
        val second = VehicleDebugTrace.markWrite()

        assertTrue(first > before)
        assertTrue(second > first)
        assertEquals(second, VehicleDebugTrace.writeSequence())
    }
}
