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
        assertEquals("Có lỗi I/O", snapshot.uiLabel)
        assertEquals("SecurityException", snapshot.detail)
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
