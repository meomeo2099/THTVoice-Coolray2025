package com.voicegeely.app.voice.safety

/** In-memory marker for a parked-only action that was deferred while the vehicle was not safely parked. */
data class DeferredParkedAction(
    val action: ParkedAction,
    val queuedAtMs: Long
)

class DeferredParkedActionStore(
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    @Volatile private var pending: DeferredParkedAction? = null

    @Synchronized
    fun defer(action: ParkedAction): DeferredParkedAction =
        DeferredParkedAction(action, clockMs()).also { pending = it }

    @Synchronized fun peek(): DeferredParkedAction? = pending

    @Synchronized
    fun take(): DeferredParkedAction? = pending.also { pending = null }

    @Synchronized fun clear() { pending = null }
}
