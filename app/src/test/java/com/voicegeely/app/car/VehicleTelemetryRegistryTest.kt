package com.voicegeely.app.car

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTelemetryRegistryTest {
    @After
    fun tearDown() {
        VehicleTelemetryRegistry.resetForTest()
    }

    @Test
    fun startsUnavailableAndNeverInventsConnectedState() {
        VehicleTelemetryRegistry.resetForTest()

        val snapshot = VehicleTelemetryRegistry.snapshot()

        assertEquals(VehicleTelemetryState.UNAVAILABLE, snapshot.state)
        assertEquals("Chưa có telemetry", snapshot.uiLabel)
    }

    @Test
    fun connectAndSuccessfulIoReportConnected() {
        VehicleTelemetryRegistry.markConnected(nowMs = 10L)
        assertEquals(VehicleTelemetryState.CONNECTED, VehicleTelemetryRegistry.snapshot().state)

        VehicleTelemetryRegistry.markIoSuccess(nowMs = 20L)
        val snapshot = VehicleTelemetryRegistry.snapshot()

        assertEquals(VehicleTelemetryState.CONNECTED, snapshot.state)
        assertEquals("IO_OK", snapshot.detail)
        assertEquals(20L, snapshot.updatedAtMs)
    }

    @Test
    fun ioFailureIsDegradedNotConnected() {
        VehicleTelemetryRegistry.markConnected(nowMs = 10L)
        VehicleTelemetryRegistry.markIoFailure("SecurityException", nowMs = 30L)

        val snapshot = VehicleTelemetryRegistry.snapshot()

        assertEquals(VehicleTelemetryState.DEGRADED, snapshot.state)
        assertEquals("Thiếu quyền truy cập xe", snapshot.uiLabel)
        assertEquals("SecurityException", snapshot.detail)
    }

    @Test
    fun retainsUnderlyingFailureAfterSuccessfulIo() {
        VehicleTelemetryRegistry.resetForTest()
        VehicleTelemetryRegistry.recordFailure("ECARX", "GET_INT", 0x10020100, 0,
            java.lang.reflect.InvocationTargetException(SecurityException("Access denied")))
        VehicleTelemetryRegistry.markIoSuccess()

        assertEquals(VehicleTelemetryState.CONNECTED, VehicleTelemetryRegistry.snapshot().state)
        assertEquals(listOf("ECARX GET_INT property=0x10020100 area=0 SecurityException: Access denied"),
            VehicleTelemetryRegistry.diagnosticFailures())
    }

    @Test
    fun diagnosticHistoryIsBoundedAndClearedByReset() {
        VehicleTelemetryRegistry.resetForTest()
        repeat(20) { index ->
            VehicleTelemetryRegistry.recordFailure("VHAL", "GET_$index", null, null,
                IllegalStateException("Unavailable\nRetry"))
        }
        val failures = VehicleTelemetryRegistry.diagnosticFailures()
        assertEquals(16, failures.size)
        assertEquals("VHAL GET_4 IllegalStateException: Unavailable Retry", failures.first())
        VehicleTelemetryRegistry.resetForTest()
        assertEquals(emptyList<String>(), VehicleTelemetryRegistry.diagnosticFailures())
    }

    @Test
    fun missingRuntimeApiHasSpecificLabel() {
        VehicleTelemetryRegistry.markIoFailure("NoSuchMethodError")
        assertEquals("API xe không tương thích", VehicleTelemetryRegistry.snapshot().uiLabel)
    }

    @Test
    fun unavailableAndDisconnectRemainExplicit() {
        VehicleTelemetryRegistry.markUnavailable("NO_PROPERTY_MANAGER", nowMs = 40L)
        assertEquals(VehicleTelemetryState.UNAVAILABLE, VehicleTelemetryRegistry.snapshot().state)

        VehicleTelemetryRegistry.markConnected(nowMs = 50L)
        VehicleTelemetryRegistry.markDisconnected(nowMs = 60L)
        val snapshot = VehicleTelemetryRegistry.snapshot()

        assertEquals(VehicleTelemetryState.DISCONNECTED, snapshot.state)
        assertEquals("Đã ngắt kết nối", snapshot.uiLabel)
        assertEquals(60L, snapshot.updatedAtMs)
    }
}
