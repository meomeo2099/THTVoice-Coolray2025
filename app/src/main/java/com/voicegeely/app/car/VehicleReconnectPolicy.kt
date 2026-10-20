package com.voicegeely.app.car

/**
 * [REF-10 / S09] When to reconnect the car service and how long to wait.
 *
 * The head unit may start THT Voice before the car service is up (vehicle backend missing until
 * the app restarts), and after sleep the binder can die (DeadObjectException). Retries are bounded
 * with a backoff (reference: 0/5/30/120 s, then give up for this chain); waking the screen starts a
 * new chain. Never retries in a tight loop and never creates a second connection while one is alive.
 */
object VehicleReconnectPolicy {
    private val DELAYS_MS = longArrayOf(0L, 5_000L, 30_000L, 120_000L)
    /** Attempts per chain (screen-on or service start begins a new chain). */
    const val MAX_ATTEMPTS = 6

    private val DEAD_BINDER_HINTS = listOf("DeadObject", "CarNotConnected", "NotConnected", "ServiceNotReady")

    /** True when the current backend should be replaced by a fresh connection. */
    fun needsReconnect(accessPresent: Boolean, state: VehicleTelemetryState, detail: String): Boolean = when {
        !accessPresent -> true
        state == VehicleTelemetryState.DISCONNECTED -> true
        state == VehicleTelemetryState.DEGRADED -> DEAD_BINDER_HINTS.any { detail.contains(it, ignoreCase = true) }
        else -> false
    }

    /** Delay before attempt number [attempt] (0-based) of a chain, or null when the chain is used up. */
    fun delayMs(attempt: Int): Long? = when {
        attempt < 0 -> 0L
        attempt >= MAX_ATTEMPTS -> null
        else -> DELAYS_MS.getOrElse(attempt) { DELAYS_MS.last() }
    }
}
