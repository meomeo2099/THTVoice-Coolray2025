package com.voicegeely.app.car

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EcarxFailureReportingTest {
    class RefusingFunction {
        fun setFunctionValue(function: Int, zone: Int, value: Int): Boolean = false
    }
    class RefusingCar {
        fun sendBytesToMcuByPropID(property: Int, bytes: ByteArray): Boolean = false
    }
    private fun access(function: Any = RefusingFunction(), car: Any = RefusingCar()): EcarxFunctionAccess {
        val setter = function.javaClass.methods.firstOrNull { it.name == "setFunctionValue" }
        return EcarxFunctionAccess::class.java.declaredConstructors.single { it.parameterCount == 7 }.apply { isAccessible = true }
            .newInstance(car, function, null, null, setter, null, null) as EcarxFunctionAccess
    }
    @Before fun reset() = VehicleTelemetryRegistry.resetForTest()
    @Test fun falseSetterResultRetainsOperationAndLocation() {
        assertFalse(access().setInt(0x21030300, 16, 50))
        val report = VehicleTelemetryRegistry.diagnosticFailures().joinToString()
        assertTrue(report, report.contains("setFunctionValue"))
        assertTrue(report, report.contains("0x21030300"))
        assertTrue(report, report.contains("16"))
        assertTrue(report, report.contains("REJECTED"))
    }
    @Test fun missingSetterAndRefusedMcuAreDistinguishable() {
        val port = access(function = Any())
        assertFalse(port.setInt(1, 16, 50))
        assertFalse(port.sendBytesToMcu(0x287000a2, byteArrayOf(50)))
        val report = VehicleTelemetryRegistry.diagnosticFailures().joinToString()
        assertTrue(report, report.contains("METHOD_UNAVAILABLE"))
        assertTrue(report, report.contains("SEND_MCU"))
        assertTrue(report, report.contains("REJECTED"))
    }
}
