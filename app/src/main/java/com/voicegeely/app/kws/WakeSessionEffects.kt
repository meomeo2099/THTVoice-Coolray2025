package com.voicegeely.app.kws

/** Provisional wake checks reserve engines only; UI and music belong to an accepted session. */
class WakeSessionEffects(private val mediaAtTrigger: Boolean) {
    @Volatile var active = false
        private set
    private var focusAcquired = false
    private var finished = false
    val rejectionCooldownMs: Long get() = MediaWakeGate.refractoryAfterRejectMs(mediaAtTrigger)

    @Synchronized fun activate(show: () -> Unit, acquire: () -> Unit, cue: () -> Unit) {
        if (active || finished) return
        active = true
        show()
        acquire()
        focusAcquired = true
        cue()
    }

    fun finish(ui: () -> Unit, media: () -> Unit) {
        val release = synchronized(this) {
            if (finished) return
            finished = true
            if (!active) return
            focusAcquired.also { focusAcquired = false }
        }
        // Cleanup can wait for audio; never hold the policy lock across those waits.
        ui()
        if (release) media()
    }
}
