package com.voicegeely.app.car

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-process diagnostic context for vehicle commands.
 *
 * - `traceId` identifies one typed vehicle command on the current thread.
 * - `scope` identifies the typed executor/command family without storing speech text.
 * - `writeSeq` increments for every attempted low-level VHAL write.
 *
 * This object is diagnostics-only: it must never change actuator behavior,
 * property mappings, retry timing, or read-back decisions.
 */
object VehicleDebugTrace {
    private data class TraceContext(val id: Long, val scope: String)

    private val commandCounter = AtomicLong(0L)
    private val writeCounter = AtomicLong(0L)
    private val currentTrace = ThreadLocal<TraceContext?>()

    fun beginCommand(scope: String = "VEHICLE"): Long {
        val id = commandCounter.incrementAndGet()
        currentTrace.set(TraceContext(id, scope))
        return id
    }

    fun currentTraceId(): Long? = currentTrace.get()?.id

    fun currentScope(): String? = currentTrace.get()?.scope

    fun endCommand(traceId: Long) {
        if (currentTrace.get()?.id == traceId) currentTrace.remove()
    }

    fun markWrite(): Long = writeCounter.incrementAndGet()

    fun writeSequence(): Long = writeCounter.get()
}
