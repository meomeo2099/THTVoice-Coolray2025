package com.voicegeely.app.car

import org.junit.Assert.*
import org.junit.Test

class EcarxGlobalContractTest {
    enum class Status { ACTIVE, INACTIVE }
    class Functions {
        val globalWrites = mutableListOf<Pair<Int, Int>>()
        val zonedWrites = mutableListOf<Triple<Int, Int, Int>>()
        val values = mutableMapOf<Int, Int>()
        var acceptGlobal = true
        fun isFunctionSupported(id: Int): Status = Status.ACTIVE
        fun isFunctionSupported(id: Int, zone: Int): Status = Status.INACTIVE
        fun getFunctionValue(id: Int): Int = values[id] ?: 0
        fun getFunctionValue(id: Int, zone: Int): Int = 0
        fun setFunctionValue(id: Int, value: Int): Boolean {
            globalWrites += id to value
            if (acceptGlobal) values[id] = value
            return acceptGlobal
        }
        fun setFunctionValue(id: Int, zone: Int, value: Int): Boolean { zonedWrites += Triple(id, zone, value); return zone != 0 }
    }
    class Car(val functions: Functions) {
        fun connect() {}
        fun disconnect() {}
        fun isConnected() = true
        fun getCarPropertyManager(): Any = this
        fun getICarFunction() = functions
    }
    @Test fun globalSwitchUsesCanonicalNonZonedOemSignatures() {
        val fn = Functions()
        val port = EcarxFunctionAccess.fromCar(Car(fn))
        assertEquals(true, port.isSupported(Coolray2025Functions.HVAC_FUNC_AUTO, 0))
        assertTrue(port.setInt(Coolray2025Functions.HVAC_FUNC_AUTO, 0, 1))
        assertEquals(1, port.getInt(Coolray2025Functions.HVAC_FUNC_AUTO, 0))
        assertEquals(listOf(Coolray2025Functions.HVAC_FUNC_AUTO to 1), fn.globalWrites)
        assertTrue(fn.zonedWrites.isEmpty())
    }
    @Test fun explicitWindowZoneKeepsZonedSignature() {
        val fn = Functions()
        val port = EcarxFunctionAccess.fromCar(Car(fn))
        assertTrue(port.setInt(Coolray2025Functions.BCM_FUNC_WINDOW_POS, 256, 50))
        assertEquals(listOf(Triple(Coolray2025Functions.BCM_FUNC_WINDOW_POS, 256, 50)), fn.zonedWrites)
        assertTrue(fn.globalWrites.isEmpty())
    }
    @Test fun rejectedGlobalWriteIsNotRetriedWithAnotherZone() {
        val fn = Functions().apply { acceptGlobal = false }
        assertFalse(EcarxFunctionAccess.fromCar(Car(fn)).setInt(Coolray2025Functions.HVAC_FUNC_AC, 0, 1))
        assertEquals(1, fn.globalWrites.size)
        assertTrue(fn.zonedWrites.isEmpty())
    }
}
