package com.voicegeely.app.voice

/** Independent timer: abandoning a worker must not depend on the next press of the voice key. */
class SessionDeadline(
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onTimeout: (Long) -> Unit
) {
    private var ticket = 0L
    private var cancelScheduled: (() -> Unit)? = null

    @Synchronized
    fun start(generation: Long, timeoutMs: Long) {
        cancel()
        val expected = ticket
        cancelScheduled = schedule(timeoutMs) {
            val accepted = synchronized(this) {
                if (ticket != expected) false else {
                    ticket++
                    cancelScheduled = null
                    true
                }
            }
            if (accepted) onTimeout(generation)
        }
    }

    @Synchronized
    fun cancel() {
        ticket++
        cancelScheduled?.invoke()
        cancelScheduled = null
    }
}
