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
            VehicleTelemetryState.DEGRADED -> when (detail) {
                "SecurityException" -> "Thiếu quyền truy cập xe"
                "NoSuchMethodError", "NoClassDefFoundError" -> "API xe không tương thích"
                else -> "Có lỗi I/O"
            }
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
    @Volatile var diagnosticSink: ((String) -> Unit)? = null
    @Volatile var archivedDiagnostics: (() -> List<String>)? = null
    private val connections = linkedMapOf<Any, VehicleTelemetrySnapshot>()
    private val failures = java.util.ArrayDeque<String>()
    @Volatile
    private var current = VehicleTelemetrySnapshot(
        state = VehicleTelemetryState.UNAVAILABLE,
        detail = "NOT_INITIALIZED",
        updatedAtMs = 0L
    )

    fun snapshot(): VehicleTelemetrySnapshot = current

    @Synchronized
    fun diagnosticFailures(): List<String> = runCatching { archivedDiagnostics?.invoke() }.getOrNull()
        ?.takeIf { it.isNotEmpty() } ?: failures.toList()

    @Synchronized
    fun recordConnection(backend: String, state: String) {
        val entry = "$backend CONNECT $state"
        failures.addLast(entry)
        runCatching { diagnosticSink?.invoke(entry) }
        while (failures.size > 16) failures.removeFirst()
        runCatching { android.util.Log.i("THTVoiceECARX", entry) }
    }

    /** Numeric API metadata only. Accepted writes are requests, not hardware confirmation. */
    @Synchronized
    fun recordResult(backend: String, operation: String, propertyId: Int, areaId: Int?, outcome: String) {
        val entry = "$backend $operation property=0x${propertyId.toString(16)} area=$areaId $outcome"
        failures.addLast(entry)
        runCatching { diagnosticSink?.invoke(entry) }
        while (failures.size > 16) failures.removeFirst()
        runCatching { android.util.Log.i("THTVoiceECARX", entry) }
    }

    /** Retains failure metadata even when a later successful read changes the status. */
    @Synchronized
    fun recordFailure(backend: String, operation: String, propertyId: Int?, areaId: Int?, error: Throwable) {
        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        val location = propertyId?.let { " property=0x${it.toString(16)} area=$areaId" }.orEmpty()
        val message = cause.message.orEmpty().replace(Regex("[\\r\\n]+"), " ").take(240)
        failures.addLast("$backend $operation$location ${cause.javaClass.simpleName}: $message")
        runCatching { diagnosticSink?.invoke("$backend $operation$location ${cause.javaClass.simpleName}: $message") }
        while (failures.size > 16) failures.removeFirst()
        runCatching { android.util.Log.w("THTVoiceECARX", "$backend $operation$location ${cause.javaClass.simpleName}: $message") }
    }

    @Synchronized
    fun markConnected(nowMs: Long = System.currentTimeMillis(), owner: Any? = null) {
        publish(VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.CONNECTED,
            detail = "PROPERTY_MANAGER_READY",
            updatedAtMs = nowMs
        ), owner, activate = true)
    }

    @Synchronized
    fun markIoSuccess(nowMs: Long = System.currentTimeMillis(), owner: Any? = null) {
        publish(VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.CONNECTED,
            detail = "IO_OK",
            updatedAtMs = nowMs
        ), owner)
    }

    @Synchronized
    fun markIoFailure(reason: String, nowMs: Long = System.currentTimeMillis(), owner: Any? = null) {
        publish(VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.DEGRADED,
            detail = reason.trim().ifBlank { "IO_FAILED" },
            updatedAtMs = nowMs
        ), owner)
    }

    @Synchronized
    fun markUnavailable(reason: String, nowMs: Long = System.currentTimeMillis(), owner: Any? = null) {
        publish(VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.UNAVAILABLE,
            detail = reason.trim().ifBlank { "UNAVAILABLE" },
            updatedAtMs = nowMs
        ), owner, remove = true, unownedFailure = true)
    }

    @Synchronized
    fun markDisconnected(nowMs: Long = System.currentTimeMillis(), owner: Any? = null) {
        publish(VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.DISCONNECTED,
            detail = "DISCONNECTED",
            updatedAtMs = nowMs
        ), owner, remove = true)
    }

    @Synchronized
    fun markServiceDisconnected(nowMs: Long = System.currentTimeMillis(), owner: Any) {
        publish(VehicleTelemetrySnapshot(VehicleTelemetryState.DISCONNECTED, "SERVICE_DISCONNECTED", nowMs), owner)
    }

    // Each bound Car owns its status. Temporary safety probes and retired reconnections
    // must not disconnect another live backend or resurrect their own retired status.
    private fun publish(value: VehicleTelemetrySnapshot, owner: Any?, activate: Boolean = false,
                        remove: Boolean = false, unownedFailure: Boolean = false) {
        if (owner == null) { current = value; return }
        when {
            activate -> connections[owner] = value
            remove -> {
                if (connections.remove(owner) == null) {
                    if (unownedFailure && connections.isEmpty()) current = value
                    return
                }
            }
            owner in connections -> connections[owner] = value
            else -> return
        }
        current = connections.values.lastOrNull { it.state == VehicleTelemetryState.CONNECTED }
            ?: connections.values.lastOrNull() ?: value
    }

    @Synchronized
    fun resetForTest() {
        diagnosticSink = null
        archivedDiagnostics = null
        failures.clear()
        connections.clear()
        current = VehicleTelemetrySnapshot(
            state = VehicleTelemetryState.UNAVAILABLE,
            detail = "NOT_INITIALIZED",
            updatedAtMs = 0L
        )
    }
}
