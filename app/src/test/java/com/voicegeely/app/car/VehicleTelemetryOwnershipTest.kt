package com.voicegeely.app.car

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class VehicleTelemetryOwnershipTest {
    @After fun clear() = VehicleTelemetryRegistry.resetForTest()
    @Test fun retiredConnectionCannotOverwriteReplacement() {
        val old = Any(); val fresh = Any()
        VehicleTelemetryRegistry.markConnected(owner=old)
        VehicleTelemetryRegistry.markConnected(owner=fresh)
        val replacement = VehicleTelemetryRegistry.snapshot()
        VehicleTelemetryRegistry.markDisconnected(owner=old)
        VehicleTelemetryRegistry.markIoFailure("old binder", owner=old)
        VehicleTelemetryRegistry.markUnavailable("old failure", owner=old)
        VehicleTelemetryRegistry.markIoSuccess(owner=old)
        assertEquals(replacement, VehicleTelemetryRegistry.snapshot())
        VehicleTelemetryRegistry.markDisconnected(owner=fresh)
        assertEquals(VehicleTelemetryState.DISCONNECTED, VehicleTelemetryRegistry.snapshot().state)
    }
    @Test fun closingTemporaryProbeKeepsServiceConnected() {
        val service = Any(); val probe = Any()
        VehicleTelemetryRegistry.markConnected(owner=service)
        VehicleTelemetryRegistry.markConnected(owner=probe)
        VehicleTelemetryRegistry.markDisconnected(owner=probe)
        assertEquals(VehicleTelemetryState.CONNECTED, VehicleTelemetryRegistry.snapshot().state)
        VehicleTelemetryRegistry.markDisconnected(owner=service)
        assertEquals(VehicleTelemetryState.DISCONNECTED, VehicleTelemetryRegistry.snapshot().state)
        VehicleTelemetryRegistry.markIoSuccess(owner=probe)
        assertEquals(VehicleTelemetryState.DISCONNECTED, VehicleTelemetryRegistry.snapshot().state)
    }
    @Test fun transientServiceDisconnectCanRecoverButRetiredOwnerCannot() {
        val owner = Any()
        VehicleTelemetryRegistry.markConnected(owner=owner)
        VehicleTelemetryRegistry.markServiceDisconnected(owner=owner)
        assertEquals(VehicleTelemetryState.DISCONNECTED, VehicleTelemetryRegistry.snapshot().state)
        VehicleTelemetryRegistry.markIoSuccess(owner=owner)
        assertEquals(VehicleTelemetryState.CONNECTED, VehicleTelemetryRegistry.snapshot().state)
        VehicleTelemetryRegistry.markDisconnected(owner=owner)
        VehicleTelemetryRegistry.markServiceDisconnected(owner=owner)
        VehicleTelemetryRegistry.markIoSuccess(owner=owner)
        assertEquals(VehicleTelemetryState.DISCONNECTED, VehicleTelemetryRegistry.snapshot().state)
    }
    @Test fun activeIoStillChangesHealth() {
        val owner = Any()
        VehicleTelemetryRegistry.markConnected(owner=owner)
        VehicleTelemetryRegistry.markIoFailure("SecurityException", owner=owner)
        assertEquals(VehicleTelemetryState.DEGRADED, VehicleTelemetryRegistry.snapshot().state)
        VehicleTelemetryRegistry.markIoSuccess(owner=owner)
        assertEquals(VehicleTelemetryState.CONNECTED, VehicleTelemetryRegistry.snapshot().state)
    }
}
