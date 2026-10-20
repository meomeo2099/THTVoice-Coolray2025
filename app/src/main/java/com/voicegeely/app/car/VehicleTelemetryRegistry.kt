package com.voicegeely.app.car

enum class VehicleTelemetryState {
    UNAVAILABLE,
    CONNECTED,
    DEGRADED,
    DISCONNECTED
}

data class VehicleTelemetrySnapshot(
    val state: VehicleTelemetryState,
    val detail: String,
    val updatedAtMs: Long
) {
    val uiLabel: String
        get() = when (state) {
            VehicleTelemetryState.CONNECTED -> "Đã kết nối"
            VehicleTelemetryState.DEGRADED -> "Có lỗi I/O"
            VehicleTelemetryState.DISCONNECTED -> "Đã ngắt kết nối"
            VehicleTelemetryState.UNAVAILABLE -> "Chưa có telemetry"
        }
}

/**
 * Process-local, read-only telemetry for Assistant health UI.
 *
 * This registry intentionally stores only VHAL availability/health state and a
 * coarse non-sensitive reason. It never stores VIN, transcript, activation code
 * or vehicle property values.
 */
object VehicleTelemetryRegistry {
    @Volatile
    private var current = VehicleTelemetrySnapshot(
        state = VehicleTelemetryState.UNAVAILABLE,
        detail = "NOT_INITIALIZED",
        updatedAtMs = 0L
    )

    fun snapshot(): VehicleTelemetrySnapshot = current

    @Synchronized
    fun markConnected(nowMs: Long = System.currentTimeMillis()) {
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.CONNECTED,
            detail = "PROPERTY_MANAGER_READY",
            updatedAtMs = nowMs
        )
    }

    @Synchronized
    fun markIoSuccess(nowMs: Long = System.currentTimeMillis()) {
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.CONNECTED,
            detail = "IO_OK",
            updatedAtMs = nowMs
        )
    }

    @Synchronized
    fun markIoFailure(reason: String, nowMs: Long = System.currentTimeMillis()) {
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.DEGRADED,
            detail = reason.trim().ifBlank { "IO_FAILED" },
            updatedAtMs = nowMs
        )
    }

    @Synchronized
    fun markUnavailable(reason: String, nowMs: Long = System.currentTimeMillis()) {
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.UNAVAILABLE,
            detail = reason.trim().ifBlank { "UNAVAILABLE" },
            updatedAtMs = nowMs
        )
    }

    @Synchronized
    fun markDisconnected(nowMs: Long = System.currentTimeMillis()) {
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.DISCONNECTED,
            detail = "DISCONNECTED",
            updatedAtMs = nowMs
        )
    }

    @Synchronized
    fun resetForTest() {
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.UNAVAILABLE,
            detail = "NOT_INITIALIZED",
            updatedAtMs = 0L
        )
    }
}
