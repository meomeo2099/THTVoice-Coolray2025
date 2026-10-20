package com.voicegeely.app

/**
 * Pure routing decision for the physical ECARX voice key.
 *
 * Keeping this Android-free makes the security/lifecycle boundary unit-testable:
 * unrelated broadcasts are ignored without consulting license state, an inactive
 * license can only route to activation, and only an accepted + licensed key event
 * may start the voice service.
 */
enum class VoiceKeyRouteDecision {
    IGNORE,
    ACTIVATION,
    VOICE_SERVICE
}

object VoiceKeyRoutingPolicy {
    fun decide(
        incomingAction: String?,
        expectedAction: String,
        isLicensed: () -> Boolean
    ): VoiceKeyRouteDecision {
        if (incomingAction != expectedAction && !VoiceKeyActions.isVoiceKey(incomingAction)) return VoiceKeyRouteDecision.IGNORE
        return if (isLicensed()) VoiceKeyRouteDecision.VOICE_SERVICE
        else VoiceKeyRouteDecision.ACTIVATION
    }
}

/**
 * The voice key can reach the app on several paths at once (manifest receiver, the receiver
 * registered by the running Voice core, a direct service start) and the head unit may send one
 * broadcast per press and release. Only the first event inside [WINDOW_MS] opens a session.
 */
class VoiceKeyDebounce(private val windowMs: Long = WINDOW_MS) {
    private var lastAcceptedAt = Long.MIN_VALUE

    @Synchronized
    fun accept(nowMs: Long): Boolean {
        if (lastAcceptedAt != Long.MIN_VALUE && nowMs >= lastAcceptedAt && nowMs - lastAcceptedAt < windowMs) return false
        lastAcceptedAt = nowMs
        return true
    }

    companion object {
        const val WINDOW_MS = 1_000L
        /** Shared by every voice-key path in the process. */
        val shared = VoiceKeyDebounce()
    }
}
