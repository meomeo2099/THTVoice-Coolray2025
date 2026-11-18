package com.voicegeely.app.kws

/** Cancellation revokes work, but ownership is held until the native worker has unwound. */
class WakeTrainingControl {
    private var generation = 0L
    private var owner: Long? = null
    private var cancelled = false
    val busy: Boolean @Synchronized get() = owner != null
    @Synchronized fun begin(): Long? {
        if (owner != null) return null
        cancelled = false
        return (++generation).also { owner = it }
    }
    @Synchronized fun active(token: Long): Boolean = owner == token && !cancelled
    @Synchronized fun ifActive(token: Long, block: () -> Boolean): Boolean = active(token) && block()
    @Synchronized fun cancel() { cancelled = true }
    @Synchronized fun finish(token: Long) { if (owner == token) owner = null }
}
