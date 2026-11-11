package com.voicegeely.app.car

import org.junit.Assert.*
import org.junit.Test

class EcarxCarConnectionTest {
    open class SignalManager {
        val events = mutableListOf<String>()
        var connected = false
        var managerReady = false
        var polls = 0
        var connects = 0
        var disconnects = 0
        fun connect() { events += "connect"; connects++; connected = true }
        fun isConnected(): Boolean { polls++; return connected }
        fun getCarPropertyManager(): Any? {
            if (connected && polls >= 3) managerReady = true
            return if (managerReady) this else null
        }
        fun disconnect() { disconnects++; connected = false; managerReady = false }
    }
    class Car : SignalManager() {
        var bytes: ByteArray? = null
        fun getBytesByPropID(property: Int, area: Int): ByteArray? = bytes
        fun getIntByPropID(property: Int, area: Int) = if (bytes == null) -1 else 0 // empty payload default
        fun getSensorManager() = Sensor()
        fun getICarFunction(): Function {
            check(connected && managerReady) { "ICar called before OEM service ready" }
            events += "function"
            return Function()
        }
    }
    class Sensor {
        fun getSensorLatestValue(sensor: Int): Float = if (sensor == 0x00100800) 321f else Float.NaN
    }
    class Function {
        fun setFunctionValue(function: Int, zone: Int, value: Int) = true
        fun getFunctionValue(function: Int, zone: Int) = 50
    }
    @Test fun productionFactoryConnectsAndWaitsBeforeObtainingSingletonFunction() {
        val car = Car()
        val port = EcarxFunctionAccess.fromCar(car)
        assertEquals(listOf("connect", "function"), car.events)
        assertEquals(1, car.connects)
        assertTrue(car.polls >= 3)
        assertTrue(port.setInt(0x21030300, 16, 50))
    }
    @Test fun alreadyReadyConnectionIsReused() {
        val car = Car().apply { connected = true; managerReady = true }
        EcarxFunctionAccess.fromCar(car)
        assertEquals(listOf("function"), car.events)
        assertEquals(0, car.connects)
    }
    @Test fun unreadyManagerTimesOutAndClosesPartialConnection() {
        val car = SignalManager()
        var clock = 0L
        // Keep this fake from ever publishing its manager.
        val neverReady = object {
            var closed = false
            fun connect() { car.connect() }
            fun isConnected() = true
            fun getCarPropertyManager(): Any? = null
            fun disconnect() { closed = true }
        }
        try {
            EcarxCarConnection.awaitConnected(neverReady, 100, { clock }, { clock += it })
            fail("must time out")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("not ready"))
        }
        assertEquals(100L, clock)
        assertTrue(neverReady.closed)
    }
    @Test fun interruptionClosesPartialConnectionAndPreservesCancellation() {
        val car = SignalManager()
        try {
            EcarxCarConnection.awaitConnected(car, pause = { throw InterruptedException("session cancelled") })
            fail("must stop on interruption")
        } catch (expected: InterruptedException) {
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(1, car.disconnects)
        } finally { Thread.interrupted() }
    }
    @Test fun missingOrEmptyMcuBytesMustNotBecomeClosedWindowZero() {
        val car = Car().apply { connected = true; managerReady = true }
        val port = EcarxFunctionAccess.fromCar(car)
        assertNull(port.getIntByProp(0x2870009d, 0x21030300))
        car.bytes = byteArrayOf()
        assertNull(port.getIntByProp(0x2870009d, 0x21030300))
        car.bytes = byteArrayOf(50)
        assertEquals(50, port.getIntByProp(0x2870009d, 0x21030300))
        assertEquals(321f, port.getSensorLatestValue(0x00100800))
        assertNull(port.getSensorLatestValue(123))
    }
}
